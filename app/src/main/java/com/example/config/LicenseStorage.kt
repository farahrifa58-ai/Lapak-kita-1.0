package com.example.config

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.example.data.database.LapakDatabase
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class CachedUserSession(
    val uid: String,
    val email: String,
    val businessName: String,
    val licenseCode: String,
    val registeredDeviceId: String,
    val connectedDevicesCount: Int = 1,
    val isOwnerDevice: Boolean = true,
    val active: Boolean,
    val lastSyncedAtMillis: Long
)

/**
 * Manages stable device identity and strictly UID-scoped offline session caches
 * (`lapakkita_{uid}_session_prefs` + `lapakkita_{uid}_*` keys) so multiple accounts
 * used on the same phone never mix session metadata or operational caches.
 */
class LicenseStorage(context: Context) {

    private val appContext = context.applicationContext
    private val devicePrefs = appContext.getSharedPreferences(DEVICE_PREFS_NAME, Context.MODE_PRIVATE)

    private fun userSessionPrefs(uid: String): SharedPreferences {
        val safeUid = sanitizeUidForStorageKey(uid)
        return appContext.getSharedPreferences("lapakkita_${safeUid}_session_prefs", Context.MODE_PRIVATE)
    }

    private fun uidKey(uid: String, suffix: String): String {
        val safeUid = sanitizeUidForStorageKey(uid)
        return "lapakkita_${safeUid}_$suffix"
    }

    /**
     * One-time startup migration: removes legacy global (non-UID-prefixed) local caches
     * from earlier versions so leftover test data never leaks into newly signed-in accounts:
     * 1. Preserves `fallback_device_id` into device-level prefs so hardware lock ID remains stable.
     * 2. Deletes legacy global Room SQLite database (`lapak_kita_database` + WAL/SHM/journal).
     * 3. Clears and deletes legacy un-prefixed SharedPreferences (`lapak_kita_license_prefs`).
     * 4. Deletes legacy un-prefixed global internal images (`shop_logo.jpg`, `poster_default_bg.jpg`).
     */
    fun cleanupLegacyGlobalCacheOnce() {
        if (devicePrefs.getBoolean(KEY_LEGACY_CACHE_CLEANED_V1, false)) {
            return
        }

        val legacyPrefs = appContext.getSharedPreferences(LEGACY_GLOBAL_PREFS_NAME, Context.MODE_PRIVATE)
        val existingFallbackDeviceId = legacyPrefs.getString(KEY_FALLBACK_DEVICE_ID, null)?.trim()
        if (!existingFallbackDeviceId.isNullOrBlank() &&
            devicePrefs.getString(KEY_FALLBACK_DEVICE_ID, null).isNullOrBlank()
        ) {
            devicePrefs.edit().putString(KEY_FALLBACK_DEVICE_ID, existingFallbackDeviceId).apply()
        }

        // 1. Delete old global un-prefixed Room database
        LapakDatabase.deleteLegacyGlobalDatabase(appContext)

        // 2. Clear all legacy un-prefixed global session keys
        legacyPrefs.edit().clear().commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { appContext.deleteSharedPreferences(LEGACY_GLOBAL_PREFS_NAME) }
        }

        // 3. Delete old un-prefixed global internal image files
        LEGACY_GLOBAL_INTERNAL_FILES.forEach { fileName ->
            runCatching {
                val file = File(appContext.filesDir, fileName)
                if (file.exists()) file.delete()
            }
        }

        // 4. Mark migration complete
        devicePrefs.edit().putBoolean(KEY_LEGACY_CACHE_CLEANED_V1, true).commit()
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(): String {
        val rawAndroidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()

        val sanitizedAndroidId = rawAndroidId.replace(Regex("[^a-zA-Z0-9]"), "").uppercase()
        if (sanitizedAndroidId.length >= 6 && sanitizedAndroidId != "9774D56D682E549C") {
            return "AND-$sanitizedAndroidId"
        }

        val cached = devicePrefs.getString(KEY_FALLBACK_DEVICE_ID, null)
        if (!cached.isNullOrBlank()) return cached

        val generated = "DEV-${UUID.randomUUID().toString().replace("-", "").take(16).uppercase()}"
        devicePrefs.edit().putString(KEY_FALLBACK_DEVICE_ID, generated).apply()
        return generated
    }

