package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.VendorSubmissionRemote
import com.example.data.firebase.VendorSyncRepository
import com.example.data.model.*
import com.example.data.repository.*
import com.example.i18n.AppStrings
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

class LapakViewModel(
    private val repository: LapakRepository,
    private val vendorSyncRepository: VendorSyncRepository,
    val currentUserId: String
) : ViewModel() {

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    // Real-time Web Penyetor Sync Status & Notification Banner
    private val _webSyncNotification = MutableStateFlow<String?>(null)
    val webSyncNotification: StateFlow<String?> = _webSyncNotification.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

    private val _lastCloudSyncAt = MutableStateFlow<Long?>(null)
    val lastCloudSyncAt: StateFlow<Long?> = _lastCloudSyncAt.asStateFlow()

    private val _cartWarningMessage = MutableStateFlow<String?>(null)
    val cartWarningMessage: StateFlow<String?> = _cartWarningMessage.asStateFlow()

    private val processedSubmissionIds = mutableSetOf<String>()

    fun dismissWebSyncNotification() {
        _webSyncNotification.value = null
    }

    fun clearCartWarning() {
        _cartWarningMessage.value = null
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

    val themeMode: StateFlow<String> = appSettings.map { it.themeMode }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.SYSTEM
    )

    val isDarkTheme: StateFlow<Boolean> = appSettings.map { it.themeMode == ThemeMode.DARK }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            val validMode = when (mode) {
                ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM -> mode
                else -> ThemeMode.SYSTEM
            }
            val curr = appSettings.value
            repository.updateAppSettings(curr.copy(themeMode = validMode))
        }
    }

    fun toggleTheme(dark: Boolean) {
        setThemeMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            val curr = appSettings.value
            repository.updateAppSettings(curr.copy(language = lang))
        }
    }

    fun updateAdminFeeSettings(
        feeType: String,
        feeValue: Double,
        webPortalBaseUrl: String,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val safeType = if (feeType == AdminFeeType.PERCENTAGE) AdminFeeType.PERCENTAGE else AdminFeeType.FIXED
            val safeValue = if (safeType == AdminFeeType.PERCENTAGE) {
                feeValue.coerceIn(0.0, 100.0)
            } else {
                feeValue.coerceIn(0.0, 10_000_000.0)
            }
            val safeUrl = sanitizeWebPortalBaseUrl(webPortalBaseUrl)
            val curr = appSettings.value
            repository.updateAppSettings(
                curr.copy(
                    adminFeeType = safeType,
                    adminFeeValue = safeValue,
                    webPortalBaseUrl = safeUrl
                )
            )
            syncAllVendorsToCloud()
            onDone()
        }
    }

    fun updateProfile(
        name: String,
        owner: String,
        wa: String,
        address: String,
        footer: String,
        shopIconKey: String? = null,
        headerBgColorKey: String? = null,
        logoUri: String? = shopProfile.value.logoUri
    ) {
        viewModelScope.launch {
            val curr = shopProfile.value
            repository.updateShopProfile(
                curr.copy(
                    shopName = name.trim().ifEmpty { "Lapak Kita" },
                    ownerName = owner.trim(),
                    whatsappNumber = wa.trim(),
                    address = address.trim(),
                    footerMessage = footer.trim(),
                    shopIconKey = shopIconKey ?: curr.shopIconKey,
                    headerBgColorKey = headerBgColorKey ?: curr.headerBgColorKey,
                    logoUri = logoUri
                )
            )
            syncAllVendorsToCloud()
        }
    }

    fun updatePromoSettings(
        template: String,
        introText: String,
        outroText: String,
        showLogo: Boolean,
        showShopName: Boolean,
        showContact: Boolean,
        customBgUri: String? = appSettings.value.posterCustomBgUri,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val curr = appSettings.value
            repository.updateAppSettings(
                curr.copy(
                    posterTemplate = template,
                    posterIntroText = introText.trim().ifEmpty { "Menu Fresh & Ready Stok Hari Ini!" },
                    posterOutroText = outroText.trim().ifEmpty { "Yuk pesan sekarang sebelum kehabisan!" },
                    posterShowLogo = showLogo,
                    posterShowShopName = showShopName,
                    posterShowContact = showContact,
                    posterCustomBgUri = customBgUri
                )
            )
            onDone()
        }
    }

    fun updateWebPortalBaseUrl(
        webPortalBaseUrl: String,
        onDone: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val safeUrl = sanitizeWebPortalBaseUrl(webPortalBaseUrl)
            val curr = appSettings.value
            repository.updateAppSettings(curr.copy(webPortalBaseUrl = safeUrl))
            syncAllVendorsToCloud()
            onDone(safeUrl)
        }
    }

    fun updateDailyStockResetSettings(
        enabled: Boolean,
        resetHour: Int,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val safeHour = resetHour.coerceIn(0, 23)
            val curr = repository.appSettings.first()
            val updatedCycleId = OperationalCycleHelper.getOperationalCycleDate(safeHour)
            repository.updateAppSettings(
                curr.copy(
                    autoResetStockEnabled = enabled,
                    autoResetHour = safeHour,
                    lastStockResetCycleId = updatedCycleId
                )
            )
            onDone()
        }
    }

    fun resetAllStocksManually(onDone: (StockResetResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.resetAllStocksManually()
            clearCart()
            delay(150)
            syncAllVendorsToCloud()
            _webSyncNotification.value = if (result.resetProductsCount > 0) {
                "Reset Stok Manual: ${result.resetProductsCount} produk (${result.totalArchivedQty} stok) diarsipkan & direset ke 0."
            } else {
                "Semua stok produk sudah bernilai 0 dan disinkronkan ke Web Penyetor."
            }
            onDone(result)
        }
    }

    fun checkOperationalCycleOnResume() {
        viewModelScope.launch {
            val resetResult = repository.checkAndRunAutoStockResetIfNeeded()
            if (resetResult != null) {
                clearCart()
                delay(150)
                syncAllVendorsToCloud()
                val hourLabel = OperationalCycleHelper.formatResetHour(appSettings.value.autoResetHour)
                _webSyncNotification.value = if (resetResult.resetProductsCount > 0) {
                    "Auto-Reset Harian ($hourLabel WIB): ${resetResult.resetProductsCount} produk (${resetResult.totalArchivedQty} sisa stok) diarsipkan & direset ke 0."
                } else {
                    "Siklus harian baru dimulai ($hourLabel WIB) • Semua stok siap untuk kiriman hari ini."
                }
            }
        }
    }

    fun testCloudConnection(onResult: (Boolean, String) -> Unit) {
        syncAllVendorsToCloud { ok, msg ->
            if (ok) {
                onResult(true, "Koneksi Firestore aktif! $msg")
            } else {
                onResult(false, "Koneksi bermasalah: $msg")
            }
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

    init {
        // 1. Migrate legacy preview URL if present, ensure all sellers have valid vendorCode & writeToken, and push initial snapshot to Firestore
        viewModelScope.launch {
            val currentSettings = repository.appSettings.first()
            val cleanUrl = sanitizeWebPortalBaseUrl(currentSettings.webPortalBaseUrl)
            if (cleanUrl != currentSettings.webPortalBaseUrl) {
                repository.updateAppSettings(currentSettings.copy(webPortalBaseUrl = cleanUrl))
            }
            repository.ensureSampleKitchensSeeded()
            repository.ensureAllSellersHaveVendorCodes()
            val startupReset = repository.checkAndRunAutoStockResetIfNeeded()
            if (startupReset != null && startupReset.resetProductsCount > 0) {
                clearCart()
                val hourLabel = OperationalCycleHelper.formatResetHour(
                    repository.appSettings.first().autoResetHour
                )
                _webSyncNotification.value =
                    "Auto-Reset Harian ($hourLabel WIB): ${startupReset.resetProductsCount} produk (${startupReset.totalArchivedQty} sisa stok) diarsipkan & direset ke 0."
            }
            delay(300)
            syncAllVendorsToCloud()
        }
        // 2. Start real-time Firestore listener for incoming Web Penyetor submissions
        startWebSubmissionsListener()
        // 3. Start operational cycle watcher (checks every 60 seconds for 17:00 WIB rollover)
        startOperationalCycleWatcher()
    }

    private fun startOperationalCycleWatcher() {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                val resetResult = repository.checkAndRunAutoStockResetIfNeeded()
                if (resetResult != null) {
                    clearCart()
                    delay(150)
                    syncAllVendorsToCloud()
                    val hourLabel = OperationalCycleHelper.formatResetHour(appSettings.value.autoResetHour)
                    _webSyncNotification.value = if (resetResult.resetProductsCount > 0) {
                        "Auto-Reset Harian ($hourLabel WIB): ${resetResult.resetProductsCount} produk (${resetResult.totalArchivedQty} sisa stok) diarsipkan & direset ke 0."
                    } else {
                        "Siklus operasional baru dimulai ($hourLabel WIB) • Siap menerima kiriman stok."
                    }
                }
            }
        }
    }

    private fun startWebSubmissionsListener() {
        viewModelScope.launch {
            try {
                vendorSyncRepository.observePendingSubmissions(currentUserId).collect { pendingList ->
                    for (sub in pendingList) {
                        if (sub.submissionId in processedSubmissionIds) continue
                        processedSubmissionIds.add(sub.submissionId)
                        handleIncomingWebSubmission(sub)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                Log.e("LapakViewModel", "Firestore submission listener error: ${e.message}", e)
            } catch (e: Exception) {
                Log.e("LapakViewModel", "Submission listener error: ${e.message}", e)
            }
        }
    }

    private suspend fun handleIncomingWebSubmission(submission: VendorSubmissionRemote) {
        val applyResult = repository.applyWebVendorSubmission(submission)
        if (applyResult.isSuccess) {
            val message = applyResult.getOrNull() ?: "Pembaruan dari Web Penyetor diterima"
            vendorSyncRepository.markSubmissionStatus(submission.submissionId, "PROCESSED")
            // Give Room flows a beat to refresh before pushing updated products & stock back to Firestore
            delay(200)
            syncVendorByCodeToCloud(submission.vendorCode, submission.submissionId)
            _webSyncNotification.value = message
        } else {
            Log.w("LapakViewModel", "Submission ${submission.submissionId} rejected: ${applyResult.exceptionOrNull()?.message}")
            vendorSyncRepository.markSubmissionStatus(submission.submissionId, "REJECTED")
        }
    }

    fun syncAllVendorsToCloud(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            try {
                repository.ensureAllSellersHaveVendorCodes()
                val sellers = repository.sellersWithStats.first()
                val products = repository.productsWithDetails.first()
                val profile = repository.shopProfile.first()
                val settings = repository.appSettings.first()

                var successCount = 0
                var lastError: String? = null
                for (sellerDetail in sellers) {
                    val sellerProds = products.filter { it.product.sellerId == sellerDetail.seller.id }
                    val res = vendorSyncRepository.syncVendorToFirestore(
                        sellerDetail = sellerDetail,
                        sellerProducts = sellerProds,
                        shopProfile = profile,
                        appSettings = settings
                    )
                    if (res.isSuccess) {
                        successCount++
                    } else {
                        lastError = res.exceptionOrNull()?.message
                    }
                }
                _lastCloudSyncAt.value = System.currentTimeMillis()
                if (lastError == null) {
                    onComplete?.invoke(true, "$successCount penyetor berhasil disinkronkan ke Web Penyetor.")
                } else {
                    onComplete?.invoke(false, "Sinkronisasi sebagian ($successCount/${sellers.size}): $lastError")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LapakViewModel", "Sync all vendors failed", e)
                onComplete?.invoke(false, e.message ?: "Gagal menyinkronkan ke cloud")
            } finally {
                _isSyncingCloud.value = false
            }
        }
    }

    fun syncSingleSellerToCloud(sellerId: Long, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _isSyncingCloud.value = true
            try {
                repository.ensureAllSellersHaveVendorCodes()
                delay(120)
                val sellers = repository.sellersWithStats.first()
                val target = sellers.firstOrNull { it.seller.id == sellerId } ?: run {
                    onComplete?.invoke(false)
                    return@launch
                }
                val products = repository.productsWithDetails.first().filter { it.product.sellerId == sellerId }
                val profile = repository.shopProfile.first()
                val settings = repository.appSettings.first()
                val res = vendorSyncRepository.syncVendorToFirestore(
                    sellerDetail = target,
                    sellerProducts = products,
                    shopProfile = profile,
                    appSettings = settings
                )
                if (res.isSuccess) _lastCloudSyncAt.value = System.currentTimeMillis()
                onComplete?.invoke(res.isSuccess)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LapakViewModel", "Single seller sync error", e)
                onComplete?.invoke(false)
            } finally {
                _isSyncingCloud.value = false
            }
        }
    }

    private suspend fun syncVendorByCodeToCloud(vendorCode: String, lastProcessedSubmissionId: String) {
        val sellers = repository.sellersWithStats.first()
        val target = sellers.firstOrNull { it.seller.vendorCode == vendorCode } ?: return
        val products = repository.productsWithDetails.first().filter { it.product.sellerId == target.seller.id }
        val profile = repository.shopProfile.first()
        val settings = repository.appSettings.first()
        val res = vendorSyncRepository.syncVendorToFirestore(
            sellerDetail = target,
            sellerProducts = products,
            shopProfile = profile,
            appSettings = settings,
            lastProcessedSubmissionId = lastProcessedSubmissionId
        )
        if (res.isSuccess) _lastCloudSyncAt.value = System.currentTimeMillis()
    }

    fun buildVendorPortalUrl(seller: Seller): String {
        val base = sanitizeWebPortalBaseUrl(appSettings.value.webPortalBaseUrl)
        val cleanCode = seller.vendorCode.trim().uppercase()
        val cleanToken = seller.writeToken.trim()
        return "$base/?kode=${Uri.encode(cleanCode)}&token=${Uri.encode(cleanToken)}"
    }

    fun buildVendorPortalShareText(seller: Seller): String {
        val profile = shopProfile.value
        val url = buildVendorPortalUrl(seller)
        val (feeType, feeValue) = AdminFeeCalculator.resolveEffectiveFee(seller, appSettings.value)
        val feeLabel = AdminFeeCalculator.formatTariffLabel(feeType, feeValue)
        return buildString {
            append("Halo ${seller.name}!\n\n")
            append("Berikut tautan Web Penyetor *${profile.shopName}* khusus untuk dapur Anda:\n")
            append("$url\n\n")
            append("Kode Penyetor: *${seller.vendorCode}*\n")
            append("Tarif Biaya Admin: $feeLabel\n\n")
            append("Lewat link tersebut, Anda bisa langsung:\n")
            append("- Cek sisa stok, barang terjual, dan setoran belum dibayar\n")
            append("- Tambah / ubah daftar produk beserta perhitungan uang bersih otomatis\n")
            append("- Input stok kiriman harian agar langsung masuk ke kasir lapak\n\n")
            append("Mohon simpan link ini dan jangan dibagikan ke penyetor lain ya. Terima kasih!")
        }
    }

    fun buildAllActiveVendorLinksShareText(sellers: List<Seller> = rawSellers.value): String {
        val profile = shopProfile.value
        val activeSellers = sellers
            .filter { it.status == SellerStatus.AKTIF }
            .sortedBy { it.name.lowercase() }

        return buildString {
            append("Halo Bapak/Ibu Penyetor ${profile.shopName},\n")
            append("Berikut link khusus masing-masing dapur untuk cek stok, setoran, dan input kiriman harian:\n\n")
            if (activeSellers.isEmpty()) {
                append("(Belum ada penyetor berstatus Aktif saat ini)\n\n")
            } else {
                activeSellers.forEach { seller ->
                    val url = buildVendorPortalUrl(seller)
                    append("*${seller.name}*\n")
                    append("$url\n\n")
                }
            }
            append("Silakan klik link sesuai nama dapur masing-masing ya. Terima kasih!")
        }
    }

    fun addSeller(
        name: String,
        wa: String,
        address: String,
        notes: String,
        customFeeType: String? = null,
        customFeeValue: Double? = null,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val newId = repository.addSeller(
                Seller(
                    name = name,
                    whatsappNumber = wa,
                    address = address,
                    notes = notes,
                    status = SellerStatus.AKTIF,
                    vendorCode = VendorCodeGenerator.generateVendorCode(),
                    writeToken = VendorCodeGenerator.generateWriteToken(),
                    customAdminFeeType = customFeeType,
                    customAdminFeeValue = customFeeValue
                )
            )
            syncSingleSellerToCloud(newId)
            onDone()
        }
    }

    fun updateSeller(seller: Seller, onDone: () -> Unit) {
        viewModelScope.launch {
            val safeSeller = seller.copy(
                vendorCode = seller.vendorCode.takeIf { VendorCodeGenerator.isValidVendorCode(it) }
                    ?: VendorCodeGenerator.generateVendorCode(),
                writeToken = seller.writeToken.takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                    ?: VendorCodeGenerator.generateWriteToken()
            )
            repository.updateSeller(safeSeller)
            syncSingleSellerToCloud(safeSeller.id)
            onDone()
        }
    }

    fun deleteSeller(seller: Seller, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSeller(seller)
            vendorSyncRepository.deleteVendorFromFirestore(seller.vendorCode)
            onDone()
        }
    }

    fun updateProduct(product: Product, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateProduct(product)
            syncSingleSellerToCloud(product.sellerId)
            onDone()
        }
    }

    fun deleteProduct(product: Product, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            syncSingleSellerToCloud(product.sellerId)
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
            syncSingleSellerToCloud(sellerId)
        }
    }

    fun reactivateSeller(sellerId: Long) {
        viewModelScope.launch {
            repository.updateSellerStatus(sellerId, SellerStatus.AKTIF, null, null, "Diaktifkan kembali oleh admin")
            syncSingleSellerToCloud(sellerId)
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
                    adminProfitPerUnit = (sellingPrice - sellerShare).coerceAtLeast(0.0)
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
            syncSingleSellerToCloud(sellerId)
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
                syncSingleSellerToCloud(sellerId)
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

    fun addToCart(product: Product, seller: Seller, stockLot: StockLot, quantity: Int = 1): Boolean {
        val currentList = _cart.value.toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.product.id == product.id && it.seller.id == seller.id
        }
        val totalAvailable = productsWithDetails.value
            .firstOrNull { it.product.id == product.id }
            ?.currentStock ?: stockLot.currentQty

        return if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            if (existing.quantity + quantity <= totalAvailable) {
                currentList[existingIndex] = existing.copy(quantity = existing.quantity + quantity)
                _cart.value = currentList
                true
            } else {
                _cartWarningMessage.value = "Stok maksimal ${product.name}: $totalAvailable ${product.unit}"
                false
            }
        } else {
            if (quantity <= totalAvailable) {
                currentList.add(CartItem(product, seller, stockLot, quantity))
                _cart.value = currentList
                true
            } else {
                _cartWarningMessage.value = "Stok ${product.name} tidak mencukupi"
                false
            }
        }
    }

    fun updateCartQty(item: CartItem, newQty: Int) {
        if (newQty <= 0) {
            removeFromCart(item)
            return
        }
        val totalAvailable = productsWithDetails.value
            .firstOrNull { it.product.id == item.product.id }
            ?.currentStock ?: item.stockLot.currentQty
        if (newQty > totalAvailable) {
            _cartWarningMessage.value = "Stok maksimal: $totalAvailable ${item.product.unit}"
            return
        }
        val currentList = _cart.value.toMutableList()
        val index = currentList.indexOfFirst {
            it.product.id == item.product.id && it.seller.id == item.seller.id
        }
        if (index >= 0) {
            currentList[index] = item.copy(quantity = newQty)
            _cart.value = currentList
        }
    }

    fun removeFromCart(item: CartItem) {
        _cart.value = _cart.value.filterNot {
            it.product.id == item.product.id && it.seller.id == item.seller.id
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

        val affectedSellerIds = items.map { it.seller.id }.distinct()
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
                affectedSellerIds.forEach { syncSingleSellerToCloud(it) }
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
            val affectedSellers = repository.getItemsForTransaction(trxId).first().map { it.sellerId }.distinct()
            val res = repository.cancelTransaction(trxId, reason)
            if (res.isSuccess) {
                affectedSellers.forEach { syncSingleSellerToCloud(it) }
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

    fun showSellerPaymentReceipt(
        payment: SellerPayment,
        seller: Seller,
        remainingBalance: Double,
        soldItems: List<SoldItemSummary> = emptyList()
    ) {
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
                _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(
                    latest,
                    sellerDetail.seller,
                    sellerDetail.remainingBalance,
                    soldItems
                )
            } else {
                val statement = SellerPayment(
                    id = 0,
                    sellerId = sellerDetail.seller.id,
                    paymentDate = System.currentTimeMillis(),
                    amount = sellerDetail.totalPaid,
                    notes = "Rekapitulasi Setoran ke Penjual",
                    paymentMethod = "Tunai"
                )
                _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(
                    statement,
                    sellerDetail.seller,
                    sellerDetail.remainingBalance,
                    soldItems
                )
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
            syncSingleSellerToCloud(sellerId)
            onDone(savedPayment)
        }
    }

    fun printCustomerReceipt(context: Context, transaction: LapakTransaction, items: List<TransactionItem>) {
        val html = com.example.util.ReceiptPrinter.buildCustomerReceiptHtml(transaction, items, shopProfile.value)
        com.example.util.ReceiptPrinter.printHtml(context, html, "Struk_${transaction.transactionNumber}")
    }

    fun printSellerPayoutReceipt(
        context: Context,
        payment: SellerPayment,
        seller: Seller,
        remainingBalance: Double,
        soldItems: List<SoldItemSummary> = emptyList()
    ) {
        val html = com.example.util.ReceiptPrinter.buildSellerPayoutReceiptHtml(
            payment,
            seller,
            shopProfile.value,
            remainingBalance,
            soldItems
        )
        com.example.util.ReceiptPrinter.printHtml(context, html, "Bukti_Bayar_${seller.name}")
    }

    fun getSellerPayoutReceiptText(
        payment: SellerPayment,
        seller: Seller,
        remainingBalance: Double,
        soldItems: List<SoldItemSummary> = emptyList()
    ): String {
        return com.example.util.ReceiptPrinter.formatSellerPayoutReceiptText(
            payment,
            seller,
            shopProfile.value,
            remainingBalance,
            soldItems
        )
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

    // WhatsApp Promo & Stock Report Formatter (Customer-friendly grouped by Nama Dapur Penyetor)
    fun generateStockReportText(isAllSellers: Boolean, targetSellerId: Long?): String {
        val sdf = SimpleDateFormat("EEEE, d MMMM yyyy • HH:mm", Locale("id", "ID"))
        val dateStr = sdf.format(Date())
        val profile = shopProfile.value
        val availableProducts = productsWithDetails.value
            .filter { it.currentStock > 0 && it.product.isActive }
            .sortedWith(compareBy({ it.seller.name.lowercase() }, { it.product.name.lowercase() }))

        val sb = StringBuilder()

        if (isAllSellers) {
            sb.append("*INFO SISA STOK PENYETOR — ${profile.shopName.uppercase()}*\n")
            sb.append("Update: $dateStr WIB\n\n")

            val bySeller = availableProducts.groupBy { it.seller.name }
            if (bySeller.isEmpty()) {
                sb.append("Alhamdulillah! Seluruh stok titipan hari ini sudah habis terjual (stok 0).\n\n")
            } else {
                bySeller.forEach { (sellerName, prodList) ->
                    val totalQtySeller = prodList.sumOf { it.currentStock }
                    sb.append("*$sellerName* (Total sisa: $totalQtySeller)\n")
                    prodList.forEach { p ->
                        sb.append("- ${p.product.name}: sisa *${p.currentStock} ${p.product.unit}* (${formatRupiah(p.product.sellingPrice)})\n")
                    }
                    sb.append("\n")
                }
            }
            sb.append("Bagi penyetor yang ingin menambah stok atau update menu, silakan langsung kirim lewat Link Web Penyetor masing-masing ya. Terima kasih!")
        } else {
            val target = availableProducts.filter { it.seller.id == targetSellerId }
            val sellerName = target.firstOrNull()?.seller?.name
                ?: rawSellers.value.firstOrNull { it.id == targetSellerId }?.name
                ?: "Penyetor"

            sb.append("*INFO SISA STOK — ${profile.shopName.uppercase()}*\n")
            sb.append("Update: $dateStr WIB\n\n")
            sb.append("*$sellerName*\n")
            if (target.isEmpty()) {
                sb.append("- Alhamdulillah semua produk sudah habis terjual (stok 0)!\n")
            } else {
                target.forEach { p ->
                    sb.append("- ${p.product.name}: sisa *${p.currentStock} ${p.product.unit}* (${formatRupiah(p.product.sellingPrice)})\n")
                }
            }
            sb.append("\nSilakan kirim tambahan stok melalui Link Web Penyetor jika ingin restok. Terima kasih!")
        }

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
                syncAllVendorsToCloud()
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
