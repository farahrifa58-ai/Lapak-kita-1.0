package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.LapakDatabase
import com.example.data.model.*
import com.example.data.repository.*
import com.example.i18n.AppStrings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

enum class AppScreen {
    DASHBOARD,
    SALES,
    CASHIER,
    HISTORY,
    SELLERS,
    SETTINGS,
    ABOUT
}

data class SellerPayoutReceiptState(
    val payment: SellerPayment,
    val seller: Seller,
    val remainingBalance: Double,
    val soldItems: List<SoldItemSummary> = emptyList()
)

class LapakViewModel(application: Application) : AndroidViewModel(application) {

    private val db = LapakDatabase.getDatabase(application, viewModelScope)
    private val repository = LapakRepository(db.lapakDao())

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Settings & Profile
    val shopProfile: StateFlow<ShopProfile> = repository.shopProfile.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ShopProfile()
    )

    val appSettings: StateFlow<AppSettings> = repository.appSettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppSettings()
    )

    val currentLanguage: StateFlow<String> = appSettings.map { it.language }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "id"
    )

    val isDarkTheme: StateFlow<Boolean> = appSettings.map { it.themeMode == "DARK" }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun toggleTheme(dark: Boolean) {
        viewModelScope.launch {
            val curr = appSettings.value
            repository.updateAppSettings(curr.copy(themeMode = if (dark) "DARK" else "LIGHT"))
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            val curr = appSettings.value
            repository.updateAppSettings(curr.copy(language = lang))
        }
    }

    fun updateProfile(name: String, owner: String, wa: String, address: String, footer: String) {
        viewModelScope.launch {
            val curr = shopProfile.value
            repository.updateShopProfile(
                curr.copy(
                    shopName = name,
                    ownerName = owner,
                    whatsappNumber = wa,
                    address = address,
                    footerMessage = footer
                )
            )
        }
    }

    // Dashboard Timeframe
    private val _timeframe = MutableStateFlow("TODAY")
    val timeframe: StateFlow<String> = _timeframe.asStateFlow()

    fun setTimeframe(tf: String) {
        _timeframe.value = tf
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dashboardSummary: StateFlow<DashboardSummary> = _timeframe.flatMapLatest { tf ->
        repository.getDashboardSummary(tf)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        DashboardSummary(
            totalOmzet = 0.0,
            totalSellerShare = 0.0,
            grossAdminProfit = 0.0,
            operatingExpenses = 0.0,
            netAdminProfit = 0.0,
            totalItemsSold = 0,
            activeSellersCount = 0,
            leaveSellersCount = 0,
            availableStockCount = 0,
            totalUnpaidSellerPayables = 0.0,
            topSellingProducts = emptyList(),
            lowStockProducts = emptyList(),
            dailyTrend = emptyList()
        )
    )

    // Products & Stock
    val productsWithDetails: StateFlow<List<ProductDetailItem>> = repository.productsWithDetails.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Sellers
    val sellersWithStats: StateFlow<List<SellerDetailItem>> = repository.sellersWithStats.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val rawSellers: StateFlow<List<Seller>> = repository.allSellers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun addSeller(name: String, wa: String, address: String, notes: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addSeller(
                Seller(
                    name = name,
                    whatsappNumber = wa,
                    address = address,
                    notes = notes,
                    status = SellerStatus.AKTIF
                )
            )
            onDone()
        }
    }

    fun updateSeller(seller: Seller, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateSeller(seller)
            onDone()
        }
    }

    fun deleteSeller(seller: Seller, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSeller(seller)
            onDone()
        }
    }

    fun updateProduct(product: Product, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateProduct(product)
            onDone()
        }
    }

    fun deleteProduct(product: Product, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            onDone()
        }
    }

    fun setSellerLeaveStatus(
        sellerId: Long,
        newStatus: SellerStatus,
        startDate: Long?,
        endDate: Long?,
        reason: String?
    ) {
        viewModelScope.launch {
            repository.updateSellerStatus(sellerId, newStatus, startDate, endDate, reason)
        }
    }

    fun reactivateSeller(sellerId: Long) {
        viewModelScope.launch {
            repository.updateSellerStatus(sellerId, SellerStatus.AKTIF, null, null, "Diaktifkan kembali oleh admin")
        }
    }

    // Add Product
    fun addProduct(
        sellerId: Long,
        name: String,
        category: String,
        unit: String,
        sellingPrice: Double,
        sellerShare: Double,
        initialStock: Int,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val prodId = repository.addProduct(
                Product(
                    sellerId = sellerId,
                    name = name,
                    category = category,
                    unit = unit,
                    sellingPrice = sellingPrice,
                    sellerShare = sellerShare,
                    adminProfitPerUnit = sellingPrice - sellerShare
                )
            )
            if (initialStock > 0) {
                repository.depositStock(
                    sellerId = sellerId,
                    productId = prodId,
                    quantity = initialStock,
                    unit = unit,
                    sellingPrice = sellingPrice,
                    sellerShare = sellerShare,
                    notes = "Setoran awal produk"
                )
            }
            onDone()
        }
    }

    // Deposit Stock (Consignment)
    fun depositStock(
        sellerId: Long,
        productId: Long,
        quantity: Int,
        unit: String,
        sellingPrice: Double,
        sellerShare: Double,
        notes: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.depositStock(
                sellerId = sellerId,
                productId = productId,
                quantity = quantity,
                unit = unit,
                sellingPrice = sellingPrice,
                sellerShare = sellerShare,
                notes = notes
            )
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Gagal menambah setoran stok")
            }
        }
    }

    // Cashier (Keranjang & Pembayaran)
    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _cashReceivedText = MutableStateFlow("")
    val cashReceivedText: StateFlow<String> = _cashReceivedText.asStateFlow()

    private val _paymentMethod = MutableStateFlow("Tunai")
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    // Last completed transaction for receipt popup
    private val _lastCompletedTransaction = MutableStateFlow<LapakTransaction?>(null)
    val lastCompletedTransaction: StateFlow<LapakTransaction?> = _lastCompletedTransaction.asStateFlow()

    fun addToCart(product: Product, seller: Seller, stockLot: StockLot, quantity: Int = 1) {
        val currentList = _cart.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.product.id == product.id && it.seller.id == seller.id && it.stockLot.id == stockLot.id
        }

        if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            if (existing.quantity + quantity <= stockLot.currentQty) {
                currentList[existingIndex] = existing.copy(quantity = existing.quantity + quantity)
            }
        } else {
            if (quantity <= stockLot.currentQty) {
                currentList.add(CartItem(product, seller, stockLot, quantity))
            }
        }
        _cart.value = currentList
    }

    fun updateCartQty(item: CartItem, newQty: Int) {
        if (newQty <= 0) {
            removeFromCart(item)
            return
        }
        if (newQty > item.stockLot.currentQty) {
            Toast.makeText(getApplication(), "Stok maksimal: ${item.stockLot.currentQty}", Toast.LENGTH_SHORT).show()
            return
        }
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst {
            it.product.id == item.product.id && it.seller.id == item.seller.id && it.stockLot.id == item.stockLot.id
        }
        if (index >= 0) {
            currentList[index] = item.copy(quantity = newQty)
            _cart.value = currentList
        }
    }

    fun removeFromCart(item: CartItem) {
        _cart.value = _cart.value.filterNot {
            it.product.id == item.product.id && it.seller.id == item.seller.id && it.stockLot.id == item.stockLot.id
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
        _cashReceivedText.value = ""
    }

    fun setCashReceived(text: String) {
        _cashReceivedText.value = text
    }

    fun setPaymentMethod(method: String) {
        _paymentMethod.value = method
    }

    fun clearLastCompletedTransaction() {
        _lastCompletedTransaction.value = null
    }

    fun checkout(
        onSuccess: (LapakTransaction) -> Unit,
        onError: (String) -> Unit
    ) {
        val items = _cart.value
        if (items.isEmpty()) {
            onError("Keranjang masih kosong!")
            return
        }

        val totalAmount = items.sumOf { it.subtotal }
        val method = _paymentMethod.value
        val cashInput = _cashReceivedText.value.toDoubleOrNull() ?: totalAmount

        if (method == "Tunai" && cashInput < totalAmount) {
            onError("Uang yang diterima kurang dari total pembayaran!")
            return
        }

        viewModelScope.launch {
            val result = repository.checkout(
                cartItems = items,
                cashReceived = if (method == "Tunai") cashInput else totalAmount,
                paymentMethod = method
            )

            if (result.isSuccess) {
                val trx = result.getOrThrow()
                _lastCompletedTransaction.value = trx
                clearCart()
                onSuccess(trx)
            } else {
                onError(result.exceptionOrNull()?.message ?: "Gagal memproses transaksi")
            }
        }
    }

    // Transactions History
    val allTransactions: StateFlow<List<LapakTransaction>> = repository.allTransactions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun getTransactionItems(trxId: Long): Flow<List<TransactionItem>> = repository.getItemsForTransaction(trxId)

    fun cancelTransaction(trxId: Long, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val res = repository.cancelTransaction(trxId, reason)
            if (res.isSuccess) {
                onSuccess()
            } else {
                onError(res.exceptionOrNull()?.message ?: "Gagal membatalkan transaksi")
            }
        }
    }

    // Seller Payout Receipt State
    private val _lastSellerPaymentReceipt = MutableStateFlow<SellerPayoutReceiptState?>(null)
    val lastSellerPaymentReceipt: StateFlow<SellerPayoutReceiptState?> = _lastSellerPaymentReceipt.asStateFlow()

    suspend fun getSoldItemsSummary(sellerId: Long): List<SoldItemSummary> {
        val items = repository.getSoldItemsForSeller(sellerId)
        return items.groupBy { it.productId to it.productName }
            .map { (key, group) ->
                SoldItemSummary(
                    productName = key.second,
                    unit = group.first().unit,
                    quantity = group.sumOf { it.quantity },
                    sellerSharePerUnit = group.first().sellerSharePerUnit,
                    totalSellerShare = group.sumOf { it.totalSellerShare }
                )
            }
            .sortedByDescending { it.quantity }
    }

    fun showSellerPaymentReceipt(payment: SellerPayment, seller: Seller, remainingBalance: Double, soldItems: List<SoldItemSummary> = emptyList()) {
        _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(payment, seller, remainingBalance, soldItems)
    }

    fun clearSellerPaymentReceipt() {
        _lastSellerPaymentReceipt.value = null
    }

    fun showLatestPaymentReceipt(sellerDetail: SellerDetailItem) {
        viewModelScope.launch {
            val payments = repository.allPayments.first()
            val latest = payments.filter { it.sellerId == sellerDetail.seller.id }.maxByOrNull { it.paymentDate }
            val soldItems = getSoldItemsSummary(sellerDetail.seller.id)
            if (latest != null) {
                _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(latest, sellerDetail.seller, sellerDetail.remainingBalance, soldItems)
            } else {
                val statement = SellerPayment(
                    id = 0,
                    sellerId = sellerDetail.seller.id,
                    paymentDate = System.currentTimeMillis(),
                    amount = sellerDetail.totalPaid,
                    notes = "Rekapitulasi Hak Penyetor",
                    paymentMethod = "Tunai"
                )
                _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(statement, sellerDetail.seller, sellerDetail.remainingBalance, soldItems)
            }
        }
    }

    // Seller Payout Settlements
    fun recordSellerPayment(
        sellerId: Long,
        amount: Double,
        notes: String,
        method: String,
        onDone: (SellerPayment) -> Unit
    ) {
        viewModelScope.launch {
            val payment = SellerPayment(
                sellerId = sellerId,
                amount = amount,
                notes = notes,
                paymentMethod = method
            )
            val newId = repository.recordSellerPayment(payment)
            val savedPayment = payment.copy(id = newId)
            val seller = rawSellers.value.firstOrNull { it.id == sellerId }
            val soldItems = getSoldItemsSummary(sellerId)
            if (seller != null) {
                val currentDetail = sellersWithStats.value.firstOrNull { it.seller.id == sellerId }
                val newRemaining = ((currentDetail?.remainingBalance ?: 0.0) - amount).coerceAtLeast(0.0)
                _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(savedPayment, seller, newRemaining, soldItems)
            }
            onDone(savedPayment)
        }
    }

    fun printCustomerReceipt(context: Context, transaction: LapakTransaction, items: List<TransactionItem>) {
        val html = com.example.util.ReceiptPrinter.buildCustomerReceiptHtml(transaction, items, shopProfile.value)
        com.example.util.ReceiptPrinter.printHtml(context, html, "Struk_${transaction.transactionNumber}")
    }

    fun printSellerPayoutReceipt(context: Context, payment: SellerPayment, seller: Seller, remainingBalance: Double, soldItems: List<SoldItemSummary> = emptyList()) {
        val html = com.example.util.ReceiptPrinter.buildSellerPayoutReceiptHtml(payment, seller, shopProfile.value, remainingBalance, soldItems)
        com.example.util.ReceiptPrinter.printHtml(context, html, "Bukti_Bayar_${seller.name}")
    }

    fun getSellerPayoutReceiptText(payment: SellerPayment, seller: Seller, remainingBalance: Double, soldItems: List<SoldItemSummary> = emptyList()): String {
        return com.example.util.ReceiptPrinter.formatSellerPayoutReceiptText(payment, seller, shopProfile.value, remainingBalance, soldItems)
    }

    // Operating Expenses
    val allExpenses: StateFlow<List<OperatingExpense>> = repository.allExpenses.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun addExpense(title: String, amount: Double, notes: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addExpense(
                OperatingExpense(
                    title = title,
                    amount = amount,
                    notes = notes
                )
            )
            onDone()
        }
    }

    fun deleteExpense(expense: OperatingExpense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // WhatsApp Stock Report Formatter
    fun generateStockReportText(isAllSellers: Boolean, targetSellerId: Long?): String {
        val lang = currentLanguage.value
        val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm", if (lang == "id") Locale("id", "ID") else Locale.ENGLISH)
        val dateStr = sdf.format(Date())
        val profile = shopProfile.value
        val products = productsWithDetails.value.filter { it.currentStock > 0 }

        val sb = StringBuilder()
        sb.append(if (lang == "id") "LAPORAN STOK TERSISA\n" else "REMAINING STOCK REPORT\n")
        sb.append("${profile.shopName.uppercase()}\n")
        sb.append(if (lang == "id") "Tanggal: $dateStr\n\n" else "Date: $dateStr\n\n")

        if (isAllSellers) {
            val bySeller = products.groupBy { it.seller.name }
            if (bySeller.isEmpty()) {
                sb.append(if (lang == "id") "(Tidak ada stok produk tersisa saat ini)\n\n" else "(No remaining product stock at this time)\n\n")
            } else {
                bySeller.forEach { (sellerName, prodList) ->
                    sb.append("${sellerName.uppercase()}:\n")
                    prodList.forEach { p ->
                        sb.append("- ${p.product.name}: ${p.currentStock} ${p.product.unit}\n")
                    }
                    sb.append("\n")
                }
            }
        } else {
            val target = products.filter { it.seller.id == targetSellerId }
            val sellerName = target.firstOrNull()?.seller?.name
                ?: rawSellers.value.firstOrNull { it.id == targetSellerId }?.name
                ?: if (lang == "id") "PENYETOR" else "SELLER"

            sb.append("${sellerName.uppercase()}:\n")
            if (target.isEmpty()) {
                sb.append(if (lang == "id") "- (Semua produk sudah habis terjual / stok 0)\n" else "- (All products sold out / 0 stock)\n")
            } else {
                target.forEach { p ->
                    sb.append("- ${p.product.name}: ${p.currentStock} ${p.product.unit}\n")
                }
            }
            sb.append("\n")
        }

        sb.append(if (lang == "id") "Mohon diperiksa untuk persiapan setoran berikutnya.\n" else "Please check and prepare for next consignment deposit.\n")
        sb.append(if (lang == "id") "Terima kasih.\n" else "Thank you.\n")
        sb.append(profile.shopName)
        return sb.toString()
    }

    // Share via WhatsApp / System Share / Clipboard
    fun shareTextToWhatsApp(context: Context, text: String, waNumber: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val cleanNumber = waNumber?.replace("[^0-9]".toRegex(), "")
                val uriStr = if (!cleanNumber.isNullOrEmpty()) {
                    val formattedNum = if (cleanNumber.startsWith("0")) "62" + cleanNumber.substring(1) else cleanNumber
                    "https://api.whatsapp.com/send?phone=$formattedNum&text=${Uri.encode(text)}"
                } else {
                    "https://api.whatsapp.com/send?text=${Uri.encode(text)}"
                }
                data = Uri.parse(uriStr)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to chooser
            shareTextGeneral(context, text)
        }
    }

    fun shareTextGeneral(context: Context, text: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Bagikan via Lapak Kita")
        shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(shareIntent)
    }

    fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Lapak Kita", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, AppStrings.get("share_copied_toast", currentLanguage.value), Toast.LENGTH_SHORT).show()
    }

    // Customer Receipt Formatter (STRICT PRIVACY: NO SELLER NAME EVER INCLUDED!)
    fun generateCustomerReceiptText(trx: LapakTransaction, items: List<TransactionItem>): String {
        val profile = shopProfile.value
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(trx.timestamp))

        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("${profile.shopName.uppercase()}\n")
        sb.append("${profile.address}\n")
        if (profile.whatsappNumber.isNotEmpty()) {
            sb.append("WA: ${profile.whatsappNumber}\n")
        }
        sb.append("================================\n")
        sb.append("No. TRX : ${trx.transactionNumber}\n")
        sb.append("Waktu   : $dateStr\n")
        sb.append("Metode  : ${trx.paymentMethod}\n")
        sb.append("--------------------------------\n")

        for (item in items) {
            sb.append("${item.productName}\n")
            sb.append("  ${item.quantity} x ${formatRupiah(item.unitPrice)} = ${formatRupiah(item.subtotal)}\n")
        }

        sb.append("--------------------------------\n")
        sb.append("TOTAL   : ${formatRupiah(trx.totalAmount)}\n")
        sb.append("BAYAR   : ${formatRupiah(trx.cashReceived)}\n")
        sb.append("KEMBALI : ${formatRupiah(trx.changeGiven)}\n")
        sb.append("================================\n")
        sb.append("${profile.footerMessage}\n")
        sb.append("================================\n")
        return sb.toString()
    }

    // Backup & Restore
    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportFullBackupJson()
            onResult(json)
        }
    }

    fun importBackup(json: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = repository.importFullBackupJson(json)
            if (res.isSuccess) {
                onDone(true, "Data cadangan berhasil dipulihkan!")
            } else {
                onDone(false, res.exceptionOrNull()?.message ?: "Gagal memulihkan cadangan!")
            }
        }
    }

    companion object {
        fun formatRupiah(value: Double): String {
            val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
            format.maximumFractionDigits = 0
            return format.format(value).replace("Rp", "Rp ")
        }
    }
}
