package com.example.data.firebase

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class UserProfileDoc(
    val userId: String,
    val email: String,
    val businessName: String,
    val licenseCode: String,
    val registeredDeviceId: String,
    val registeredDeviceIds: List<String> = listOfNotNull(registeredDeviceId.takeIf { it.isNotBlank() }),
    val active: Boolean
) {
    val connectedDevicesCount: Int
        get() = registeredDeviceIds.distinct().size

    /**
     * Device 1 (first registered device) is the Owner with full financial & operational visibility.
     * Device 2 (any secondary device) is Kasir / Karyawan with financial metrics hidden.
     */
    fun isOwnerDevice(deviceId: String): Boolean {
        val clean = deviceId.trim()
        if (registeredDeviceIds.isEmpty() || clean.isBlank()) return true
        return registeredDeviceIds.first() == clean
    }
}

data class DeviceAutoRestoreHint(
    val ownerUid: String,
    val emailHint: String,
    val businessName: String,
    val licenseCode: String,
    val active: Boolean
)

sealed class CodeAvailabilityResult {
    data class Available(val normalizedCode: String) : CodeAvailabilityResult()
    data class OwnedByCurrentUser(
        val normalizedCode: String,
        val businessName: String,
        val registeredDeviceIds: List<String>
    ) : CodeAvailabilityResult()
    data class InvalidOrUsed(val message: String) : CodeAvailabilityResult()
    data class NetworkError(val message: String) : CodeAvailabilityResult()
}

sealed class LoginDeviceCheckResult {
    data class Allowed(val profile: UserProfileDoc, val wasRebound: Boolean = false) : LoginDeviceCheckResult()
    data object NotRegistered : LoginDeviceCheckResult()
    data class InactiveAccount(val message: String) : LoginDeviceCheckResult()
    data class BlockedOtherDevice(val message: String) : LoginDeviceCheckResult()
    data class OfflineFallback(val message: String) : LoginDeviceCheckResult()
}

private data class ClaimedActivationCodeMatch(
    val code: String,
    val usedByUid: String,
    val usedByEmail: String,
    val businessName: String,
    val registeredDeviceIds: List<String>
)

/**
 * 100% synced with the external "License Admin" Firestore database:
 * - Performs dual activation lookup on login:
 *   1) Checks `users/{uid}` (treating `licenseCode`, `isActivated == true`, `active == true`, or
 *      existing operational subcollections `vendors`/`transactions`/`expenses`/`settlements` as active).
 *   2) Falls back to querying `activation_codes` by `usedByUid == uid` or `usedByEmail == email`
 *      (`status == "used"`) and auto-restores `users/{uid}` so previously activated accounts NEVER
 *      get prompted for a license code again.
 * - Supports seamless same-account reactivation when entering a `"used"` code already bound to the
 *   signed-in Google user's `uid` or `email`.
 * - Enforces Multi-Device Lock (up to 2 devices via `registeredDeviceIds` array).
 */
