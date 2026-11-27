package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LapakDatabase
import com.example.data.firebase.UserProfileDoc
import com.example.data.model.Product
import com.example.data.model.Seller
import com.example.data.model.SellerStatus
import com.example.data.model.StockMovementType
import com.example.data.repository.LapakRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdminStockAndRoleTest {

    @After
    fun tearDown() {
        LapakDatabase.closeAllDatabases()
    }

    @Test
    fun `owner device role detection grants full access to device 1 and kasir mode to device 2`() {
        val profile = UserProfileDoc(
            userId = "uid_owner_test",
            email = "owner@lapakkita.id",
            businessName = "Lapak Kue Pagi",
            licenseCode = "LPK-TEST-001",
            registeredDeviceId = "DEV-OWNER-001",
            registeredDeviceIds = listOf("DEV-OWNER-001", "DEV-KASIR-002"),
            active = true
        )

        assertTrue(profile.isOwnerDevice("DEV-OWNER-001"))
        assertFalse(profile.isOwnerDevice("DEV-KASIR-002"))
        assertFalse(profile.isOwnerDevice("DEV-OTHER-999"))

        // Legacy or freshly created profile without registeredDeviceIds defaults to owner
        val emptyDevicesProfile = UserProfileDoc(
            userId = "uid_empty",
            email = "empty@lapakkita.id",
            businessName = "Lapak Baru",
            licenseCode = "LPK-TEST-002",
            registeredDeviceId = "",
            registeredDeviceIds = emptyList(),
            active = true
        )
        assertTrue(emptyDevicesProfile.isOwnerDevice("DEV-ANY-001"))
    }

    @Test
    fun `freshly opened user database starts completely empty without auto seeded dummy data`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_brand_new_account_999")
        val dao = db.lapakDao()

        assertEquals(0, dao.getAllSellersDirect().size)
        assertEquals(0, dao.getAllProductsDirect().size)
        assertEquals(0, dao.getAllTransactionsDirect().size)
        assertEquals(0, dao.getAllStockLotsDirect().size)
        assertEquals(0, dao.getAllOperatingExpensesDirect().size)
    }

    @Test
    fun `admin adjustProductStock can increase, FIFO decrease, set to zero, and rejects negative`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_stock_edit_test_001")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)

        val sellerId = dao.insertSeller(
            Seller(
                name = "Dapur Ibu Sari",
                whatsappNumber = "081234567890",
                status = SellerStatus.AKTIF,
                vendorCode = "LPK-SARI",
                writeToken = "tok_sari"
            )
        )
        val productId = dao.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Risoles Mayo Spesial Keju Lumer Ukuran Jumbo",
                category = "Gorengan",
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                adminProfitPerUnit = 1000.0
            )
        )

        // Initial deposit of 10 pcs
        repo.depositStock(
            sellerId = sellerId,
            productId = productId,
            quantity = 10,
            unit = "pcs",
            sellingPrice = 5000.0,
            sellerShare = 4000.0,
            notes = "Titipan pagi"
        )
        assertEquals(10, dao.getAllStockLotsDirect().filter { it.productId == productId }.sumOf { it.currentQty })

        // 1. Admin increases stock from 10 -> 18
        val incResult = repo.adjustProductStock(
            sellerId = sellerId,
            productId = productId,
            targetStock = 18,
            reason = "Penyetor salah input, tambah 8 pcs"
        )
        assertTrue(incResult.isSuccess)
        assertEquals(18, incResult.getOrNull())
        assertEquals(18, dao.getAllStockLotsDirect().filter { it.productId == productId }.sumOf { it.currentQty })

        // 2. Admin decreases stock from 18 -> 5 (FIFO across lots)
        val decResult = repo.adjustProductStock(
            sellerId = sellerId,
            productId = productId,
            targetStock = 5,
            reason = "Salah ketik di web portal"
        )
        assertTrue(decResult.isSuccess)
        assertEquals(5, decResult.getOrNull())
        assertEquals(5, dao.getAllStockLotsDirect().filter { it.productId == productId }.sumOf { it.currentQty })

        // 3. Admin sets stock to 0
        val zeroResult = repo.adjustProductStock(
            sellerId = sellerId,
            productId = productId,
            targetStock = 0,
            reason = "Barang ditarik penyetor"
        )
        assertTrue(zeroResult.isSuccess)
        assertEquals(0, zeroResult.getOrNull())
        assertEquals(0, dao.getAllStockLotsDirect().filter { it.productId == productId }.sumOf { it.currentQty })

        // 4. Negative stock must be rejected
        val negResult = repo.adjustProductStock(
            sellerId = sellerId,
            productId = productId,
            targetStock = -3,
            reason = "Invalid"
        )
        assertTrue(negResult.isFailure)

        // 5. Verify KOREKSI movements were recorded
        val koreksiLogs = dao.getAllStockMovementsDirect().filter { it.type == StockMovementType.KOREKSI }
        assertEquals(3, koreksiLogs.size)
    }
}