    fun saveOfflineSession(
        uid: String,
        email: String,
        businessName: String,
        licenseCode: String,
        deviceId: String = getDeviceId(),
        connectedDevicesCount: Int = 1,
        isOwnerDevice: Boolean = true,
        active: Boolean = true,
        syncedAtMillis: Long = System.currentTimeMillis()
    ) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        val normalizedCode = licenseCode.trim().uppercase()
        val seal = computeSessionSeal(cleanUid, normalizedCode, deviceId)
        val uPrefs = userSessionPrefs(cleanUid)

        uPrefs.edit()
            .putString(uidKey(cleanUid, "session_uid"), cleanUid)
            .putString(uidKey(cleanUid, "session_email"), email.trim())
            .putString(uidKey(cleanUid, "session_business_name"), businessName.trim())
            .putString(uidKey(cleanUid, "license_code"), normalizedCode)
            .putString(uidKey(cleanUid, "device_id"), deviceId.trim())
            .putInt(uidKey(cleanUid, "connected_devices_count"), connectedDevicesCount.coerceIn(1, 2))
            .putBoolean(uidKey(cleanUid, "is_owner_device"), isOwnerDevice)
            .putBoolean(uidKey(cleanUid, "session_active"), active)
            .putBoolean(uidKey(cleanUid, "is_activated"), active)
            .putString(uidKey(cleanUid, "integrity_seal"), seal)
            .putLong(uidKey(cleanUid, "activated_at"), syncedAtMillis)
            .apply()