class ActivationAuthRepository(
    private val db: FirebaseFirestore,
    private val fallbackDbs: List<FirebaseFirestore> = emptyList()
) {
    private val allCandidateDbs: List<FirebaseFirestore> =
        (listOf(db) + fallbackDbs).distinctBy { System.identityHashCode(it) }
    private val activationCodesCollection = db.collection("activation_codes")
    private val usersCollection = db.collection("users")
    private val deviceRegistryCollection = db.collection("device_registry")

    private data class ActivationCodeLookupResult(
        val sourceDb: FirebaseFirestore,
        val snapshot: DocumentSnapshot
    )

    private suspend fun findActivationCodeAcrossDbs(
        code: String
    ): Pair<ActivationCodeLookupResult?, FirebaseFirestoreException?> {
        var lastNetworkError: FirebaseFirestoreException? = null
        for (candidateDb in allCandidateDbs) {
            try {
                val snap = candidateDb.collection("activation_codes")
                    .document(code)
                    .get(Source.SERVER)
                    .await()
                if (snap.exists()) {
                    return ActivationCodeLookupResult(candidateDb, snap) to null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                Log.w(TAG, "Activation code lookup warning for $code on DB: ${e.code} (${e.message})")
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                    e.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
                ) {
                    lastNetworkError = e
                }
            } catch (e: Exception) {
                Log.w(TAG, "Activation code lookup skipped on fallback DB: ${e.message}")
            }
        }
        return null to lastNetworkError
    }

    private suspend fun claimCodeOnSourceDb(
        sourceDb: FirebaseFirestore,
        codeRef: DocumentReference,
        uid: String,
        cleanEmail: String,
        cleanBusinessName: String,
        cleanDeviceId: String,
        initialDevices: List<String>
    ) {
        val fullPayload = mapOf(
            "status" to "used",
            "usedByUid" to uid,
            "usedByEmail" to cleanEmail,
            "usedByBusinessName" to cleanBusinessName,
            "registeredDeviceId" to cleanDeviceId,
            "registeredDeviceIds" to initialDevices,
            "usedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            codeRef.update(fullPayload).await()
        } catch (e: FirebaseFirestoreException) {
            if (sourceDb === db || e.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw e
            }
            // Retry with legacy single-device payloads in case the older License Admin DB rules
            // do not yet permit registeredDeviceIds or updatedAt on claim.
            val legacyWithUpdatedAt = mapOf(
                "status" to "used",
                "usedByUid" to uid,
                "usedByEmail" to cleanEmail,
                "usedByBusinessName" to cleanBusinessName,
                "registeredDeviceId" to cleanDeviceId,
                "usedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                codeRef.update(legacyWithUpdatedAt).await()
            } catch (e2: FirebaseFirestoreException) {
                if (e2.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) throw e2
                val legacyMinimal = mapOf(
                    "status" to "used",
                    "usedByUid" to uid,
                    "usedByEmail" to cleanEmail,
                    "usedByBusinessName" to cleanBusinessName,
                    "registeredDeviceId" to cleanDeviceId,
                    "usedAt" to FieldValue.serverTimestamp()
                )
                codeRef.update(legacyMinimal).await()
            }
        }
    }

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")

    private fun extractRawDeviceArray(snap: DocumentSnapshot): List<String> {
        val rawList = snap.get("registeredDeviceIds") as? List<*> ?: return emptyList()
        val result = linkedSetOf<String>()
        rawList.forEach { entry ->
            val id = (entry as? String)?.trim().orEmpty()
            if (id.isNotEmpty()) result.add(id)
        }
        return result.toList()
    }

    private fun extractRegisteredDeviceIds(snap: DocumentSnapshot): List<String> {
        val existingDevices = extractRawDeviceArray(snap).toMutableList()
        val legacySingle = snap.getString("registeredDeviceId")?.trim().orEmpty()
        if (legacySingle.isNotEmpty() && legacySingle !in existingDevices) {
            existingDevices.add(legacySingle)
        }
        return existingDevices
    }

    private fun isCodeOwnedByUser(
        codeSnap: DocumentSnapshot,
        uid: String,
        email: String
    ): Boolean {
        val cleanUid = uid.trim()
        val cleanEmail = email.trim()

        val uidCandidates = listOf("usedByUid", "ownerUid", "userId", "uid")
            .mapNotNull { codeSnap.getString(it)?.trim()?.takeIf { v -> v.isNotEmpty() } }
        val emailCandidates = listOf("usedByEmail", "email", "buyerEmail", "ownerEmail", "usedBy")
            .mapNotNull { codeSnap.getString(it)?.trim()?.takeIf { v -> v.isNotEmpty() } }

        val uidMatches = cleanUid.isNotEmpty() && uidCandidates.any { it == cleanUid }
        val emailMatches = cleanEmail.isNotEmpty() && emailCandidates.any { it.equals(cleanEmail, ignoreCase = true) }
        return uidMatches || emailMatches
    }

    /**
     * Real-time listener on `users/{uid}` with MetadataChanges.INCLUDE so both devices
     * see live connected device count ("Perangkat Terhubung: X / 2 HP") immediately.
     */
    fun observeUserProfile(uid: String): Flow<UserProfileDoc?> {
        val path = "users/$uid"
        return usersCollection.document(uid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snap ->
                if (!snap.exists()) return@map null
                val deviceIds = extractRegisteredDeviceIds(snap)
                val activeFlag = snap.getBoolean("active")
                val isActivatedFlag = snap.getBoolean("isActivated")
                val licenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty()
                val effectiveActive = when {
                    activeFlag == false && isActivatedFlag != true -> false
                    activeFlag == true || isActivatedFlag == true || licenseCode.isNotBlank() -> true
                    else -> false
                }
                UserProfileDoc(
                    userId = snap.getString("userId")?.trim().orEmpty().ifBlank { uid },
                    email = snap.getString("email")?.trim().orEmpty(),
                    businessName = snap.getString("businessName")?.trim().orEmpty().ifBlank { "Lapak Kita" },
                    licenseCode = licenseCode,
                    registeredDeviceId = deviceIds.firstOrNull().orEmpty(),
                    registeredDeviceIds = deviceIds,
                    active = effectiveActive
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                Log.w(TAG, "observeUserProfile($path) warning: ${rootError.message}")
            }
    }

    /**
     * Checks `activation_codes/{code}` against server:
     * - If `status == "available"` -> [CodeAvailabilityResult.Available]
     * - If `status == "used"` AND bound to the same Google account (`usedByUid == currentUid` or
     *   `usedByEmail == currentEmail`) -> [CodeAvailabilityResult.OwnedByCurrentUser] (allows
     *   seamless automatic reactivation instead of rejecting!)
     * - Otherwise -> [CodeAvailabilityResult.InvalidOrUsed]
     */
    suspend fun checkActivationCodeAvailable(
        rawCode: String,
        currentUid: String = "",
        currentEmail: String = ""
    ): CodeAvailabilityResult {
        val code = normalizeCode(rawCode)
        if (code.length < 4 || code.length > 48) {
            return CodeAvailabilityResult.InvalidOrUsed("Kode aktivasi tidak valid atau sudah pernah digunakan.")
        }

        return try {
            val (found, networkErr) = findActivationCodeAcrossDbs(code)
            if (found == null) {
                if (networkErr != null) {
                    return CodeAvailabilityResult.NetworkError(
                        "Tidak dapat terhubung ke server. Periksa koneksi internet Anda."
                    )
                }
                return CodeAvailabilityResult.InvalidOrUsed(
                    "Kode aktivasi $code tidak ditemukan di server saat ini. Pastikan penulisan benar atau buat kode baru di License Admin."
                )
            }

            val snapshot = found.snapshot
            val status = snapshot.getString("status")?.trim()?.lowercase().orEmpty()
            if (status == "available" || status == "unused") {
                return CodeAvailabilityResult.Available(normalizedCode = code)
            }

            if ((status == "used" || status == "active") && isCodeOwnedByUser(snapshot, currentUid, currentEmail)) {
                return CodeAvailabilityResult.OwnedByCurrentUser(
                    normalizedCode = code,
                    businessName = snapshot.getString("usedByBusinessName")?.trim().orEmpty(),
                    registeredDeviceIds = extractRegisteredDeviceIds(snapshot)
                )
            }

            if (status == "used" || status == "active") {
                return CodeAvailabilityResult.InvalidOrUsed(
                    "Kode aktivasi $code sudah terdaftar pada akun Google lain."
                )
            }

            CodeAvailabilityResult.InvalidOrUsed(
                "Kode aktivasi $code tidak aktif (status: ${status.ifBlank { "tidak valid" }}). Hubungi admin."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Activation code check error for $code: ${e.code}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    CodeAvailabilityResult.NetworkError("Tidak dapat terhubung ke server. Periksa koneksi internet Anda.")
                else ->
                    CodeAvailabilityResult.InvalidOrUsed("Gagal memverifikasi kode aktivasi (${e.code.name}).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected activation code check error", e)
            CodeAvailabilityResult.NetworkError("Gagal memeriksa kode aktivasi. Periksa koneksi internet Anda.")
        }
    }

    /**
     * Completes new activation OR automatic reactivation when the entered code is already
     * bound to this exact Google account (`usedByUid == uid` or `usedByEmail == email`).
     */
    suspend fun completeRegistrationWithCode(
        uid: String,
        email: String,
        businessName: String,
        rawCode: String,
        deviceId: String
    ): Result<UserProfileDoc> {
        val code = normalizeCode(rawCode)
        val cleanBusinessName = businessName.trim().ifEmpty { "Lapak Kita" }.take(120)
        val cleanEmail = email.trim().ifEmpty { "$uid@lapakkita.local" }.take(160)
        val cleanDeviceId = deviceId.trim()

        if (code.length < 4) {
            return Result.failure(IllegalArgumentException("Kode aktivasi tidak valid atau sudah pernah digunakan."))
        }

        val userRef = usersCollection.document(uid)
        val deviceRef = deviceRegistryCollection.document(cleanDeviceId)

        return try {
            val (foundCode, networkErr) = findActivationCodeAcrossDbs(code)
            if (foundCode == null) {
                if (networkErr != null) {
                    return Result.failure(
                        IllegalStateException("Koneksi internet terputus saat memeriksa kode aktivasi. Coba lagi.")
                    )
                }
                return Result.failure(
                    IllegalStateException("Kode aktivasi $code tidak ditemukan di server saat ini.")
                )
            }

            val sourceDb = foundCode.sourceDb
            val codeSnap = foundCode.snapshot
            val codeRef = sourceDb.collection("activation_codes").document(code)

            val status = codeSnap.getString("status")?.trim()?.lowercase().orEmpty()
            val isAvailable = codeSnap.exists() && (status == "available" || status == "unused")
            val isOwnedUsedCode = codeSnap.exists() &&
                (status == "used" || status == "active") &&
                isCodeOwnedByUser(codeSnap, uid, cleanEmail)

            if (!isAvailable && !isOwnedUsedCode) {
                val reason = when {
                    status == "used" || status == "active" ->
                        "Kode aktivasi $code sudah terdaftar pada akun Google lain."
                    else ->
                        "Kode aktivasi $code tidak valid atau sudah pernah digunakan."
                }
                return Result.failure(IllegalStateException(reason))
            }

            // Check if this Google account already has a users/{uid} doc in primary DB (and legacy DB if different)
            val existingUserSnap = userRef.get(Source.SERVER).await()
            val existingSourceUserSnap = if (sourceDb !== db) {
                runCatching { sourceDb.collection("users").document(uid).get(Source.SERVER).await() }.getOrNull()
            } else {
                null
            }

            // Case 1: Code is already "used" by THIS exact Google account -> restore & reactivate seamlessly!
            if (isOwnedUsedCode) {
                val restoredBiz = codeSnap.getString("usedByBusinessName")?.trim().orEmpty()
                    .ifBlank { existingUserSnap.getString("businessName")?.trim().orEmpty() }
                    .ifBlank { existingSourceUserSnap?.getString("businessName")?.trim().orEmpty() }
                    .ifBlank { cleanBusinessName }
                val mergedDevices = linkedSetOf<String>().apply {
                    addAll(extractRegisteredDeviceIds(codeSnap))
                    if (existingUserSnap.exists()) {
                        addAll(extractRegisteredDeviceIds(existingUserSnap))
                    }
                    if (existingSourceUserSnap != null && existingSourceUserSnap.exists()) {
                        addAll(extractRegisteredDeviceIds(existingSourceUserSnap))
                    }
                }.toList()

                val finalDevices = when {
                    cleanDeviceId in mergedDevices -> mergedDevices.take(MAX_REGISTERED_DEVICES)
                    mergedDevices.size < MAX_REGISTERED_DEVICES -> (mergedDevices + cleanDeviceId).distinct()
                    else -> return Result.failure(IllegalStateException(MAX_DEVICES_LOCK_MESSAGE))
                }

                restoreOrUpdateUserProfile(
                    userRef = userRef,
                    existingUserSnap = existingUserSnap,
                    uid = uid,
                    email = cleanEmail,
                    businessName = restoredBiz,
                    licenseCode = code,
                    devices = finalDevices
                )
                syncActivationCodeOwnershipAndDevices(
                    codeRef = codeRef,
                    codeSnap = codeSnap,
                    uid = uid,
                    updatedDevices = finalDevices
                )
                if (sourceDb !== db) {
                    runCatching {
                        restoreOrUpdateUserProfile(
                            userRef = sourceDb.collection("users").document(uid),
                            existingUserSnap = existingSourceUserSnap,
                            uid = uid,
                            email = cleanEmail,
                            businessName = restoredBiz,
                            licenseCode = code,
                            devices = finalDevices
                        )
                    }
                }
                runCatching {
                    syncDeviceRegistry(deviceRef, uid, cleanEmail, restoredBiz, code)
                }

                return Result.success(
                    UserProfileDoc(
                        userId = uid,
                        email = cleanEmail,
                        businessName = restoredBiz,
                        licenseCode = code,
                        registeredDeviceId = finalDevices.firstOrNull().orEmpty(),
                        registeredDeviceIds = finalDevices,
                        active = true
                    )
                )
            }

            // Case 2: Account already has an active users/{uid} profile (avoid burning a brand new code)
            if (existingUserSnap.exists() && isUserDocMarkedActive(existingUserSnap)) {
                val existingDevices = extractRegisteredDeviceIds(existingUserSnap)
                val existingCode = existingUserSnap.getString("licenseCode")?.trim()?.uppercase().orEmpty().ifBlank { code }
                val existingBiz = existingUserSnap.getString("businessName")?.trim().orEmpty().ifBlank { cleanBusinessName }

                val finalDevices = when {
                    cleanDeviceId in existingDevices -> existingDevices
                    existingDevices.size < MAX_REGISTERED_DEVICES -> (existingDevices + cleanDeviceId).distinct()
                    else -> return Result.failure(IllegalStateException(MAX_DEVICES_LOCK_MESSAGE))
                }

                restoreOrUpdateUserProfile(
                    userRef = userRef,
                    existingUserSnap = existingUserSnap,
                    uid = uid,
                    email = cleanEmail,
                    businessName = existingBiz,
                    licenseCode = existingCode,
                    devices = finalDevices
                )
                syncActivationCodeDevices(existingCode, finalDevices)
                runCatching {
                    syncDeviceRegistry(deviceRef, uid, cleanEmail, existingBiz, existingCode)
                }

                return Result.success(
                    UserProfileDoc(
                        userId = uid,
                        email = cleanEmail,
                        businessName = existingBiz,
                        licenseCode = existingCode,
                        registeredDeviceId = finalDevices.firstOrNull().orEmpty(),
                        registeredDeviceIds = finalDevices,
                        active = true
                    )
                )
            }

            // Case 3: Fresh first-time activation with an "available" code
            val initialDevices = listOf(cleanDeviceId)

            if (sourceDb === db) {
                val batch = db.batch()
                batch.update(
                    codeRef,
                    mapOf(
                        "status" to "used",
                        "usedByUid" to uid,
                        "usedByEmail" to cleanEmail,
                        "usedByBusinessName" to cleanBusinessName,
                        "registeredDeviceId" to cleanDeviceId,
                        "registeredDeviceIds" to initialDevices,
                        "usedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.set(
                    userRef,
                    mapOf(
                        "userId" to uid,
                        "email" to cleanEmail,
                        "businessName" to cleanBusinessName,
                        "licenseCode" to code,
                        "registeredDeviceId" to cleanDeviceId,
                        "registeredDeviceIds" to initialDevices,
                        "active" to true,
                        "isActivated" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            } else {
                // Code lives in the License Admin / legacy Firestore database -> claim it there first,
                // then provision users/{uid} in primaryDb and mirror best-effort to sourceDb.
                claimCodeOnSourceDb(
                    sourceDb = sourceDb,
                    codeRef = codeRef,
                    uid = uid,
                    cleanEmail = cleanEmail,
                    cleanBusinessName = cleanBusinessName,
                    cleanDeviceId = cleanDeviceId,
                    initialDevices = initialDevices
                )
                restoreOrUpdateUserProfile(
                    userRef = userRef,
                    existingUserSnap = existingUserSnap,
                    uid = uid,
                    email = cleanEmail,
                    businessName = cleanBusinessName,
                    licenseCode = code,
                    devices = initialDevices
                )
                runCatching {
                    restoreOrUpdateUserProfile(
                        userRef = sourceDb.collection("users").document(uid),
                        existingUserSnap = existingSourceUserSnap,
                        uid = uid,
                        email = cleanEmail,
                        businessName = cleanBusinessName,
                        licenseCode = code,
                        devices = initialDevices
                    )
                }
            }

            runCatching {
                syncDeviceRegistry(deviceRef, uid, cleanEmail, cleanBusinessName, code)
            }

            Result.success(
                UserProfileDoc(
                    userId = uid,
                    email = cleanEmail,
                    businessName = cleanBusinessName,
                    licenseCode = code,
                    registeredDeviceId = cleanDeviceId,
                    registeredDeviceIds = initialDevices,
                    active = true
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Registration batch failed: ${e.code} ${e.message}", e)
            val msg = when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Izin aktivasi ditolak oleh server (pastikan kode belum dipakai akun lain dan masih dalam batas 2 perangkat)."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Koneksi internet terputus saat mengaktifkan akun. Coba lagi."
                else ->
                    "Gagal mendaftarkan akun (${e.code.name}). Silakan coba lagi."
            }
            Result.failure(IllegalStateException(msg))
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            Result.failure(IllegalStateException(e.message ?: "Gagal mengaktifkan akun."))
        }
    }

    private fun isUserDocMarkedActive(snap: DocumentSnapshot): Boolean {
        if (!snap.exists()) return false
        val explicitActive = snap.getBoolean("active")
        val explicitIsActivated = snap.getBoolean("isActivated")
        val licenseCode = snap.getString("licenseCode")?.trim().orEmpty()
        if (explicitActive == false && explicitIsActivated != true) return false
        return explicitActive == true || explicitIsActivated == true || licenseCode.isNotBlank()
    }

    /**
     * Checks if `users/{uid}` already owns operational subcollections (vendors, transactions,
     * expenses, or settlements), indicating this account was previously active on Lapak Kita.
     */
    private suspend fun hasUserOperationalSubData(uid: String): Boolean {
        val userDoc = usersCollection.document(uid)
        val subcollections = listOf("vendors", "transactions", "expenses", "settlements")
        for (sub in subcollections) {
            val found = runCatching {
                !userDoc.collection(sub).limit(1).get(Source.SERVER).await().isEmpty
            }.getOrDefault(false)
            if (found) return true
        }
        return false
    }

    /**
     * Queries `activation_codes` for any `"used"` code already bound to this Google account
     * via `usedByUid == uid` or `usedByEmail == email`.
     */
    private suspend fun findClaimedActivationCodeForUser(
        uid: String,
        email: String,
        targetDb: FirebaseFirestore? = null
    ): ClaimedActivationCodeMatch? {
        val cleanUid = uid.trim()
        val cleanEmail = email.trim()
        val dbsToSearch = if (targetDb != null) listOf(targetDb) else allCandidateDbs

        for (candidateDb in dbsToSearch) {
            val codesCol = candidateDb.collection("activation_codes")
            // 1. Primary lookup by usedByUid
            if (cleanUid.isNotEmpty()) {
                val byUidSnap = runCatching {
                    codesCol
                        .whereEqualTo("usedByUid", cleanUid)
                        .limit(5)
                        .get(Source.SERVER)
                        .await()
                }.onFailure { err ->
                    Log.w(TAG, "Query activation_codes by usedByUid skipped: ${err.message}")
                }.getOrNull()

                val matchedDoc = byUidSnap?.documents?.firstOrNull { doc ->
                    doc.getString("status")?.trim()?.lowercase() in setOf("used", "active")
                }
                if (matchedDoc != null) {
                    return matchedDoc.toClaimedMatch()
                }
            }

            // 2. Secondary lookup by usedByEmail (exact & lowercase)
            if (cleanEmail.isNotEmpty()) {
                val emailCandidates = linkedSetOf(cleanEmail, cleanEmail.lowercase())
                for (candidateEmail in emailCandidates) {
                    val byEmailSnap = runCatching {
                        codesCol
                            .whereEqualTo("usedByEmail", candidateEmail)
                            .limit(5)
                            .get(Source.SERVER)
                            .await()
                    }.onFailure { err ->
                        Log.w(TAG, "Query activation_codes by usedByEmail skipped: ${err.message}")
                    }.getOrNull()

                    val matchedDoc = byEmailSnap?.documents?.firstOrNull { doc ->
                        doc.getString("status")?.trim()?.lowercase() in setOf("used", "active")
                    }
                    if (matchedDoc != null) {
                        return matchedDoc.toClaimedMatch()
                    }
                }
            }
        }

        return null
    }

    private fun DocumentSnapshot.toClaimedMatch(): ClaimedActivationCodeMatch {
        val docCode = normalizeCode(getString("code").orEmpty().ifBlank { id })
        return ClaimedActivationCodeMatch(
            code = docCode,
            usedByUid = getString("usedByUid")?.trim().orEmpty(),
            usedByEmail = getString("usedByEmail")?.trim().orEmpty(),
            businessName = getString("usedByBusinessName")?.trim().orEmpty(),
            registeredDeviceIds = extractRegisteredDeviceIds(this)
        )
    }

    private suspend fun restoreOrUpdateUserProfile(
        userRef: DocumentReference,
        existingUserSnap: DocumentSnapshot?,
        uid: String,
        email: String,
        businessName: String,
        licenseCode: String,
        devices: List<String>
    ) {
        val safeCode = normalizeCode(licenseCode).ifBlank { "RESTORED-ACTIVE" }
        val safeBiz = businessName.trim().ifEmpty { "Lapak Kita" }.take(120)
        val safeEmail = email.trim().ifEmpty { "$uid@lapakkita.local" }.take(160)
        val safeDevices = devices.distinct().take(MAX_REGISTERED_DEVICES)
        val primaryDevice = safeDevices.firstOrNull().orEmpty()

        if (existingUserSnap == null || !existingUserSnap.exists()) {
            userRef.set(
                mapOf(
                    "userId" to uid,
                    "email" to safeEmail,
                    "businessName" to safeBiz,
                    "licenseCode" to safeCode,
                    "registeredDeviceId" to primaryDevice,
                    "registeredDeviceIds" to safeDevices,
                    "active" to true,
                    "isActivated" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            val updates = mutableMapOf<String, Any>(
                "email" to safeEmail,
                "businessName" to safeBiz,
                "registeredDeviceId" to primaryDevice,
                "registeredDeviceIds" to safeDevices,
                "isActivated" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (existingUserSnap.getString("userId").isNullOrBlank()) {
                updates["userId"] = uid
            }
            val existingCode = existingUserSnap.getString("licenseCode")?.trim().orEmpty()
            if (existingCode.isBlank() || existingCode == "RESTORED-ACTIVE") {
                updates["licenseCode"] = safeCode
            }
            if (existingUserSnap.getBoolean("active") == null) {
                updates["active"] = true
            }
            userRef.update(updates).await()
        }
    }

    private suspend fun syncActivationCodeOwnershipAndDevices(
        codeRef: DocumentReference,
        codeSnap: DocumentSnapshot?,
        uid: String,
        updatedDevices: List<String>
    ) {
        if (updatedDevices.isEmpty()) return
        runCatching {
            val updates = mutableMapOf<String, Any>(
                "registeredDeviceId" to updatedDevices.first(),
                "registeredDeviceIds" to updatedDevices,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val existingUid = codeSnap?.getString("usedByUid")?.trim().orEmpty()
            if (existingUid.isBlank() && uid.isNotBlank()) {
                updates["usedByUid"] = uid
            }
            codeRef.update(updates).await()
        }.onFailure { err ->
            Log.w(TAG, "syncActivationCodeOwnershipAndDevices(${codeRef.id}) skipped: ${err.message}")
        }
    }

    /**
     * Dual-check login verification + Multi-Device Lock (Max 2 Devices = HP Kasir + HP Owner):
     * 1. Checks `users/{uid}` first: if document exists and has `licenseCode`, `isActivated == true`,
     *    `active == true`, or existing operational sub-data -> treats account as ALREADY ACTIVE.
     * 2. If `users/{uid}` is missing or lacks activation flags, queries `activation_codes` for
     *    `usedByUid == uid` or `usedByEmail == email` (`status == "used"`). If found, automatically
     *    restores `users/{uid}` with that license code and grants Dashboard access immediately.
     * 3. Only returns [LoginDeviceCheckResult.NotRegistered] (routing to "Lengkapi Aktivasi Akun")
     *    when BOTH checks genuinely find no license or operational data linked to this Google account.
     */
    suspend fun verifySingleDeviceOnLogin(
        uid: String,
        email: String,
        currentDeviceId: String
    ): LoginDeviceCheckResult {
        val userRef = usersCollection.document(uid)
        val cleanDeviceId = currentDeviceId.trim()
        val deviceRef = deviceRegistryCollection.document(cleanDeviceId)
        val cleanEmail = email.trim()

        return try {
            val snap = userRef.get(Source.SERVER).await()

            // Step 1: Evaluate users/{uid} if present
            if (snap.exists()) {
                val explicitActive = snap.getBoolean("active")
                val explicitIsActivated = snap.getBoolean("isActivated")
                val existingLicenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty()

                // Explicitly suspended/deactivated by developer admin
                if (explicitActive == false && explicitIsActivated != true) {
                    return LoginDeviceCheckResult.InactiveAccount(
                        "Akun Anda saat ini tidak aktif. Silakan hubungi admin Lapak Kita."
                    )
                }

                val hasDocActivation = explicitActive == true ||
                    explicitIsActivated == true ||
                    existingLicenseCode.isNotBlank()

                val claimedMatch = if (!hasDocActivation || existingLicenseCode.isBlank()) {
                    findClaimedActivationCodeForUser(uid, cleanEmail)
                } else {
                    null
                }
                val hasOperationalData = if (!hasDocActivation && claimedMatch == null) {
                    hasUserOperationalSubData(uid)
                } else {
                    false
                }

                if (hasDocActivation || claimedMatch != null || hasOperationalData) {
                    val resolvedCode = existingLicenseCode
                        .ifBlank { claimedMatch?.code.orEmpty() }
                        .ifBlank { "RESTORED-ACTIVE" }
                    val resolvedBiz = snap.getString("businessName")?.trim().orEmpty()
                        .ifBlank { claimedMatch?.businessName.orEmpty() }
                        .ifBlank { "Lapak Kita" }
                    val resolvedEmail = snap.getString("email")?.trim().orEmpty()
                        .ifBlank { claimedMatch?.usedByEmail.orEmpty() }
                        .ifBlank { cleanEmail }

                    val rawArrayDevices = extractRawDeviceArray(snap)
                    val hasArrayField = snap.get("registeredDeviceIds") is List<*>
                    val existingDevices = linkedSetOf<String>().apply {
                        addAll(extractRegisteredDeviceIds(snap))
                        if (claimedMatch != null) addAll(claimedMatch.registeredDeviceIds)
                    }.toList()
                    val needsBackfill = !hasArrayField ||
                        rawArrayDevices != existingDevices ||
                        explicitIsActivated != true ||
                        explicitActive == null ||
                        existingLicenseCode.isBlank()

                    if (cleanDeviceId in existingDevices) {
                        if (needsBackfill) {
                            runCatching {
                                restoreOrUpdateUserProfile(
                                    userRef = userRef,
                                    existingUserSnap = snap,
                                    uid = uid,
                                    email = resolvedEmail,
                                    businessName = resolvedBiz,
                                    licenseCode = resolvedCode,
                                    devices = existingDevices
                                )
                            }
                            syncActivationCodeDevices(resolvedCode, existingDevices)
                        }
                        runCatching {
                            syncDeviceRegistry(deviceRef, uid, resolvedEmail, resolvedBiz, resolvedCode)
                        }
                        return LoginDeviceCheckResult.Allowed(
                            profile = UserProfileDoc(
                                userId = uid,
                                email = resolvedEmail,
                                businessName = resolvedBiz,
                                licenseCode = resolvedCode,
                                registeredDeviceId = existingDevices.firstOrNull().orEmpty(),
                                registeredDeviceIds = existingDevices,
                                active = true
                            ),
                            wasRebound = false
                        )
                    }

                    if (existingDevices.size < MAX_REGISTERED_DEVICES) {
                        val updatedDevices = (existingDevices + cleanDeviceId).distinct().take(MAX_REGISTERED_DEVICES)
                        restoreOrUpdateUserProfile(
                            userRef = userRef,
                            existingUserSnap = snap,
                            uid = uid,
                            email = resolvedEmail,
                            businessName = resolvedBiz,
                            licenseCode = resolvedCode,
                            devices = updatedDevices
                        )
                        syncActivationCodeDevices(resolvedCode, updatedDevices)
                        runCatching {
                            syncDeviceRegistry(deviceRef, uid, resolvedEmail, resolvedBiz, resolvedCode)
                        }
                        return LoginDeviceCheckResult.Allowed(
                            profile = UserProfileDoc(
                                userId = uid,
                                email = resolvedEmail,
                                businessName = resolvedBiz,
                                licenseCode = resolvedCode,
                                registeredDeviceId = updatedDevices.first(),
                                registeredDeviceIds = updatedDevices,
                                active = true
                            ),
                            wasRebound = true
                        )
                    }

                    return LoginDeviceCheckResult.BlockedOtherDevice(MAX_DEVICES_LOCK_MESSAGE)
                }
            }

            // Step 2: users/{uid} does not exist (or had no activation flag) ->
            // Dual-check `activation_codes` by usedByUid / usedByEmail AND existing operational sub-data!
            val claimedMatch = findClaimedActivationCodeForUser(uid, cleanEmail)
            if (claimedMatch != null) {
                val existingDevices = claimedMatch.registeredDeviceIds
                val finalDevices = when {
                    cleanDeviceId in existingDevices -> existingDevices.take(MAX_REGISTERED_DEVICES)
                    existingDevices.size < MAX_REGISTERED_DEVICES -> (existingDevices + cleanDeviceId).distinct()
                    else -> return LoginDeviceCheckResult.BlockedOtherDevice(MAX_DEVICES_LOCK_MESSAGE)
                }
                val restoredBiz = claimedMatch.businessName.ifBlank { "Lapak Kita" }
                val restoredEmail = cleanEmail.ifBlank { claimedMatch.usedByEmail }.ifBlank { "$uid@lapakkita.local" }

                runCatching {
                    restoreOrUpdateUserProfile(
                        userRef = userRef,
                        existingUserSnap = snap.takeIf { it.exists() },
                        uid = uid,
                        email = restoredEmail,
                        businessName = restoredBiz,
                        licenseCode = claimedMatch.code,
                        devices = finalDevices
                    )
                }.onFailure { err ->
                    Log.w(TAG, "Auto-restore users/$uid warning: ${err.message}")
                }
                syncActivationCodeDevices(claimedMatch.code, finalDevices)
                runCatching {
                    syncDeviceRegistry(deviceRef, uid, restoredEmail, restoredBiz, claimedMatch.code)
                }

                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = restoredEmail,
                        businessName = restoredBiz,
                        licenseCode = claimedMatch.code,
                        registeredDeviceId = finalDevices.firstOrNull().orEmpty(),
                        registeredDeviceIds = finalDevices,
                        active = true
                    ),
                    wasRebound = cleanDeviceId !in existingDevices
                )
            }

            // Step 2b: Check if users/{uid} already has operational lapak sub-data (vendors/transactions/etc.)
            if (hasUserOperationalSubData(uid)) {
                val restoredCode = "RESTORED-ACTIVE"
                val restoredDevices = listOf(cleanDeviceId)
                val restoredEmail = cleanEmail.ifBlank { "$uid@lapakkita.local" }
                runCatching {
                    restoreOrUpdateUserProfile(
                        userRef = userRef,
                        existingUserSnap = snap.takeIf { it.exists() },
                        uid = uid,
                        email = restoredEmail,
                        businessName = "Lapak Kita",
                        licenseCode = restoredCode,
                        devices = restoredDevices
                    )
                }
                runCatching {
                    syncDeviceRegistry(deviceRef, uid, restoredEmail, "Lapak Kita", restoredCode)
                }
                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = restoredEmail,
                        businessName = "Lapak Kita",
                        licenseCode = restoredCode,
                        registeredDeviceId = cleanDeviceId,
                        registeredDeviceIds = restoredDevices,
                        active = true
                    ),
                    wasRebound = true
                )
            }

            // Step 2c: Check if this exact device is already registered to this Google account in device_registry
            val deviceHint = lookupDeviceRegistration(cleanDeviceId)
            val deviceMatchesUser = deviceHint != null && deviceHint.active && (
                deviceHint.ownerUid == uid ||
                    (cleanEmail.isNotEmpty() && deviceHint.emailHint.equals(cleanEmail, ignoreCase = true))
                )
            if (deviceMatchesUser && deviceHint != null) {
                val restoredCode = normalizeCode(deviceHint.licenseCode).ifBlank { "RESTORED-ACTIVE" }
                val restoredBiz = deviceHint.businessName.ifBlank { "Lapak Kita" }
                val restoredEmail = cleanEmail.ifBlank { deviceHint.emailHint }.ifBlank { "$uid@lapakkita.local" }
                val restoredDevices = listOf(cleanDeviceId)
                runCatching {
                    restoreOrUpdateUserProfile(
                        userRef = userRef,
                        existingUserSnap = snap.takeIf { it.exists() },
                        uid = uid,
                        email = restoredEmail,
                        businessName = restoredBiz,
                        licenseCode = restoredCode,
                        devices = restoredDevices
                    )
                }
                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = restoredEmail,
                        businessName = restoredBiz,
                        licenseCode = restoredCode,
                        registeredDeviceId = cleanDeviceId,
                        registeredDeviceIds = restoredDevices,
                        active = true
                    ),
                    wasRebound = false
                )
            }

            // Step 2d: Check fallback/legacy License Admin Firestore DBs for existing users/{uid}
            // so accounts already activated on the original Lapak Kita DB enter seamlessly without re-entering a code.
            for (fallbackDb in fallbackDbs) {
                val legacySnap = runCatching {
                    fallbackDb.collection("users").document(uid).get(Source.SERVER).await()
                }.getOrNull()
                if (legacySnap != null && legacySnap.exists()) {
                    val legacyActive = legacySnap.getBoolean("active")
                    val legacyIsActivated = legacySnap.getBoolean("isActivated")
                    if (legacyActive == false && legacyIsActivated != true) {
                        return LoginDeviceCheckResult.InactiveAccount(
                            "Akun Anda saat ini tidak aktif. Silakan hubungi admin Lapak Kita."
                        )
                    }
                    if (isUserDocMarkedActive(legacySnap)) {
                        val legacyDevices = extractRegisteredDeviceIds(legacySnap)
                        val finalDevices = when {
                            cleanDeviceId in legacyDevices -> legacyDevices.take(MAX_REGISTERED_DEVICES)
                            legacyDevices.size < MAX_REGISTERED_DEVICES -> (legacyDevices + cleanDeviceId).distinct()
                            else -> return LoginDeviceCheckResult.BlockedOtherDevice(MAX_DEVICES_LOCK_MESSAGE)
                        }
                        val restoredCode = legacySnap.getString("licenseCode")?.trim()?.uppercase().orEmpty()
                            .ifBlank { "RESTORED-ACTIVE" }
                        val restoredBiz = legacySnap.getString("businessName")?.trim().orEmpty()
                            .ifBlank { "Lapak Kita" }
                        val restoredEmail = cleanEmail
                            .ifBlank { legacySnap.getString("email")?.trim().orEmpty() }
                            .ifBlank { "$uid@lapakkita.local" }

                        runCatching {
                            restoreOrUpdateUserProfile(
                                userRef = userRef,
                                existingUserSnap = snap.takeIf { it.exists() },
                                uid = uid,
                                email = restoredEmail,
                                businessName = restoredBiz,
                                licenseCode = restoredCode,
                                devices = finalDevices
                            )
                        }
                        syncActivationCodeDevices(restoredCode, finalDevices)
                        runCatching {
                            syncDeviceRegistry(deviceRef, uid, restoredEmail, restoredBiz, restoredCode)
                        }
                        return LoginDeviceCheckResult.Allowed(
                            profile = UserProfileDoc(
                                userId = uid,
                                email = restoredEmail,
                                businessName = restoredBiz,
                                licenseCode = restoredCode,
                                registeredDeviceId = finalDevices.firstOrNull().orEmpty(),
                                registeredDeviceIds = finalDevices,
                                active = true
                            ),
                            wasRebound = cleanDeviceId !in legacyDevices
                        )
                    }
                }
            }

            // Step 3: Neither users/{uid} nor activation_codes nor sub-data nor device_registry found -> new account
            LoginDeviceCheckResult.NotRegistered
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Online login check failed (${e.code}): ${e.message}")
            when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    LoginDeviceCheckResult.OfflineFallback("Mode Offline: menggunakan sesi lokal perangkat.")
                else ->
                    LoginDeviceCheckResult.OfflineFallback("Tidak dapat memverifikasi server saat ini.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Online login check exception: ${e.message}")
            LoginDeviceCheckResult.OfflineFallback("Mode Offline: menggunakan sesi lokal perangkat.")
        }
    }

    private suspend fun syncActivationCodeDevices(licenseCode: String, updatedDevices: List<String>) {
        val code = normalizeCode(licenseCode)
        if (code.length < 4 || code == "RESTORED-ACTIVE" || updatedDevices.isEmpty()) return
        val (found, _) = findActivationCodeAcrossDbs(code)
        val targetRef = found?.sourceDb?.collection("activation_codes")?.document(code)
            ?: activationCodesCollection.document(code)
        runCatching {
            targetRef.update(
                mapOf(
                    "registeredDeviceId" to updatedDevices.first(),
                    "registeredDeviceIds" to updatedDevices,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }.onFailure { err ->
            Log.w(TAG, "syncActivationCodeDevices($code) skipped: ${err.message}")
        }
    }

    /**
     * Checks `device_registry/{currentDeviceId}` across primary and fallback DBs when opening the app
     * without a local session (e.g., after reinstalling on the exact same phone).
     */
    suspend fun lookupDeviceRegistration(currentDeviceId: String): DeviceAutoRestoreHint? {
        if (currentDeviceId.length < 6) return null
        for (candidateDb in allCandidateDbs) {
            try {
                val snap = candidateDb.collection("device_registry")
                    .document(currentDeviceId)
                    .get(Source.SERVER)
                    .await()
                if (!snap.exists()) continue
                val active = snap.getBoolean("active") ?: false
                if (!active) continue
                val ownerUid = snap.getString("ownerUid")?.trim().orEmpty()
                val emailHint = snap.getString("emailHint")?.trim().orEmpty()
                val businessName = snap.getString("businessName")?.trim().orEmpty()
                val licenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty()
                if (ownerUid.isBlank() || emailHint.isBlank()) continue

                return DeviceAutoRestoreHint(
                    ownerUid = ownerUid,
                    emailHint = emailHint,
                    businessName = businessName,
                    licenseCode = licenseCode,
                    active = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Device auto-restore lookup skipped on DB: ${e.message}")
            }
        }
        return null
    }

    private suspend fun syncDeviceRegistry(
        deviceRef: DocumentReference,
        uid: String,
        email: String,
        businessName: String,
        licenseCode: String
    ) {
        if (licenseCode.length < 4 || email.length < 3) return
        val existing = runCatching { deviceRef.get(Source.SERVER).await() }.getOrNull()
        if (existing != null && existing.exists() && existing.getString("ownerUid") == uid) {
            deviceRef.update(
                mapOf(
                    "emailHint" to email.take(160),
                    "businessName" to businessName.take(120),
                    "licenseCode" to licenseCode,
                    "active" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else if (existing == null || !existing.exists()) {
            deviceRef.set(
                mapOf(
                    "ownerUid" to uid,
                    "emailHint" to email.take(160),
                    "businessName" to businessName.take(120),
                    "licenseCode" to licenseCode,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    companion object {
        private const val TAG = "ActivationAuthRepo"
        const val LEGACY_LICENSE_DATABASE_ID = "ai-studio-android-lapakkit-7600eb97-cc5e-45ea-a204-647022c41df2"
        const val MAX_REGISTERED_DEVICES = 2
        const val MAX_DEVICES_LOCK_MESSAGE =
            "Batas maksimal 2 perangkat telah tercapai untuk akun ini. Hubungi admin jika ingin mereset perangkat."
    }
}
