package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.LicenseStorage
import com.example.data.database.LapakDatabase
import com.example.data.model.OperatingExpense
import com.example.data.model.Product
import com.example.data.model.Seller
import com.example.data.model.SellerStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountIsolationTest {

    @After
    fun tearDown() {
        LapakDatabase.closeAllDatabases()
    }

    @Test
    fun `room databases are strictly isolated per uid`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uidA = "uid_account_A_123"
        val uidB = "uid_account_B_456"

        assertEquals("lapakkita_uid_account_A_123_db", LapakDatabase.databaseNameForUser(uidA))
        assertEquals("lapakkita_uid_account_B_456_db", LapakDatabase.databaseNameForUser(uidB))

        val dbA = LapakDatabase.getDatabaseForUser(context, uidA)
        val daoA = dbA.lapakDao()

        val sellerId = daoA.insertSeller(
            Seller(
                name = "Dapur Akun A",
                whatsappNumber = "081200000001",
                status = SellerStatus.AKTIF,
                vendorCode = "LPK-AAAA",
                writeToken = "tokenA"
            )
        )
        daoA.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Risoles Akun A",
                category = "Cemilan",
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                adminProfitPerUnit = 1000.0
            )
        )
        daoA.insertExpense(
            OperatingExpense(
                title = "Plastik Akun A",
                amount = 10000.0,
                notes = "Belanja operasional A"
            )
        )

        assertEquals(1, daoA.getAllSellersDirect().size)
        assertEquals(1, daoA.getAllProductsDirect().size)
        assertEquals(1, daoA.getAllOperatingExpensesDirect().size)

        // Switching to Account B opens an isolated DB with zero data from Account A
        LapakDatabase.closeUserDatabase(uidA)

        val dbB = LapakDatabase.getDatabaseForUser(context, uidB)
        val daoB = dbB.lapakDao()

        assertEquals(0, daoB.getAllSellersDirect().size)
        assertEquals(0, daoB.getAllProductsDirect().size)
        assertEquals(0, daoB.getAllOperatingExpensesDirect().size)
    }

    @Test
    fun `license storage isolates sessions per uid and clears without cross contamination`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LicenseStorage(context)

        val uidA = "google_uid_A"
        val uidB = "google_uid_B"

        storage.saveOfflineSession(
            uid = uidA,
            email = "akuna@example.com",
            businessName = "Lapak A",
            licenseCode = "LPK-1111",
            connectedDevicesCount = 2
        )
        storage.saveOfflineSession(
            uid = uidB,
            email = "akunb@example.com",
            businessName = "Lapak B",
            licenseCode = "LPK-2222",
            connectedDevicesCount = 1
        )

        val sessionA = storage.getOfflineSession(uidA)
        val sessionB = storage.getOfflineSession(uidB)

        assertNotNull(sessionA)
        assertNotNull(sessionB)
        assertEquals("Lapak A", sessionA?.businessName)
        assertEquals("LPK-1111", sessionA?.licenseCode)
        assertEquals(2, storage.getCachedConnectedDevicesCount(uidA))

        assertEquals("Lapak B", sessionB?.businessName)
        assertEquals("LPK-2222", sessionB?.licenseCode)
        assertEquals(1, storage.getCachedConnectedDevicesCount(uidB))

        // Clearing session A must not erase session B
        storage.clearSession(uidA)
        assertNull(storage.getOfflineSession(uidA))
        assertNotNull(storage.getOfflineSession(uidB))
        assertEquals("LPK-2222", storage.getOfflineSession(uidB)?.licenseCode)
    }

    @Test
    fun `legacy global cache cleanup removes old global artifacts while preserving fallback device id`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Seed legacy global prefs, legacy global DB file, and legacy global images
        val legacyPrefs = context.getSharedPreferences("lapak_kita_license_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit()
            .putString("fallback_device_id", "DEV-LEGACY12345678")
            .putString("session_uid", "old_leaked_uid")
            .putString("license_code", "OLD-CODE")
            .commit()

        val legacyDbFile = context.getDatabasePath(LapakDatabase.LEGACY_GLOBAL_DB_NAME)
        legacyDbFile.parentFile?.mkdirs()
        legacyDbFile.writeText("legacy sqlite bytes")

        val legacyLogo = File(context.filesDir, "shop_logo.jpg").apply { writeText("legacy logo") }
        val legacyBg = File(context.filesDir, "poster_default_bg.jpg").apply { writeText("legacy bg") }

        assertTrue(legacyDbFile.exists())
        assertTrue(legacyLogo.exists())
        assertTrue(legacyBg.exists())

        val storage = LicenseStorage(context)
        storage.cleanupLegacyGlobalCacheOnce()

        // Legacy artifacts should be gone
        assertFalse(legacyDbFile.exists())
        assertFalse(legacyLogo.exists())
        assertFalse(legacyBg.exists())

        val afterLegacyPrefs = context.getSharedPreferences("lapak_kita_license_prefs", Context.MODE_PRIVATE)
        assertNull(afterLegacyPrefs.getString("session_uid", null))
        assertNull(afterLegacyPrefs.getString("license_code", null))

        // Fallback device ID must be preserved in device prefs
        val devicePrefs = context.getSharedPreferences("lapak_kita_device_prefs", Context.MODE_PRIVATE)
        assertEquals("DEV-LEGACY12345678", devicePrefs.getString("fallback_device_id", null))
        assertTrue(devicePrefs.getBoolean("legacy_global_cache_cleaned_v1", false))
    }
}