        devicePrefs.edit().putString(KEY_ACTIVE_SESSION_UID, cleanUid).apply()
    }

    fun updateConnectedDevicesCount(uid: String, count: Int) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        userSessionPrefs(cleanUid).edit()
            .putInt(uidKey(cleanUid, "connected_devices_count"), count.coerceIn(0, 2))
            .apply()
    }

    fun getCachedConnectedDevicesCount(uid: String): Int {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return 1
        return userSessionPrefs(cleanUid)
            .getInt(uidKey(cleanUid, "connected_devices_count"), 1)
            .coerceIn(1, 2)
    }

    fun updateIsOwnerDevice(uid: String, isOwner: Boolean) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        userSessionPrefs(cleanUid).edit()
            .putBoolean(uidKey(cleanUid, "is_owner_device"), isOwner)
            .apply()
    }

    fun getCachedIsOwnerDevice(uid: String): Boolean {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return true
        return userSessionPrefs(cleanUid)
            .getBoolean(uidKey(cleanUid, "is_owner_device"), true)
    }

    fun getOfflineSession(targetUid: String? = null): CachedUserSession? {
        val resolvedUid = targetUid?.trim()?.takeIf { it.isNotBlank() }
            ?: devicePrefs.getString(KEY_ACTIVE_SESSION_UID, null)?.trim().orEmpty()
        if (resolvedUid.isBlank()) return null

        val uPrefs = userSessionPrefs(resolvedUid)
        val uid = uPrefs.getString(uidKey(resolvedUid, "session_uid"), null)?.trim().orEmpty()
        val email = uPrefs.getString(uidKey(resolvedUid, "session_email"), null)?.trim().orEmpty()
        val businessName = uPrefs.getString(uidKey(resolvedUid, "session_business_name"), null)?.trim().orEmpty()
        val code = uPrefs.getString(uidKey(resolvedUid, "license_code"), null)?.trim()?.uppercase().orEmpty()
        val boundDeviceId = uPrefs.getString(uidKey(resolvedUid, "device_id"), null)?.trim().orEmpty()
        val connectedCount = uPrefs.getInt(uidKey(resolvedUid, "connected_devices_count"), 1).coerceIn(1, 2)
        val isOwnerDevice = uPrefs.getBoolean(uidKey(resolvedUid, "is_owner_device"), true)
        val active = uPrefs.getBoolean(uidKey(resolvedUid, "session_active"), false)
        val savedSeal = uPrefs.getString(uidKey(resolvedUid, "integrity_seal"), null).orEmpty()
        val syncedAt = uPrefs.getLong(uidKey(resolvedUid, "activated_at"), 0L)

        if (uid.isBlank() || uid != resolvedUid || code.isBlank() || boundDeviceId.isBlank() || !active) return null
        if (boundDeviceId != getDeviceId()) return null

        val expectedSeal = computeSessionSeal(uid, code, boundDeviceId)
        if (savedSeal != expectedSeal) return null

        return CachedUserSession(
            uid = uid,
            email = email,
            businessName = businessName,
            licenseCode = code,
            registeredDeviceId = boundDeviceId,
            connectedDevicesCount = connectedCount,
            isOwnerDevice = isOwnerDevice,
            active = active,
            lastSyncedAtMillis = syncedAt
        )
    }

    fun isLocallyLicensedFor(uid: String): Boolean {
        val session = getOfflineSession(uid) ?: return false
        return session.uid == uid && session.active && session.registeredDeviceId == getDeviceId()
    }

    fun getActivatedLicenseCode(uid: String? = null): String? {
        return getOfflineSession(uid)?.licenseCode
    }

    fun saveAutoClosingPrefs(
        uid: String,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        leftoverMode: String,
        lastClosedDate: String? = null
    ) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        val editor = userSessionPrefs(cleanUid).edit()
            .putBoolean(uidKey(cleanUid, "auto_closing_enabled"), enabled)
            .putInt(uidKey(cleanUid, "auto_closing_hour"), hour.coerceIn(0, 23))
            .putInt(uidKey(cleanUid, "auto_closing_minute"), minute.coerceIn(0, 59))
            .putString(uidKey(cleanUid, "auto_closing_leftover_mode"), leftoverMode.trim().uppercase())
        if (lastClosedDate != null) {
            editor.putString(uidKey(cleanUid, "auto_closing_last_closed_date"), lastClosedDate.trim())
        }
        editor.apply()
    }

    fun getAutoClosingLastClosedDate(uid: String): String {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return ""
        return userSessionPrefs(cleanUid)
            .getString(uidKey(cleanUid, "auto_closing_last_closed_date"), "")
            .orEmpty()
    }

    fun saveCashierVisibilityPrefs(
        uid: String,
        showOmzet: Boolean,
        showProfit: Boolean,
        showExpenses: Boolean
    ) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        userSessionPrefs(cleanUid).edit()
            .putBoolean(uidKey(cleanUid, "cashier_show_omzet"), showOmzet)
            .putBoolean(uidKey(cleanUid, "cashier_show_profit"), showProfit)
            .putBoolean(uidKey(cleanUid, "cashier_show_expenses"), showExpenses)
            .apply()
    }

    fun savePrinterPrefs(
        uid: String,
        printerName: String,
        printerAddress: String,
        paperSize: String
    ) {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return
        userSessionPrefs(cleanUid).edit()
            .putString(uidKey(cleanUid, "printer_name"), printerName.trim())
            .putString(uidKey(cleanUid, "printer_address"), printerAddress.trim())
            .putString(uidKey(cleanUid, "printer_paper_size"), paperSize.trim().uppercase())
            .apply()
    }

    fun getPrinterPrefs(uid: String): Triple<String, String, String> {
        val cleanUid = uid.trim()
        if (cleanUid.isBlank()) return Triple("", "", "58MM")
        val prefs = userSessionPrefs(cleanUid)
        return Triple(
            prefs.getString(uidKey(cleanUid, "printer_name"), "").orEmpty(),
            prefs.getString(uidKey(cleanUid, "printer_address"), "").orEmpty(),
            prefs.getString(uidKey(cleanUid, "printer_paper_size"), "58MM").orEmpty().ifBlank { "58MM" }
        )
    }

    fun clearSession(targetUid: String? = null) {
        val activeUid = targetUid?.trim()?.takeIf { it.isNotBlank() }
            ?: devicePrefs.getString(KEY_ACTIVE_SESSION_UID, null)?.trim()

        if (!activeUid.isNullOrBlank()) {
            userSessionPrefs(activeUid).edit().clear().apply()
        }
        devicePrefs.edit().remove(KEY_ACTIVE_SESSION_UID).apply()
    }

    companion object {
        private const val DEVICE_PREFS_NAME = "lapak_kita_device_prefs"
        private const val LEGACY_GLOBAL_PREFS_NAME = "lapak_kita_license_prefs"
        private const val KEY_ACTIVE_SESSION_UID = "active_session_uid"
        private const val KEY_FALLBACK_DEVICE_ID = "fallback_device_id"
        private const val KEY_LEGACY_CACHE_CLEANED_V1 = "legacy_global_cache_cleaned_v1"

        private val LEGACY_GLOBAL_INTERNAL_FILES = listOf(
            "shop_logo.jpg",
            "poster_default_bg.jpg"
        )

        const val DEVELOPER_EMAIL = "rendykurniawan0311@gmail.com"
        const val LICENSE_APP_NAME = "Lapak Kita"

        fun sanitizeUidForStorageKey(uid: String): String {
            return uid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "unknown" }
        }

        private fun computeSessionSeal(uid: String, code: String, deviceId: String): String {
            return sha256Hex("lapak-kita-session-v2:$uid:$code:$deviceId")
        }

        private fun sha256Hex(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
