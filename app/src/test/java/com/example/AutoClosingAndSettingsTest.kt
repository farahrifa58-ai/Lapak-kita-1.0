package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.config.LicenseStorage
import com.example.data.database.LapakDatabase
import com.example.data.firebase.CloudUserSettingsDoc
import com.example.data.model.AutoClosingLeftoverMode
import com.example.data.model.DailyClosingHelper
import com.example.data.model.DailyClosingTriggerType
import com.example.data.model.LapakTransaction
import com.example.data.model.OperatingExpense
import com.example.data.model.PrinterPaperSize
import com.example.data.model.Product
import com.example.data.model.Seller
import com.example.data.model.SellerStatus
import com.example.data.model.ShopProfile
import com.example.data.model.StockLot
import com.example.data.model.TransactionItem
import com.example.data.repository.LapakRepository
import com.example.util.BluetoothThermalPrinter
import com.example.util.ReceiptPrinter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoClosingAndSettingsTest {

    @After
    fun tearDown() {
        LapakDatabase.closeAllDatabases()
    }

    private fun wibMillis(year: Int, month1Based: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month1Based - 1)
        cal.set(Calendar.DAY_OF_MONTH, day)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `first install safety - auto closing never triggers for today before scheduled WIB hour`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_autoclose_before_time")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)

        // Default schedule 22:00 WIB, RESET_ZERO
        repo.updateAutoClosingSettings(
            enabled = true,
            hour = 22,
            minute = 0,
            leftoverMode = AutoClosingLeftoverMode.RESET_ZERO
        )

        val todayMorning = wibMillis(2026, 6, 15, 8, 30)
        val todayBeforeClose = wibMillis(2026, 6, 15, 21, 59)

        val sellerId = dao.insertSeller(
            Seller(name = "Bu Ani", whatsappNumber = "081234567001", status = SellerStatus.AKTIF)
        )
        val productId = dao.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Risoles Mayo",
                category = "Gorengan",
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                adminProfitPerUnit = 1000.0
            )
        )
        dao.insertStockLot(
            StockLot(
                productId = productId,
                sellerId = sellerId,
                initialQty = 10,
                currentQty = 7,
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                depositDate = todayMorning
            )
        )
        val trxId = dao.insertTransaction(
            LapakTransaction(
                transactionNumber = "TRX-20260615-001",
                timestamp = todayMorning + 60_000L,
                totalAmount = 15000.0,
                totalItemsCount = 3,
                cashReceived = 20000.0,
                changeGiven = 5000.0,
                paymentMethod = "Tunai",
                totalAdminProfit = 3000.0,
                totalSellerShare = 12000.0
            )
        )
        dao.insertTransactionItems(
            listOf(
                TransactionItem(
                    transactionId = trxId,
                    productId = productId,
                    sellerId = sellerId,
                    productName = "Risoles Mayo",
                    sellerName = "Bu Ani",
                    unit = "pcs",
                    unitPrice = 5000.0,
                    sellerSharePerUnit = 4000.0,
                    adminProfitPerUnit = 1000.0,
                    quantity = 3,
                    subtotal = 15000.0,
                    totalSellerShare = 12000.0,
                    totalAdminProfit = 3000.0
                )
            )
        )

        val created = repo.checkAndRunAutoClosing(nowMillis = todayBeforeClose)
        assertTrue(created.isEmpty())
        assertEquals(7, dao.getProductTotalStock(productId))
        assertEquals(0, dao.getAllDailyClosingArchivesDirect().size)
    }

    @Test
    fun `auto closing at scheduled WIB time records accurate Masuk Terjual Sisa and resets leftover stock`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_autoclose_reset_zero")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)

        repo.updateAutoClosingSettings(
            enabled = true,
            hour = 22,
            minute = 0,
            leftoverMode = AutoClosingLeftoverMode.RESET_ZERO
        )

        val morning = wibMillis(2026, 6, 15, 9, 0)
        val afterClose = wibMillis(2026, 6, 15, 22, 5)

        val sellerId = dao.insertSeller(
            Seller(name = "Dapur Sari", whatsappNumber = "081234567002", status = SellerStatus.AKTIF)
        )
        val productId = dao.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Lemper Ayam",
                category = "Jajanan",
                unit = "pcs",
                sellingPrice = 4000.0,
                sellerShare = 3000.0,
                adminProfitPerUnit = 1000.0
            )
        )
        dao.insertStockLot(
            StockLot(
                productId = productId,
                sellerId = sellerId,
                initialQty = 20,
                currentQty = 8,
                unit = "pcs",
                sellingPrice = 4000.0,
                sellerShare = 3000.0,
                depositDate = morning
            )
        )
        val trxId = dao.insertTransaction(
            LapakTransaction(
                transactionNumber = "TRX-20260615-002",
                timestamp = morning + 120_000L,
                totalAmount = 48000.0,
                totalItemsCount = 12,
                cashReceived = 50000.0,
                changeGiven = 2000.0,
                paymentMethod = "Tunai",
                totalAdminProfit = 12000.0,
                totalSellerShare = 36000.0
            )
        )
        dao.insertTransactionItems(
            listOf(
                TransactionItem(
                    transactionId = trxId,
                    productId = productId,
                    sellerId = sellerId,
                    productName = "Lemper Ayam",
                    sellerName = "Dapur Sari",
                    unit = "pcs",
                    unitPrice = 4000.0,
                    sellerSharePerUnit = 3000.0,
                    adminProfitPerUnit = 1000.0,
                    quantity = 12,
                    subtotal = 48000.0,
                    totalSellerShare = 36000.0,
                    totalAdminProfit = 12000.0
                )
            )
        )
        dao.insertExpense(
            OperatingExpense(
                title = "Plastik & Kertas Minyak",
                amount = 5000.0,
                expenseDate = morning + 300_000L
            )
        )

        val created = repo.checkAndRunAutoClosing(nowMillis = afterClose)
        assertEquals(1, created.size)
        val archive = created.first()
        assertEquals("2026-06-15", archive.closingDate)
        assertEquals(DailyClosingTriggerType.AUTO, archive.triggerType)
        assertEquals(AutoClosingLeftoverMode.RESET_ZERO, archive.leftoverMode)
        assertEquals(20, archive.totalItemsIn)
        assertEquals(12, archive.totalItemsSold)
        assertEquals(8, archive.totalItemsLeftover)
        assertEquals(48000.0, archive.totalOmzet, 0.01)
        assertEquals(36000.0, archive.totalSellerShare, 0.01)
        assertEquals(12000.0, archive.totalAdminProfit, 0.01)
        assertEquals(5000.0, archive.totalOperatingExpenses, 0.01)
        assertEquals(7000.0, archive.netProfit, 0.01)

        // Stock must be zeroed because RESET_ZERO is enabled
        assertEquals(0, dao.getProductTotalStock(productId))
        // lastClosedDate updated
        assertEquals("2026-06-15", dao.getAppSettings()?.lastClosedDate)

        // Breakdown JSON preserves product & seller details
        val items = LapakRepository.decodeClosingItemsJson(archive.itemsBreakdownJson)
        assertEquals(1, items.size)
        assertEquals("Lemper Ayam", items.first().productName)
        assertEquals("Dapur Sari", items.first().sellerName)
        assertEquals(20, items.first().qtyIn)
        assertEquals(12, items.first().qtySold)
        assertEquals(8, items.first().qtyLeftover)

        // Running checkAndRunAutoClosing again is strictly idempotent (no duplicate execution)
        val secondRun = repo.checkAndRunAutoClosing(nowMillis = afterClose + 60_000L)
        assertTrue(secondRun.isEmpty())
    }

    @Test
    fun `carry over mode archives leftover count while preserving active stock for tomorrow`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_autoclose_carry_over")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)

        repo.updateAutoClosingSettings(
            enabled = true,
            hour = 21,
            minute = 30,
            leftoverMode = AutoClosingLeftoverMode.CARRY_OVER
        )

        val afternoon = wibMillis(2026, 6, 16, 14, 0)
        val afterClose = wibMillis(2026, 6, 16, 21, 35)

        val sellerId = dao.insertSeller(
            Seller(name = "Keripik Mba Yuni", whatsappNumber = "081234567003", status = SellerStatus.AKTIF)
        )
        val productId = dao.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Keripik Pisang Coklat",
                category = "Snack",
                unit = "bks",
                sellingPrice = 10000.0,
                sellerShare = 8000.0,
                adminProfitPerUnit = 2000.0
            )
        )
        dao.insertStockLot(
            StockLot(
                productId = productId,
                sellerId = sellerId,
                initialQty = 15,
                currentQty = 9,
                unit = "bks",
                sellingPrice = 10000.0,
                sellerShare = 8000.0,
                depositDate = afternoon
            )
        )

        val created = repo.checkAndRunAutoClosing(nowMillis = afterClose)
        assertEquals(1, created.size)
        assertEquals(AutoClosingLeftoverMode.CARRY_OVER, created.first().leftoverMode)
        assertEquals(9, created.first().totalItemsLeftover)
        // Active stock must remain intact (9 bks) for tomorrow!
        assertEquals(9, dao.getProductTotalStock(productId))
    }

    @Test
    fun `anti-fail catch-up closes past unclosed date without wiping today live stock`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_autoclose_catchup")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)

        repo.updateAutoClosingSettings(
            enabled = true,
            hour = 22,
            minute = 0,
            leftoverMode = AutoClosingLeftoverMode.RESET_ZERO
        )

        val yesterdayMorning = wibMillis(2026, 6, 17, 10, 0)
        val todayMorning = wibMillis(2026, 6, 18, 8, 0)
        val todayCheckTime = wibMillis(2026, 6, 18, 9, 15)

        val sellerId = dao.insertSeller(
            Seller(name = "Kue Pak Budi", whatsappNumber = "081234567004", status = SellerStatus.AKTIF)
        )
        val productId = dao.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Pastel Sayur",
                category = "Gorengan",
                unit = "pcs",
                sellingPrice = 3000.0,
                sellerShare = 2500.0,
                adminProfitPerUnit = 500.0
            )
        )
        // Yesterday's sale (phone was turned off before 22:00 yesterday)
        val yTrxId = dao.insertTransaction(
            LapakTransaction(
                transactionNumber = "TRX-20260617-010",
                timestamp = yesterdayMorning,
                totalAmount = 30000.0,
                totalItemsCount = 10,
                cashReceived = 30000.0,
                changeGiven = 0.0,
                paymentMethod = "Tunai",
                totalAdminProfit = 5000.0,
                totalSellerShare = 25000.0
            )
        )
        dao.insertTransactionItems(
            listOf(
                TransactionItem(
                    transactionId = yTrxId,
                    productId = productId,
                    sellerId = sellerId,
                    productName = "Pastel Sayur",
                    sellerName = "Kue Pak Budi",
                    unit = "pcs",
                    unitPrice = 3000.0,
                    sellerSharePerUnit = 2500.0,
                    adminProfitPerUnit = 500.0,
                    quantity = 10,
                    subtotal = 30000.0,
                    totalSellerShare = 25000.0,
                    totalAdminProfit = 5000.0
                )
            )
        )
        // Fresh stock deposited today morning
        dao.insertStockLot(
            StockLot(
                productId = productId,
                sellerId = sellerId,
                initialQty = 25,
                currentQty = 25,
                unit = "pcs",
                sellingPrice = 3000.0,
                sellerShare = 2500.0,
                depositDate = todayMorning
            )
        )

        val created = repo.checkAndRunAutoClosing(nowMillis = todayCheckTime)
        assertEquals(1, created.size)
        assertEquals("2026-06-17", created.first().closingDate)
        assertEquals(30000.0, created.first().totalOmzet, 0.01)
        // Today's fresh 25 pcs stock must remain completely untouched
        assertEquals(25, dao.getProductTotalStock(productId))
    }

    @Test
    fun `manual closing prevents duplicate manual or auto closing on the same date`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_autoclose_manual_idempotent")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)

        repo.updateAutoClosingSettings(
            enabled = true,
            hour = 22,
            minute = 0,
            leftoverMode = AutoClosingLeftoverMode.RESET_ZERO
        )

        val todayStr = "2026-06-19"
        val morning = wibMillis(2026, 6, 19, 10, 0)
        val lateNight = wibMillis(2026, 6, 19, 22, 30)

        val sellerId = dao.insertSeller(
            Seller(name = "Donat Ibu Nia", whatsappNumber = "081234567005", status = SellerStatus.AKTIF)
        )
        val productId = dao.insertProduct(
            Product(
                sellerId = sellerId,
                name = "Donat Kentang",
                category = "Roti",
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                adminProfitPerUnit = 1000.0
            )
        )
        dao.insertStockLot(
            StockLot(
                productId = productId,
                sellerId = sellerId,
                initialQty = 10,
                currentQty = 4,
                unit = "pcs",
                sellingPrice = 5000.0,
                sellerShare = 4000.0,
                depositDate = morning
            )
        )

        val firstManual = repo.performDailyClosing(
            targetDateWib = todayStr,
            triggerType = DailyClosingTriggerType.MANUAL,
            nowMillis = morning + 3_600_000L
        )
        assertTrue(firstManual.isSuccess)
        assertEquals(DailyClosingTriggerType.MANUAL, firstManual.getOrNull()?.triggerType)
        assertEquals(0, dao.getProductTotalStock(productId))

        // Second manual closing on same date fails gracefully
        val secondManual = repo.performDailyClosing(
            targetDateWib = todayStr,
            triggerType = DailyClosingTriggerType.MANUAL,
            nowMillis = morning + 7_200_000L
        )
        assertTrue(secondManual.isFailure)

        // Auto closing after 22:00 on the same date skips cleanly
        val autoAfterManual = repo.checkAndRunAutoClosing(nowMillis = lateNight)
        assertTrue(autoAfterManual.isEmpty())
    }

    @Test
    fun `cashier customization, cloud settings sync, printer prefs, and ESC-POS formatting work cleanly`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = LapakDatabase.getDatabaseForUser(context, "uid_settings_and_print")
        val dao = db.lapakDao()
        val repo = LapakRepository(dao)
        val storage = LicenseStorage(context)

        // 1. Cashier customization defaults to hidden (false)
        val initial = dao.getAppSettings()
        assertFalse(initial?.cashierShowOmzet ?: false)
        assertFalse(initial?.cashierShowProfit ?: false)
        assertFalse(initial?.cashierShowExpenses ?: false)

        // 2. Owner toggles cashier customization
        val updatedCashier = repo.updateCashierVisibilitySettings(
            showOmzet = true,
            showProfit = false,
            showExpenses = true
        )
        assertTrue(updatedCashier.cashierShowOmzet)
        assertFalse(updatedCashier.cashierShowProfit)
        assertTrue(updatedCashier.cashierShowExpenses)

        // 3. Cloud user settings merge into companion device Room state
        val merged = repo.applyCloudUserSettings(
            CloudUserSettingsDoc(
                autoClosingEnabled = true,
                autoClosingHour = 21,
                autoClosingMinute = 45,
                autoClosingLeftoverMode = AutoClosingLeftoverMode.CARRY_OVER,
                lastClosedDate = "2026-06-18",
                cashierShowOmzet = true,
                cashierShowProfit = true,
                cashierShowExpenses = false
            )
        )
        assertEquals(21, merged.autoClosingHour)
        assertEquals(45, merged.autoClosingMinute)
        assertEquals(AutoClosingLeftoverMode.CARRY_OVER, merged.autoClosingLeftoverMode)
        assertEquals("2026-06-18", merged.lastClosedDate)
        assertTrue(merged.cashierShowOmzet)
        assertTrue(merged.cashierShowProfit)
        assertFalse(merged.cashierShowExpenses)

        // 4. Printer settings in Room + UID-prefixed LicenseStorage
        val updatedPrinter = repo.updatePrinterSettings(
            printerName = "RPP02N Thermal",
            printerAddress = "00:11:22:33:44:55",
            paperSize = PrinterPaperSize.MM_80
        )
        assertEquals("RPP02N Thermal", updatedPrinter.printerName)
        assertEquals("00:11:22:33:44:55", updatedPrinter.printerAddress)
        assertEquals(PrinterPaperSize.MM_80, updatedPrinter.printerPaperSize)

        storage.savePrinterPrefs("uid_settings_and_print", "RPP02N Thermal", "00:11:22:33:44:55", PrinterPaperSize.MM_80)
        storage.saveAutoClosingPrefs("uid_settings_and_print", true, 21, 45, AutoClosingLeftoverMode.CARRY_OVER, "2026-06-18")
        val (savedName, savedAddr, savedPaper) = storage.getPrinterPrefs("uid_settings_and_print")
        assertEquals("RPP02N Thermal", savedName)
        assertEquals("00:11:22:33:44:55", savedAddr)
        assertEquals(PrinterPaperSize.MM_80, savedPaper)
        assertEquals("2026-06-18", storage.getAutoClosingLastClosedDate("uid_settings_and_print"))

        // 5. ESC/POS formatting for 58mm vs 80mm + HTML test print
        val trx = LapakTransaction(
            id = 1,
            transactionNumber = "TRX-TEST-001",
            timestamp = wibMillis(2026, 6, 19, 10, 15),
            totalAmount = 15000.0,
            totalItemsCount = 3,
            cashReceived = 20000.0,
            changeGiven = 5000.0,
            paymentMethod = "Tunai",
            totalAdminProfit = 3000.0,
            totalSellerShare = 12000.0
        )
        val trxItem = TransactionItem(
            transactionId = 1,
            productId = 1,
            sellerId = 1,
            productName = "Risoles Mayo Spesial Keju Lumer Ukuran Jumbo",
            sellerName = "Bu Ani",
            unit = "pcs",
            unitPrice = 5000.0,
            sellerSharePerUnit = 4000.0,
            adminProfitPerUnit = 1000.0,
            quantity = 3,
            subtotal = 15000.0,
            totalSellerShare = 12000.0,
            totalAdminProfit = 3000.0
        )
        val profile = ShopProfile(shopName = "Lapak Kue Pagi", address = "Jl. Merdeka 10", whatsappNumber = "08123456789")
        val receipt58 = BluetoothThermalPrinter.formatCustomerReceiptEscPos(trx, listOf(trxItem), profile, PrinterPaperSize.MM_58)
        val receipt80 = BluetoothThermalPrinter.formatCustomerReceiptEscPos(trx, listOf(trxItem), profile, PrinterPaperSize.MM_80)
        assertTrue(receipt58.contains("LAPAK KUE PAGI"))
        assertTrue(receipt58.contains("=".repeat(32)))
        assertTrue(receipt80.contains("=".repeat(48)))
        assertTrue(BluetoothThermalPrinter.buildEscPosBytes(receipt58).size > receipt58.length)

        val testHtmlA4 = ReceiptPrinter.buildTestPrintHtml(profile, "Printer Kantor", PrinterPaperSize.A4)
        assertNotNull(testHtmlA4)
        assertTrue(testHtmlA4.contains("A4"))
        assertEquals("2026-06-19", DailyClosingHelper.dateStringFromMillis(wibMillis(2026, 6, 19, 23, 59)))
    }
}
