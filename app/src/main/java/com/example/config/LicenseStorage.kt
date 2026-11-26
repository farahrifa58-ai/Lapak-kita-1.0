package com.example.config

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

data class CachedUserSession(
    val uid: String,
    val email: String,
    val businessName: String,
    val licenseCode: String,
    val registeredDeviceId: String,
    val active: Boolean,
    val lastSyncedAtMillis: Long
)

/**
 * Manages stable device identity and offline-first cached activation/session state
 * so licensed users can continue running Lapak Kita smoothly offline.
 */
class LicenseStorage(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        val rawAndroidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()

        val sanitizedAndroidId = rawAndroidId.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        if (sanitizedAndroidId.length >= 6 && sanitizedAndroidId != "9774D56D682E549C") {
            return "AND-$sanitizedAndroidId"
        }

        val cached = prefs.getString(KEY_FALLBACK_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) return cached

        val generated = "DEV-${UUID.randomUUID().toString().replace("-", "").take(16).uppercase()}"
        prefs.edit().putString(KEY_FALLBACK_DEVICE_ID, generated).apply()
        return generated
    }

    fun saveOfflineSession(
        uid: String,
        email: String,
        businessName: String,
        licenseCode: String,
        deviceId: String = getDeviceId(),
        active: Boolean = true,
        syncedAtMillis: Long = System.currentTimeMillis()
    ) {
        val normalizedCode = licenseCode.trim().uppercase()
        val seal = computeSessionSeal(uid, normalizedCode, deviceId)
        prefs.edit()
            .putString(KEY_SESSION_UID, uid.trim())
            .putString(KEY_SESSION_EMAIL, email.trim())
            .putString(KEY_SESSION_BUSINESS_NAME, businessName.trim())
            .putString(KEY_LICENSE_CODE, normalizedCode)
            .putString(KEY_DEVICE_ID, deviceId.trim())
            .putBoolean(KEY_SESSION_ACTIVE, active)
            .putString(KEY_INTEGRITY_SEAL, seal)
            .putLong(KEY_ACTIVATED_AT, syncedAtMillis)
            .apply()
    }

    fun getOfflineSession(): CachedUserSession? {
        val uid = prefs.getString(KEY_SESSION_UID, null)?.trim().orEmpty()
        val email = prefs.getString(KEY_SESSION_EMAIL, null)?.trim().orEmpty()
        val businessName = prefs.getString(KEY_SESSION_BUSINESS_NAME, null)?.trim().orEmpty()
        val code = prefs.getString(KEY_LICENSE_CODE, null)?.trim()?.uppercase().orEmpty()
        val boundDeviceId = prefs.getString(KEY_DEVICE_ID, null)?.trim().orEmpty()
        val active = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        val savedSeal = prefs.getString(KEY_INTEGRITY_SEAL, null).orEmpty()
        val syncedAt = prefs.getLong(KEY_ACTIVATED_AT, 0L)

        if (uid.isBlank() || code.isBlank() || boundDeviceId.isBlank() || !active) return null
        if (boundDeviceId != getDeviceId()) return null

        val expectedSeal = computeSessionSeal(uid, code, boundDeviceId)
        if (savedSeal != expectedSeal) return null

        return CachedUserSession(
            uid = uid,
            email = email,
            businessName = businessName,
            licenseCode = code,
            registeredDeviceId = boundDeviceId,
            active = active,
            lastSyncedAtMillis = syncedAt
        )
    }

    fun isLocallyLicensedFor(uid: String): Boolean {
        val session = getOfflineSession() ?: return false
        return session.uid == uid && session.active && session.registeredDeviceId == getDeviceId()
    }

    fun getActivatedLicenseCode(): String? {
        return getOfflineSession()?.licenseCode
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_UID)
            .remove(KEY_SESSION_EMAIL)
            .remove(KEY_SESSION_BUSINESS_NAME)
            .remove(KEY_LICENSE_CODE)
            .remove(KEY_DEVICE_ID)
            .remove(KEY_SESSION_ACTIVE)
            .remove(KEY_INTEGRITY_SEAL)
            .remove(KEY_ACTIVATED_AT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "lapak_kita_license_prefs"
        private const val KEY_SESSION_UID = "session_uid"
        private const val KEY_SESSION_EMAIL = "session_email"
        private const val KEY_SESSION_BUSINESS_NAME = "session_business_name"
        private const val KEY_LICENSE_CODE = "active_license_code"
        private const val KEY_DEVICE_ID = "bound_device_id"
        private const val KEY_SESSION_ACTIVE = "session_active"
        private const val KEY_INTEGRITY_SEAL = "license_integrity_seal"
        private const val KEY_ACTIVATED_AT = "license_activated_at"
        private const val KEY_FALLBACK_DEVICE_ID = "fallback_device_id"

        const val DEVELOPER_EMAIL = "rendykurniawan0311@gmail.com"
        const val LICENSE_APP_NAME = "Lapak Kita"

        private fun computeSessionSeal(uid: String, code: String, deviceId: String): String {
            return sha256Hex("lapak-kita-session-v2:$uid:$code:$deviceId")
        }

        private fun sha256Hex(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
