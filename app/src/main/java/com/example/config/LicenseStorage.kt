package com.example.config

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import java.util.UUID

/**
 * Manages permanent local license activation state and stable device identity
 * without requiring runtime permissions.
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

    fun isLicenseActive(): Boolean {
        val code = prefs.getString(KEY_LICENSE_CODE, null)?.trim()?.uppercase() ?: return false
        val boundDeviceId = prefs.getString(KEY_DEVICE_ID, null)?.trim() ?: return false
        val savedSeal = prefs.getString(KEY_INTEGRITY_SEAL, null) ?: return false
        if (code.isBlank() || boundDeviceId.isBlank()) return false

        val currentDeviceId = getDeviceId()
        if (boundDeviceId != currentDeviceId) return false

        val expectedSeal = computeLicenseSeal(code, boundDeviceId)
        return savedSeal == expectedSeal
    }

    fun getActivatedLicenseCode(): String? {
        if (!isLicenseActive()) return null
        return prefs.getString(KEY_LICENSE_CODE, null)?.trim()?.uppercase()
    }

    fun getActivatedAtMillis(): Long {
        return prefs.getLong(KEY_ACTIVATED_AT, 0L)
    }

    fun savePermanentActivation(
        code: String,
        deviceId: String = getDeviceId(),
        activatedAtMillis: Long = System.currentTimeMillis()
    ) {
        val normalizedCode = code.trim().uppercase()
        val seal = computeLicenseSeal(normalizedCode, deviceId)
        prefs.edit()
            .putString(KEY_LICENSE_CODE, normalizedCode)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_INTEGRITY_SEAL, seal)
            .putLong(KEY_ACTIVATED_AT, activatedAtMillis)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "lapak_kita_license_prefs"
        private const val KEY_LICENSE_CODE = "active_license_code"
        private const val KEY_DEVICE_ID = "bound_device_id"
        private const val KEY_INTEGRITY_SEAL = "license_integrity_seal"
        private const val KEY_ACTIVATED_AT = "license_activated_at"
        private const val KEY_FALLBACK_DEVICE_ID = "fallback_device_id"

        // SHA-256 digest of salted Admin Generator PIN (UI gate; real write auth enforced in Firestore rules)
        private const val ADMIN_PIN_SALT = "lapakkita-dev-pin-v1:"
        private const val ADMIN_PIN_HASH =
            "c93e53db6a800ff84a3e143df6394ca8d11275f969e14f568c58b28625e9a217"

        const val DEVELOPER_EMAIL = "rendykurniawan0311@gmail.com"
        const val LICENSE_APP_NAME = "Lapak Kita"
        const val LICENSE_PRODUCT_KEY = "lapak_kita"

        fun verifyAdminPin(inputPin: String): Boolean {
            val digest = sha256Hex(ADMIN_PIN_SALT + inputPin.trim())
            return digest.equals(ADMIN_PIN_HASH, ignoreCase = true)
        }

        private fun computeLicenseSeal(code: String, deviceId: String): String {
            return sha256Hex("lapak-kita-license-seal-v1:$code:$deviceId")
        }

        private fun sha256Hex(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
