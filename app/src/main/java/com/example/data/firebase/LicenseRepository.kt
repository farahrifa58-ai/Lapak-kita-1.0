package com.example.data.firebase

import android.util.Log
import com.example.config.LicenseStorage
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom

data class LicenseCodeRecord(
    val code: String,
    val appName: String,
    val productKey: String,
    val buyerNote: String,
    val used: Boolean,
    val usedByDeviceId: String,
    val usedAtMillis: Long?,
    val createdByUid: String,
    val createdAtMillis: Long
)

sealed class LicenseActivationOutcome {
    data class Activated(val code: String, val isReinstallRestore: Boolean = false) : LicenseActivationOutcome()
    data class InvalidCode(val message: String = "Kode lisensi tidak valid") : LicenseActivationOutcome()
    data class AlreadyUsedElsewhere(val message: String = "Kode lisensi sudah digunakan di perangkat lain") : LicenseActivationOutcome()
    data class Error(val message: String) : LicenseActivationOutcome()
}

class LicenseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val licensesCollection = db.collection("license_codes")
    private val secureRandom = SecureRandom()

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Silakan masuk dengan akun Google Developer terlebih dahulu.")

    fun normalizeLicenseCode(raw: String): String =
        raw.trim().uppercase().replace(" ", "")

    fun isValidCodeFormat(code: String): Boolean =
        code.length in 6..32 && LICENSE_CODE_REGEX.matches(code)

    /**
     * Validates a license code against Firebase Firestore (`license_codes/{code}`).
     * - Unknown code -> InvalidCode ("Kode lisensi tidak valid")
     * - Used on same device (reinstall) -> Activated(isReinstallRestore = true)
     * - Used on another device -> AlreadyUsedElsewhere ("Kode lisensi sudah digunakan di perangkat lain")
     * - Unused & valid -> Atomically marks isUsed=true, usedByDeviceId, usedAt, appName="Lapak Kita"
     */
    suspend fun activateLicenseCode(
        rawCode: String,
        deviceId: String
    ): LicenseActivationOutcome {
        val code = normalizeLicenseCode(rawCode)
        if (!isValidCodeFormat(code)) {
            return LicenseActivationOutcome.InvalidCode("Kode lisensi tidak valid")
        }

        val docRef = licensesCollection.document(code)
        val path = "license_codes/$code"

        return try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return LicenseActivationOutcome.InvalidCode("Kode lisensi tidak valid")
            }

            // Ensure product/app compatibility while allowing codes from a shared multi-app generator
            val docProductKey = snap.getString("productKey")?.trim().orEmpty()
            val docAppName = snap.getString("appName")?.trim().orEmpty()
            val isProductCompatible = docProductKey.isEmpty() ||
                docProductKey.equals(LicenseStorage.LICENSE_PRODUCT_KEY, ignoreCase = true) ||
                docProductKey.equals("all", ignoreCase = true) ||
                docAppName.contains("lapak", ignoreCase = true)
            if (!isProductCompatible) {
                return LicenseActivationOutcome.InvalidCode("Kode lisensi tidak valid untuk aplikasi Lapak Kita")
            }

            val isUsed = snap.getBoolean("isUsed") ?: false
            val existingDeviceId = snap.getString("usedByDeviceId")?.trim().orEmpty()

            if (isUsed) {
                return if (existingDeviceId.isNotEmpty() && existingDeviceId == deviceId) {
                    LicenseActivationOutcome.Activated(code = code, isReinstallRestore = true)
                } else {
                    LicenseActivationOutcome.AlreadyUsedElsewhere("Kode lisensi sudah digunakan di perangkat lain")
                }
            }

            // Claim unused code once and bind permanently to this deviceId
            docRef.update(
                mapOf(
                    "isUsed" to true,
                    "usedByDeviceId" to deviceId,
                    "usedAt" to FieldValue.serverTimestamp(),
                    "appName" to LicenseStorage.LICENSE_APP_NAME,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            LicenseActivationOutcome.Activated(code = code, isReinstallRestore = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                // Can occur if another device claimed the exact same code concurrently
                LicenseActivationOutcome.AlreadyUsedElsewhere("Kode lisensi sudah digunakan di perangkat lain")
            } else if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                LicenseActivationOutcome.Error("Tidak ada koneksi internet. Hubungkan ke internet untuk aktivasi pertama kali.")
            } else {
                Log.e("LicenseRepository", "Firestore error activating $path", e)
                LicenseActivationOutcome.Error("Gagal memverifikasi kode lisensi: ${e.message ?: "kesalahan server"}")
            }
        } catch (e: Exception) {
            Log.e("LicenseRepository", "Activation error for $path", e)
            LicenseActivationOutcome.Error("Gagal memverifikasi lisensi. Pastikan internet aktif lalu coba lagi.")
        }
    }

    /**
     * Developer-only: Generates a unique random `LPK-XXXX-XXXX` code and stores it in Firestore.
     */
    suspend fun generateAndSaveNewLicense(buyerNote: String): Result<LicenseCodeRecord> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val cleanNote = buyerNote.trim().take(120)

        return try {
            var chosenCode = generateRandomLicenseCode()
            var attempts = 0
            while (attempts < 5) {
                val existing = licensesCollection.document(chosenCode).get().await()
                if (!existing.exists()) break
                chosenCode = generateRandomLicenseCode()
                attempts++
            }

            val payload = mapOf(
                "code" to chosenCode,
                "appName" to LicenseStorage.LICENSE_APP_NAME,
                "productKey" to LicenseStorage.LICENSE_PRODUCT_KEY,
                "buyerNote" to cleanNote,
                "isUsed" to false,
                "usedByDeviceId" to "",
                "createdByUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            licensesCollection.document(chosenCode).set(payload).await()

            Result.success(
                LicenseCodeRecord(
                    code = chosenCode,
                    appName = LicenseStorage.LICENSE_APP_NAME,
                    productKey = LicenseStorage.LICENSE_PRODUCT_KEY,
                    buyerNote = cleanNote,
                    used = false,
                    usedByDeviceId = "",
                    usedAtMillis = null,
                    createdByUid = uid,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Result.failure(
                    IllegalStateException(
                        "Akses ditolak. Masuk menggunakan akun Google Developer resmi (${LicenseStorage.DEVELOPER_EMAIL}) untuk membuat kode lisensi."
                    )
                )
            } else {
                try {
                    handleFirestoreError(e, OperationType.CREATE, "license_codes")
                } catch (wrapped: IllegalStateException) {
                    Result.failure(wrapped)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Developer-only: Deletes a license document from Firestore.
     */
    suspend fun deleteLicense(code: String): Result<Unit> {
        try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val normalized = normalizeLicenseCode(code)
        val path = "license_codes/$normalized"
        return try {
            licensesCollection.document(normalized).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.DELETE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Developer-only: Real-time observer of all license codes sorted newest-first.
     */
    fun observeAllLicenses(): Flow<List<LicenseCodeRecord>> {
        val path = "license_codes"
        return licensesCollection
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseLicenseDoc(it) }
                    .sortedByDescending { it.createdAtMillis }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    private fun parseLicenseDoc(doc: DocumentSnapshot): LicenseCodeRecord? {
        val code = doc.getString("code")?.trim()?.uppercase()?.ifBlank { doc.id } ?: doc.id
        if (code.isBlank()) return null

        val createdAt = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time ?: 0L
        val usedAt = doc.getTimestamp("usedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            ?.toDate()?.time

        return LicenseCodeRecord(
            code = code,
            appName = doc.getString("appName").orEmpty().ifBlank { LicenseStorage.LICENSE_APP_NAME },
            productKey = doc.getString("productKey").orEmpty().ifBlank { LicenseStorage.LICENSE_PRODUCT_KEY },
            buyerNote = doc.getString("buyerNote").orEmpty(),
            used = doc.getBoolean("isUsed") ?: false,
            usedByDeviceId = doc.getString("usedByDeviceId").orEmpty(),
            usedAtMillis = usedAt,
            createdByUid = doc.getString("createdByUid").orEmpty(),
            createdAtMillis = createdAt
        )
    }

    private fun generateRandomLicenseCode(): String {
        fun randomBlock(len: Int): String = buildString(len) {
            repeat(len) {
                append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
            }
        }
        return "LPK-${randomBlock(4)}-${randomBlock(4)}"
    }

    companion object {
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val LICENSE_CODE_REGEX = Regex("^[A-Z0-9][A-Z0-9\\-]{5,31}$")
    }
}
