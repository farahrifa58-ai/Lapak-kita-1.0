package com.example.data.firebase

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class UserProfileDoc(
    val userId: String,
    val email: String,
    val businessName: String,
    val licenseCode: String,
    val registeredDeviceId: String,
    val active: Boolean
)

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
 * - Stores user profiles & enforces 1 Account = 1 Device (`registeredDeviceId`) in `users/{uid}`.
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
     * Checks `activation_codes/{code}` against server to verify `status == "available"`.
     * Prevents false "used on another device" errors on freshly issued License Admin codes.
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
     * 2. Atomically updates `activation_codes/{code}` -> `status: "used"` + metadata
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
                val existingDevice = existingUserSnap.getString("registeredDeviceId").orEmpty()
                val existingCode = existingUserSnap.getString("licenseCode").orEmpty()
                val existingBiz = existingUserSnap.getString("businessName").orEmpty().ifBlank { cleanBusinessName }
                if (existingDevice == cleanDeviceId || existingDevice.isBlank()) {
                    if (existingDevice.isBlank()) {
                        userRef.update(
                            mapOf(
                                "registeredDeviceId" to cleanDeviceId,
                                "businessName" to cleanBusinessName,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    syncDeviceRegistry(deviceRef, uid, cleanEmail, existingBiz, existingCode.ifBlank { code })
                    return Result.success(
                        UserProfileDoc(
                            userId = uid,
                            email = cleanEmail,
                            businessName = existingBiz,
                            licenseCode = existingCode.ifBlank { code },
                            registeredDeviceId = cleanDeviceId,
                            active = true
                        )
                    )
                } else {
                    return Result.failure(
                        IllegalStateException(SINGLE_DEVICE_LOCK_MESSAGE)
                    )
                }
            }

            // Write atomically in a batch:
            // 1. Update activation_codes/{code} -> status: "used"
            // 2. Create users/{uid}
            // 3. Upsert device_registry/{deviceId}
            val batch = db.batch()
            batch.update(
                codeRef,
                mapOf(
                    "status" to "used",
                    "usedByUid" to uid,
                    "usedByEmail" to cleanEmail,
                    "usedByBusinessName" to cleanBusinessName,
                    "registeredDeviceId" to cleanDeviceId,
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
     * Enforces Single Device Lock (1 Account = 1 Device) on Login and startup online verification.
     * - If `registeredDeviceId` is empty `""` (reset by admin), automatically binds current device.
     * - If `registeredDeviceId == currentDeviceId`, grants access.
     * - If `registeredDeviceId != currentDeviceId`, blocks login with exact warning message.
     */
    suspend fun verifySingleDeviceOnLogin(
        uid: String,
        email: String,
        currentDeviceId: String
    ): LoginDeviceCheckResult {
        val userRef = usersCollection.document(uid)
        val deviceRef = deviceRegistryCollection.document(currentDeviceId)
        val cleanEmail = email.trim()

        return try {
            val snap = userRef.get(Source.SERVER).await()
            if (!snap.exists()) {
                return LoginDeviceCheckResult.NotRegistered
            }

            val active = snap.getBoolean("active") ?: false
            val registeredDeviceId = snap.getString("registeredDeviceId")?.trim().orEmpty()
            val licenseCode = snap.getString("licenseCode")?.trim()?.uppercase().orEmpty()
            val businessName = snap.getString("businessName")?.trim().orEmpty().ifBlank { "Lapak Kita" }
            val profileEmail = snap.getString("email")?.trim().orEmpty().ifBlank { cleanEmail }

            if (!active) {
                return LoginDeviceCheckResult.InactiveAccount(
                    "Akun Anda saat ini tidak aktif. Silakan hubungi admin Lapak Kita."
                )
            }

            if (registeredDeviceId.isBlank()) {
                // Admin cleared registeredDeviceId -> auto-bind to this new device!
                userRef.update(
                    mapOf(
                        "registeredDeviceId" to currentDeviceId,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                runCatching {
                    syncDeviceRegistry(deviceRef, uid, profileEmail, businessName, licenseCode)
                }

                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = profileEmail,
                        businessName = businessName,
                        licenseCode = licenseCode,
                        registeredDeviceId = currentDeviceId,
                        active = true
                    ),
                    wasRebound = true
                )
            }

            if (registeredDeviceId == currentDeviceId) {
                runCatching {
                    syncDeviceRegistry(deviceRef, uid, profileEmail, businessName, licenseCode)
                }
                return LoginDeviceCheckResult.Allowed(
                    profile = UserProfileDoc(
                        userId = uid,
                        email = profileEmail,
                        businessName = businessName,
                        licenseCode = licenseCode,
                        registeredDeviceId = currentDeviceId,
                        active = true
                    ),
                    wasRebound = false
                )
            }

            LoginDeviceCheckResult.BlockedOtherDevice(SINGLE_DEVICE_LOCK_MESSAGE)
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
        const val SINGLE_DEVICE_LOCK_MESSAGE =
            "Akun ini sudah terhubung dengan perangkat lain. Satu akun hanya dapat digunakan di 1 HP."
    }
}
