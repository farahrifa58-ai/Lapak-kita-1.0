package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.LapakDatabase
import com.example.data.firebase.VendorSubmissionRemote
import com.example.data.firebase.WebEditedProductPayload
import com.example.data.firebase.WebStockAdditionPayload
import com.example.data.model.AdminFeeType
import com.example.data.model.AppSettings
import com.example.data.model.Product
import com.example.data.model.Seller
import com.example.data.model.SellerStatus
import com.example.data.repository.LapakRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VendorPricingSyncTest {

    @After
    fun tearDown() {
        LapakDatabase.closeAllDatabases()
    }

    @Test
    fun `updateProduct immediately reflects custom price share and commission in direct sync snapshot and stock lots`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_pricing_sync_test")
        val repository = LapakRepository(db.lapakDao())

        repository.updateAppSettings(
            AppSettings(
                adminFeeType = AdminFeeType.FIXED,
                adminFeeValue = 1000.0
            )
        )

        val sellerId = repository.addSeller(
            Seller(
                name = "Dapur Ibu Ani",
                whatsappNumber = "081234567890",
                status = SellerStatus.AKTIF,
                vendorCode = "LPK-ANI123",
                writeToken = "tokenAni1234567890"
            )
        )

        val productId = repository.addProduct(
            Product(
                sellerId = sellerId,
                name = "Risoles Mayo",
                category = "Makanan",
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                adminProfitPerUnit = 1000.0
            )
        )
        repository.depositStock(
            sellerId = sellerId,
            productId = productId,
            quantity = 12,
            unit = "pcs",
            sellingPrice = 5000.0,
            sellerShare = 4000.0,
            notes = "Stok pagi"
        )

        // Admin edits selling price, net share, and custom per-item commission (Rp 1.500 instead of global Rp 1.000)
        val original = db.lapakDao().getProductById(productId)!!
        repository.updateProduct(
            original.copy(
                sellingPrice = 6000.0,
                sellerShare = 4500.0,
                adminProfitPerUnit = 1500.0
            )
        )

        val snapshot = repository.getSellerSyncSnapshotDirect(sellerId)
        assertNotNull(snapshot)
        val syncedProd = snapshot!!.sellerProducts.first { it.product.id == productId }
        assertEquals(6000.0, syncedProd.product.sellingPrice, 0.01)
        assertEquals(4500.0, syncedProd.product.sellerShare, 0.01)
        assertEquals(1500.0, syncedProd.product.adminProfitPerUnit, 0.01)
        assertEquals(12, syncedProd.currentStock)

        // Active StockLot should also be aligned with the updated Admin pricing
        assertTrue(syncedProd.activeLots.all { it.sellingPrice == 6000.0 && it.sellerShare == 4500.0 })
    }

    @Test
    fun `web vendor stock-only submission never overwrites admin price or custom per-item commission`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_stock_only_test")
        val repository = LapakRepository(db.lapakDao())

        repository.updateAppSettings(
            AppSettings(
                adminFeeType = AdminFeeType.FIXED,
                adminFeeValue = 1000.0
            )
        )

        val sellerId = repository.addSeller(
            Seller(
                name = "Dapur Ibu Budi",
                whatsappNumber = "081299998888",
                status = SellerStatus.AKTIF,
                vendorCode = "LPK-BUDI99",
                writeToken = "tokenBudi123456789"
            )
        )

        // Admin configures custom per-item commission Rp 1.500 (global is Rp 1.000)
        val productId = repository.addProduct(
            Product(
                sellerId = sellerId,
                name = "Lontong Isi",
                category = "Makanan",
                unit = "pcs",
                sellingPrice = 5500.0,
                sellerShare = 4000.0,
                adminProfitPerUnit = 1500.0
            )
        )

        val stockOnlySubmission = VendorSubmissionRemote(
            submissionId = "sub_stock_only_01",
            ownerUid = "uid_stock_only_test",
            vendorCode = "LPK-BUDI99",
            status = "PENDING",
            newProducts = emptyList(),
            editedProducts = emptyList(),
            stockAdditions = listOf(WebStockAdditionPayload(localProductId = productId, addedQty = 15)),
            note = "Kiriman pagi",
            createdAtMillis = System.currentTimeMillis()
        )

        val result = repository.applyWebVendorSubmission(stockOnlySubmission)
        assertTrue(result.isSuccess)

        val snapshot = repository.getSellerSyncSnapshotByCodeDirect("LPK-BUDI99")
        assertNotNull(snapshot)
        val item = snapshot!!.sellerProducts.first { it.product.id == productId }
        assertEquals(15, item.currentStock)
        assertEquals(5500.0, item.product.sellingPrice, 0.01)
        assertEquals(4000.0, item.product.sellerShare, 0.01)
        assertEquals(1500.0, item.product.adminProfitPerUnit, 0.01)
        assertEquals(5500.0, item.activeLots.first().sellingPrice, 0.01)
        assertEquals(4000.0, item.activeLots.first().sellerShare, 0.01)

        // Even if vendor edits only product name (same price), custom per-item commission stays intact
        val nameOnlyEdit = VendorSubmissionRemote(
            submissionId = "sub_name_edit_02",
            ownerUid = "uid_stock_only_test",
            vendorCode = "LPK-BUDI99",
            status = "PENDING",
            newProducts = emptyList(),
            editedProducts = listOf(
                WebEditedProductPayload(
                    localProductId = productId,
                    name = "Lontong Isi Ayam",
                    category = "Makanan",
                    unit = "pcs",
                    sellingPrice = 5500.0
                )
            ),
            stockAdditions = emptyList(),
            note = "",
            createdAtMillis = System.currentTimeMillis()
        )
        val editRes = repository.applyWebVendorSubmission(nameOnlyEdit)
        assertTrue(editRes.isSuccess)

        val afterEdit = db.lapakDao().getProductById(productId)!!
        assertEquals("Lontong Isi Ayam", afterEdit.name)
        assertEquals(5500.0, afterEdit.sellingPrice, 0.01)
        assertEquals(4000.0, afterEdit.sellerShare, 0.01)
        assertEquals(1500.0, afterEdit.adminProfitPerUnit, 0.01)
    }
}
