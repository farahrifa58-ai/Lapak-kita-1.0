package com.example.data.firebase

import android.util.Log
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

/**
 * 100% synced with the external "License Admin" Firestore database:
 * - Reads & claims activation codes from `activation_codes/{code}` verifying `status == "available"`.
 * - Stores user profiles & enforces Multi-Device Lock (up to 2 devices via `registeredDeviceIds` array,
 *   while maintaining full backward compatibility with legacy single-string `registeredDeviceId`).
 * - Maintains exact-ID `device_registry/{deviceId}` for seamless same-HP auto-restore after reinstall.
 */
class ActivationAuthRepository(
    private val db: FirebaseFirestore
) {
    private val activationCodesCollection = db.collection("activation_codes")
    private val usersCollection = db.collection("users")
    private val deviceRegistryCollection = db.collection("device_registry")

    fun normalizeCode(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9_\\-]"), "")

    /**
     * Step 1 helper: reads raw `registeredDeviceIds` array if present (or empty list).
     */
    private fun extractRawDeviceArray(snap: DocumentSnapshot): List<String> {
        val rawList = snap.get("registeredDeviceIds") as? List<*> ?: return emptyList()
        val result = linkedSetOf<String>()
        rawList.forEach { entry ->
            val id = (entry as? String)?.trim().orEmpty()
            if (id.isNotEmpty()) result.add(id)
        }
        return result.toList()
    }

    /**
     * Legacy auto-migration Steps 1 & 2:
     * 1. Build initial `existingDevices` from `registeredDeviceIds` array (or empty list).
     * 2. If legacy `registeredDeviceId` is non-empty and not yet in `existingDevices`,
     *    add it so the original HP #1 stays recorded as device #1.
     */
    private fun extractRegisteredDeviceIds(snap: DocumentSnapshot): List<String> {
        val existingDevices = extractRawDeviceArray(snap).toMutableList()
        val legacySingle = snap.getString("registeredDeviceId")?.trim().orEmpty()
        if (legacySingle.isNotEmpty() && legacySingle !in existingDevices) {
            existingDevices.add(legacySingle)
        }
        return existingDevices
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
                UserProfileDoc(
                    userId = snap.getString("userId")?.trim().orEmpty().ifBlank { uid },
                    email = snap.getString("email")?.trim().orEmpty(),
                    businessName = snap.getString("businessName")?.trim().orEmpty().ifBlank { "Lapak Kita" },
                    licenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty(),
                    registeredDeviceId = deviceIds.firstOrNull().orEmpty(),
                    registeredDeviceIds = deviceIds,
                    active = snap.getBoolean("active") ?: false
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                Log.w(TAG, "observeUserProfile($path) warning: ${rootError.message}")
            }
    }

    /**
     * Checks `activation_codes/{code}` against server to verify `status == "available"`.
     */
    suspend fun checkActivationCodeAvailable(rawCode: String): CodeAvailabilityResult {
        val code = normalizeCode(rawCode)
        if (code.length < 4 || code.length > 48) {
            return CodeAvailabilityResult.InvalidOrUsed("Kode aktivasi tidak valid atau sudah pernah digunakan.")
        }

        return try {
            val snapshot = activationCodesCollection.document(code).get(Source.SERVER).await()
            if (!snapshot.exists()) {
                return CodeAvailabilityResult.InvalidOrUsed("Kode aktivasi tidak valid atau sudah pernah digunakan.")
            }

            val status = snapshot.getString("status")?.trim()?.lowercase().orEmpty()
            if (status != "available") {
                return CodeAvailabilityResult.InvalidOrUsed("Kode aktivasi tidak valid atau sudah pernah digunakan.")
            }

            CodeAvailabilityResult.Available(normalizedCode = code)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Activation code check error for $code: ${e.code}", e)
            when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    CodeAvailabilityResult.NetworkError("Tidak dapat terhubung ke server. Periksa koneksi internet Anda.")
                else ->
                    CodeAvailabilityResult.InvalidOrUsed("Kode aktivasi tidak valid atau sudah pernah digunakan.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected activation code check error", e)
            CodeAvailabilityResult.NetworkError("Gagal memeriksa kode aktivasi. Periksa koneksi internet Anda.")
        }
    }

    /**
     * Completes new account activation after Google Sign-In succeeds:
     * 1. Re-verifies `activation_codes/{code}` has `status == "available"`.
     * 2. Atomically updates `activation_codes/{code}` -> `status: "used"` + `registeredDeviceIds: [deviceId]`
     *    and writes `users/{uid}` + `device_registry/{deviceId}`.
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

        val codeRef = activationCodesCollection.document(code)
        val userRef = usersCollection.document(uid)
        val deviceRef = deviceRegistryCollection.document(cleanDeviceId)

        return try {
            // Re-verify on server right before claiming
            val codeSnap = codeRef.get(Source.SERVER).await()
            val status = codeSnap.getString("status")?.trim()?.lowercase().orEmpty()
            if (!codeSnap.exists() || status != "available") {
                return Result.failure(IllegalStateException("Kode aktivasi tidak valid atau sudah pernah digunakan."))
            }

            // Check if this Google account already exists with an active profile
            val existingUserSnap = userRef.get(Source.SERVER).await()
            if (existingUserSnap.exists() && existingUserSnap.getBoolean("active") == true) {
                val rawArrayDevices = extractRawDeviceArray(existingUserSnap)
                val hasArrayField = existingUserSnap.get("registeredDeviceIds") is List<*>
                val existingDevices = extractRegisteredDeviceIds(existingUserSnap)
                val needsLegacyMigration = !hasArrayField || rawArrayDevices != existingDevices
                val existingCode = existingUserSnap.getString("licenseCode").orEmpty().ifBlank { code }
                val existingBiz = existingUserSnap.getString("businessName").orEmpty().ifBlank { cleanBusinessName }

                if (cleanDeviceId in existingDevices) {
                    if (needsLegacyMigration) {
                        runCatching {
                            userRef.update(
                                mapOf(
                                    "registeredDeviceId" to existingDevices.first(),
                                    "registeredDeviceIds" to existingDevices,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        }
                        syncActivationCodeDevices(existingCode, existingDevices)
                    }
                    syncDeviceRegistry(deviceRef, uid, cleanEmail, existingBiz, existingCode)
                    return Result.success(
                        UserProfileDoc(
                            userId = uid,
                            email = cleanEmail,
                            businessName = existingBiz,
                            licenseCode = existingCode,
                            registeredDeviceId = existingDevices.firstOrNull().orEmpty(),
                            registeredDeviceIds = existingDevices,
                            active = true
                        )
                    )
                } else if (existingDevices.size < MAX_REGISTERED_DEVICES) {
                    val updatedDevices = (existingDevices + cleanDeviceId).distinct().take(MAX_REGISTERED_DEVICES)
                    userRef.update(
                        mapOf(
                            "registeredDeviceId" to updatedDevices.first(),
                            "registeredDeviceIds" to updatedDevices,
                            "businessName" to existingBiz,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                    syncActivationCodeDevices(existingCode, updatedDevices)
                    syncDeviceRegistry(deviceRef, uid, cleanEmail, existingBiz, existingCode)
                    return Result.success(
                        UserProfileDoc(
                            userId = uid,
                            email = cleanEmail,
                            businessName = existingBiz,
                            licenseCode = existingCode,
                            registeredDeviceId = updatedDevices.first(),
                            registeredDeviceIds = updatedDevices,
                            active = true
                        )
                    )
                } else {
                    return Result.failure(IllegalStateException(MAX_DEVICES_LOCK_MESSAGE))
                }
            }

            val initialDevices = listOf(cleanDeviceId)

            // Write atomically in a batch:
            // 1. Update activation_codes/{code} -> status: "used" + registeredDeviceIds
            // 2. Create users/{uid} with registeredDeviceIds (max 2 devices)
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
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()

            // Best-effort device registry update for reinstall auto-restore
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
                    "Kode aktivasi tidak valid atau sudah pernah digunakan."
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Koneksi internet terputus saat mengaktifkan akun. Coba lagi."
                else ->
                    "Gagal mendaftarkan akun. Silakan coba lagi."
            }
            Result.failure(IllegalStateException(msg))
        } catch (e: Exception) {
            Log.e(TAG, "Registration failed", e)
            Result.failure(IllegalStateException(e.message ?: "Gagal mengaktifkan akun."))
        }
    }

    /**
     * Enforces Multi-Device Lock (Max 2 Devices = HP Kasir + HP Owner) with automatic legacy migration:
     * 1. Build `existingDevices` from `registeredDeviceIds` array (or empty list if missing).
     * 2. If legacy `registeredDeviceId` is non-empty and not in `existingDevices`, add it first so HP #1 stays device #1.
     * 3. If `currentDeviceId` is already in `existingDevices` -> auto-migrate array if needed and allow immediately.
     * 4. If `existingDevices.size < 2` -> append `currentDeviceId` (as device #2), save `registeredDeviceIds` to
     *    `users/{uid}` and `activation_codes/{licenseCode}`, then allow into Dashboard.
     * 5. If `existingDevices.size >= 2` and `currentDeviceId` is not among them -> reject login with max-2-devices error.
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
            if (!snap.exists()) {
                return LoginDeviceCheckResult.NotRegistered
            }

            val active = snap.getBoolean("active") ?: false
            val rawArrayDevices = extractRawDeviceArray(snap)
            val hasArrayField = snap.get("registeredDeviceIds") is List<*>
            // Steps 1 & 2: build existingDevices from registeredDeviceIds + legacy registeredDeviceId
            val existingDevices = extractRegisteredDeviceIds(snap)
            val needsLegacyMigration = !hasArrayField || rawArrayDevices != existingDevices
            val licenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty()
            val businessName = snap.getString("businessName")?.trim().orEmpty().ifBlank { "Lapak Kita" }
            val profileEmail = snap.getString("email")?.trim().orEmpty().ifBlank { cleanEmail }

            if (!active) {
                return LoginDeviceCheckResult.InactiveAccount(
                    "Akun Anda saat ini tidak aktif. Silakan hubungi admin Lapak Kita."
                )
            }

            // Step 3: Current device is already in existingDevices -> allow immediately (and backfill array if legacy)
            if (cleanDeviceId in existingDevices) {
                if (needsLegacyMigration) {
                    runCatching {
                        userRef.update(
                            mapOf(
                                "registeredDeviceId" to existingDevices.first(),
                                "registeredDeviceIds" to existingDevices,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    syncActivationCodeDevices(licenseCode, existingDevices)
                }

                runCatching {
                    syncDeviceRegistry(deviceRef, uid, profileEmail, businessName, licenseCode)
                }

                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = profileEmail,
                        businessName = businessName,
                        licenseCode = licenseCode,
                        registeredDeviceId = existingDevices.firstOrNull().orEmpty(),
                        registeredDeviceIds = existingDevices,
                        active = true
                    ),
                    wasRebound = false
                )
            }

            // Step 4: existingDevices.size < 2 -> append currentDeviceId (preserves legacy HP #1 as index 0),
            // save registeredDeviceIds to users/{uid} and activation_codes/{code}, then allow into Dashboard.
            if (existingDevices.size < MAX_REGISTERED_DEVICES) {
                val updatedDevices = (existingDevices + cleanDeviceId).distinct().take(MAX_REGISTERED_DEVICES)
                userRef.update(
                    mapOf(
                        "registeredDeviceId" to updatedDevices.first(),
                        "registeredDeviceIds" to updatedDevices,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                syncActivationCodeDevices(licenseCode, updatedDevices)

                runCatching {
                    syncDeviceRegistry(deviceRef, uid, profileEmail, businessName, licenseCode)
                }

                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = profileEmail,
                        businessName = businessName,
                        licenseCode = licenseCode,
                        registeredDeviceId = updatedDevices.first(),
                        registeredDeviceIds = updatedDevices,
                        active = true
                    ),
                    wasRebound = true
                )
            }

            // Step 5: existingDevices.size >= 2 and currentDeviceId is not one of them -> block 3rd device!
            LoginDeviceCheckResult.BlockedOtherDevice(MAX_DEVICES_LOCK_MESSAGE)
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
        if (code.length < 4 || updatedDevices.isEmpty()) return
        runCatching {
            activationCodesCollection.document(code).update(
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
     * Checks `device_registry/{currentDeviceId}` when opening the app without a local session
     * (e.g., after reinstalling on the exact same phone) to detect an already-registered account.
     */
    suspend fun lookupDeviceRegistration(currentDeviceId: String): DeviceAutoRestoreHint? {
        if (currentDeviceId.length < 6) return null
        return try {
            val snap = deviceRegistryCollection.document(currentDeviceId).get(Source.SERVER).await()
            if (!snap.exists()) return null
            val active = snap.getBoolean("active") ?: false
            if (!active) return null
            val ownerUid = snap.getString("ownerUid")?.trim().orEmpty()
            val emailHint = snap.getString("emailHint")?.trim().orEmpty()
            val businessName = snap.getString("businessName")?.trim().orEmpty()
            val licenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty()
            if (ownerUid.isBlank() || emailHint.isBlank()) return null

            DeviceAutoRestoreHint(
                ownerUid = ownerUid,
                emailHint = emailHint,
                businessName = businessName,
                licenseCode = licenseCode,
                active = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Device auto-restore lookup skipped: ${e.message}")
            null
        }
    }

    private suspend fun syncDeviceRegistry(
        deviceRef: com.google.firebase.firestore.DocumentReference,
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
        const val MAX_REGISTERED_DEVICES = 2
        const val MAX_DEVICES_LOCK_MESSAGE =
            "Batas maksimal 2 perangkat telah tercapai untuk akun ini. Hubungi admin jika ingin mereset perangkat."
    }
}
