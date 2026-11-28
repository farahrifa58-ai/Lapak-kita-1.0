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
import com.example.config.LicenseStorage
import com.example.data.firebase.ActivationAuthRepository
import com.example.data.firebase.FinancialSyncRepository
import com.example.data.firebase.VendorSubmissionRemote
import com.example.data.firebase.VendorSyncRepository
import com.example.data.model.*
import com.example.data.repository.*
import com.example.i18n.AppStrings
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

enum class AppScreen {
    DASHBOARD,
    SALES,
    CASHIER,
    HISTORY,
    SELLERS,
    FINANCIAL_REPORT,
    SETTINGS,
    ABOUT
}

data class SellerPayoutReceiptState(
    val payment: SellerPayment,
    val seller: Seller,
    val remainingBalance: Double,
    val soldItems: List<SoldItemSummary> = emptyList()
)

data class WebPortalSyncFeedback(
    val id: Long = System.nanoTime(),
    val isSuccess: Boolean,
    val message: String
)

@OptIn(ExperimentalCoroutinesApi::class)
class LapakViewModel(
    private val repository: LapakRepository,
    private val vendorSyncRepository: VendorSyncRepository,
    private val financialSyncRepository: FinancialSyncRepository,
    private val activationAuthRepository: ActivationAuthRepository,
    private val licenseStorage: LicenseStorage,
    val currentUserId: String,
    private val currentDeviceId: String = licenseStorage.getDeviceId(),
    private val initialBusinessName: String? = null
) : ViewModel() {

    private val sessionActiveFlag = AtomicBoolean(true)
    private val cloudHydratedFlag = AtomicBoolean(false)
    private val _sessionActive = MutableStateFlow(true)
    private val _isCloudLoaded = MutableStateFlow(false)
    val isCloudLoaded: StateFlow<Boolean> = _isCloudLoaded.asStateFlow()

    private val jobsLock = Any()
    private val listenerJobs = mutableListOf<Job>()
    private val backgroundJobs = mutableListOf<Job>()
    private val sellerSyncMutexes = ConcurrentHashMap<Long, Mutex>()
    private val activeVendorsSyncInProgress = AtomicBoolean(false)

    private fun isActiveAuthenticatedSession(): Boolean {
        return sessionActiveFlag.get() &&
            currentUserId.isNotBlank() &&
            Firebase.auth.currentUser?.uid == currentUserId
    }

    /**
     * Strict Download-First Guard:
     * Forbids ANY Firestore write/upload while `isCloudLoaded == false` so an empty or stale
     * local state can NEVER overwrite real cloud data in `users/{uid}` or `lapak_vendors/{kodePenyetor}`.
     */
    private fun canWriteToCloud(): Boolean {
        return isActiveAuthenticatedSession() && cloudHydratedFlag.get()
    }

    private fun trackListenerJob(job: Job) {
        synchronized(jobsLock) {
            if (!sessionActiveFlag.get()) {
                job.cancel()
            } else {
                listenerJobs.add(job)
            }
        }
    }

    private fun trackBackgroundJob(job: Job) {
        synchronized(jobsLock) {
            if (!sessionActiveFlag.get()) {
                job.cancel()
            } else {
                backgroundJobs.removeAll { !it.isActive }
                backgroundJobs.add(job)
            }
        }
    }

    /**
     * Immediately unsubscribes all active Firestore `onSnapshot` listeners, cancels any in-flight
     * sync/watcher jobs, and resets every in-memory UI state flow back to empty/default values.
     * Must be invoked BEFORE `Firebase.auth.signOut()` or switching to another account UID.
     */
    fun teardownSessionAndUnsubscribe() {
        sessionActiveFlag.set(false)
        cloudHydratedFlag.set(false)
        _isCloudLoaded.value = false
        _sessionActive.value = false

        synchronized(jobsLock) {
            listenerJobs.forEach { it.cancel() }
            listenerJobs.clear()
            backgroundJobs.forEach { it.cancel() }
            backgroundJobs.clear()
        }

        processedSubmissionIds.clear()
        manualRoleOverride = null
        _showRoleSwitchDialog.value = false
        _isRestoringFromCloud.value = false
        _currentScreen.value = AppScreen.DASHBOARD
        _connectedDevicesCount.value = 1
        _isOwner.value = true
        _webSyncNotification.value = null
        _webPortalSyncFeedback.value = null
        _isSyncingCloud.value = false
        _lastCloudSyncAt.value = null
        _pendingSync.value = false
        _cartWarningMessage.value = null
        _timeframe.value = "TODAY"
        _financialPeriodFilter.value = FinancialPeriodFilter()
        _isSyncingFinancials.value = false
        _cart.value = emptyList()
        _cashReceivedText.value = ""
        _paymentMethod.value = "Tunai"
        _lastCompletedTransaction.value = null
        _lastSellerPaymentReceipt.value = null
    }

    override fun onCleared() {
        teardownSessionAndUnsubscribe()
        super.onCleared()
    }

    // Navigation
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // Device Role: Device 1 = Owner (full access), Device 2 = Kasir / Karyawan (financials hidden)
    // Also supports quick local switching via Owner PIN from the top header badge.
    private var manualRoleOverride: Boolean? = null
    private val _isOwner = MutableStateFlow(
        licenseStorage.getCachedIsOwnerDevice(currentUserId)
    )
    val isOwner: StateFlow<Boolean> = _isOwner.asStateFlow()

    private val _showRoleSwitchDialog = MutableStateFlow(false)
    val showRoleSwitchDialog: StateFlow<Boolean> = _showRoleSwitchDialog.asStateFlow()

    fun openRoleSwitchDialog() {
        _showRoleSwitchDialog.value = true
    }

    fun dismissRoleSwitchDialog() {
        _showRoleSwitchDialog.value = false
    }

    fun isUsingDefaultOwnerPin(): Boolean {
        return licenseStorage.getOwnerPin(currentUserId) == LicenseStorage.DEFAULT_OWNER_PIN
    }

    fun switchToCashierMode() {
        manualRoleOverride = false
        _isOwner.value = false
        if (_currentScreen.value == AppScreen.FINANCIAL_REPORT) {
            _currentScreen.value = AppScreen.DASHBOARD
        }
        _showRoleSwitchDialog.value = false
    }

    fun verifyOwnerPinAndSwitch(enteredPin: String): Boolean {
        val expected = licenseStorage.getOwnerPin(currentUserId)
        val cleanInput = enteredPin.trim()
        if (cleanInput == expected) {
            manualRoleOverride = true
            _isOwner.value = true
            _showRoleSwitchDialog.value = false
            return true
        }
        return false
    }

    fun updateOwnerPin(oldPin: String, newPin: String): Result<Unit> {
        val expected = licenseStorage.getOwnerPin(currentUserId)
        if (oldPin.trim() != expected) {
            return Result.failure(IllegalArgumentException("PIN lama tidak sesuai"))
        }
        val cleanNew = newPin.filter { it.isDigit() }
        if (cleanNew.length !in 4..6) {
            return Result.failure(IllegalArgumentException("PIN baru harus 4 sampai 6 digit angka"))
        }
        licenseStorage.saveOwnerPin(currentUserId, cleanNew)
        return Result.success(Unit)
    }

    fun navigateTo(screen: AppScreen) {
        if (screen == AppScreen.FINANCIAL_REPORT && !_isOwner.value) {
            _currentScreen.value = AppScreen.DASHBOARD
            return
        }
        _currentScreen.value = screen
    }

    // Multi-Device Real-Time Sync Status ("Perangkat Terhubung: X / 2 HP")
    private val _connectedDevicesCount = MutableStateFlow(
        licenseStorage.getCachedConnectedDevicesCount(currentUserId)
    )
    val connectedDevicesCount: StateFlow<Int> = _connectedDevicesCount.asStateFlow()

    // Real-time Web Penyetor & Cloud Auto-Backup Sync Status
    private val _webSyncNotification = MutableStateFlow<String?>(null)
    val webSyncNotification: StateFlow<String?> = _webSyncNotification.asStateFlow()

    private val _webPortalSyncFeedback = MutableStateFlow<WebPortalSyncFeedback?>(null)
    val webPortalSyncFeedback: StateFlow<WebPortalSyncFeedback?> = _webPortalSyncFeedback.asStateFlow()

    private val _isSyncingCloud = MutableStateFlow(false)
    val isSyncingCloud: StateFlow<Boolean> = _isSyncingCloud.asStateFlow()

    private val _lastCloudSyncAt = MutableStateFlow<Long?>(
        licenseStorage.getLastCloudSyncAt(currentUserId)
    )
    val lastCloudSyncAt: StateFlow<Long?> = _lastCloudSyncAt.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _pendingSync = MutableStateFlow(
        licenseStorage.hasPendingCloudSync(currentUserId)
    )
    val pendingSync: StateFlow<Boolean> = _pendingSync.asStateFlow()

    private val _isRestoringFromCloud = MutableStateFlow(false)
    val isRestoringFromCloud: StateFlow<Boolean> = _isRestoringFromCloud.asStateFlow()

    private val flushSyncInProgress = AtomicBoolean(false)

    private fun markCloudSyncedNow() {
        val now = System.currentTimeMillis()
        _lastCloudSyncAt.value = now
        _pendingSync.value = false
        licenseStorage.saveLastCloudSyncAt(currentUserId, now)
        licenseStorage.setPendingCloudSync(currentUserId, false)
    }

    private fun markPendingCloudSync() {
        _pendingSync.value = true
        licenseStorage.setPendingCloudSync(currentUserId, true)
    }

    fun onNetworkConnectivityChanged(isConnected: Boolean) {
        val wasOffline = !_isOnline.value
        _isOnline.value = isConnected
        if (isConnected && (_pendingSync.value || wasOffline) && canWriteToCloud()) {
            flushPendingCloudBackup()
        }
    }

    /**
     * Automatically flushes all queued offline local changes (sellers, products, stocks,
     * transactions, expenses, settlements, daily closings) to Firestore on Dispatchers.IO.
     */
    fun flushPendingCloudBackup(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!isActiveAuthenticatedSession()) {
            onComplete?.invoke(false, "Sesi akun tidak aktif.")
            return
        }
        if (!_isOnline.value) {
            markPendingCloudSync()
            onComplete?.invoke(false, "Menunggu koneksi internet...")
            return
        }
        if (!canWriteToCloud()) {
            markPendingCloudSync()
            onComplete?.invoke(false, "Menunggu sinkronisasi awal selesai...")
            return
        }
        if (!flushSyncInProgress.compareAndSet(false, true)) {
            return
        }
        val job = viewModelScope.launch(Dispatchers.IO) {
            _isSyncingCloud.value = true
            try {
                if (!canWriteToCloud()) return@launch
                repository.ensureAllSellersHaveVendorCodes()
                var hadFailure = false

                // 1. Sellers & Products
                val sellers = repository.allSellers.first()
                for (seller in sellers) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val snap = repository.getSellerSyncSnapshotDirect(seller.id) ?: continue
                    if (syncSellerSnapshotConcurrently(snap).isFailure) {
                        hadFailure = true
                    }
                }

                // 2. Transactions, Expenses, Settlements
                val finSnap = repository.getFinancialSyncSnapshot()
                val sellersById = finSnap.sellers.associateBy { it.id }
                val sellerStatusById = repository.sellersWithStats.first()
                    .associate { it.seller.id to it.paymentStatus }

                for (trx in finSnap.transactions) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val trxItems = finSnap.itemsByTransactionId[trx.id].orEmpty()
                    if (financialSyncRepository.syncTransactionToFirestore(trx, trxItems, expectedUid = currentUserId).isFailure) {
                        hadFailure = true
                    }
                }
                for (exp in finSnap.expenses) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    if (financialSyncRepository.syncExpenseToFirestore(exp, expectedUid = currentUserId).isFailure) {
                        hadFailure = true
                    }
                }
                for (pay in finSnap.payments) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val seller = sellersById[pay.sellerId]
                    val status = sellerStatusById[pay.sellerId] ?: "SEBAGIAN"
                    if (financialSyncRepository.syncSettlementToFirestore(pay, seller, status, expectedUid = currentUserId).isFailure) {
                        hadFailure = true
                    }
                }

                // 3. Daily Closing Archives
                val archives = repository.allDailyClosingArchives.first()
                for (archive in archives) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val items = LapakRepository.decodeClosingItemsJson(archive.itemsBreakdownJson)
                    if (financialSyncRepository.syncDailyClosingToFirestore(archive, items, expectedUid = currentUserId).isFailure) {
                        hadFailure = true
                    }
                }

                // 4. User settings
                val settings = repository.appSettings.first()
                financialSyncRepository.syncUserSettingsToFirestore(settings, expectedUid = currentUserId)

                if (hadFailure) {
                    markPendingCloudSync()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false, "Sebagian data masih menunggu koneksi internet.")
                    }
                } else {
                    markCloudSyncedNow()
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true, "Seluruh data berhasil dicadangkan ke Cloud.")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("LapakViewModel", "Pending cloud backup flush failed: ${e.message}")
                markPendingCloudSync()
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, e.message ?: "Menunggu koneksi internet...")
                }
            } finally {
                _isSyncingCloud.value = false
                flushSyncInProgress.set(false)
            }
        }
        trackBackgroundJob(job)
    }

    private val _cartWarningMessage = MutableStateFlow<String?>(null)
    val cartWarningMessage: StateFlow<String?> = _cartWarningMessage.asStateFlow()

    private val processedSubmissionIds = Collections.synchronizedSet(mutableSetOf<String>())

    fun dismissWebSyncNotification() {
        _webSyncNotification.value = null
    }

    fun dismissWebPortalSyncFeedback() {
        _webPortalSyncFeedback.value = null
    }

    private fun emitWebPortalSyncFeedback(isSuccess: Boolean, message: String) {
        if (!sessionActiveFlag.get()) return
        _webPortalSyncFeedback.value = WebPortalSyncFeedback(
            id = System.nanoTime(),
            isSuccess = isSuccess,
            message = message
        )
    }

    fun clearCartWarning() {
        _cartWarningMessage.value = null
    }

    // Settings & Profile
    val shopProfile: StateFlow<ShopProfile> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(ShopProfile()) else repository.shopProfile
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ShopProfile()
    )

    val appSettings: StateFlow<AppSettings> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(AppSettings()) else repository.appSettings
    }.stateIn(
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

    // Permanent Daily Closing Archives (Auto & Manual Tutup Buku)
    val dailyClosingArchives: StateFlow<List<DailyClosingArchive>> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList()) else repository.allDailyClosingArchives
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val isTodayClosed: StateFlow<Boolean> = combine(dailyClosingArchives, appSettings) { archives, settings ->
        val today = DailyClosingHelper.todayDateString()
        archives.any { it.closingDate == today } || settings.lastClosedDate == today
    }.stateIn(
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

    fun updateAutoClosingSettings(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        leftoverMode: String,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val updated = repository.updateAutoClosingSettings(
                enabled = enabled,
                hour = hour,
                minute = minute,
                leftoverMode = leftoverMode
            )
            licenseStorage.saveAutoClosingPrefs(
                uid = currentUserId,
                enabled = updated.autoClosingEnabled,
                hour = updated.autoClosingHour,
                minute = updated.autoClosingMinute,
                leftoverMode = updated.autoClosingLeftoverMode,
                lastClosedDate = updated.lastClosedDate
            )
            if (canWriteToCloud()) {
                val res = financialSyncRepository.syncUserSettingsToFirestore(updated, expectedUid = currentUserId)
                if (res.isFailure) {
                    onDone(true, "Jadwal disimpan lokal (akan disinkronkan ke cloud saat online).")
                    return@launch
                }
            }
            val timeLabel = DailyClosingHelper.formatTime(updated.autoClosingHour, updated.autoClosingMinute)
            val statusMsg = if (updated.autoClosingEnabled) {
                "Auto Tutup Buku aktif pukul $timeLabel WIB (${AutoClosingLeftoverMode.label(updated.autoClosingLeftoverMode)})."
            } else {
                "Auto Tutup Buku harian dinonaktifkan."
            }
            onDone(true, statusMsg)
        }
    }

    fun updateCashierVisibilitySettings(
        showOmzet: Boolean,
        showProfit: Boolean,
        showExpenses: Boolean,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val updated = repository.updateCashierVisibilitySettings(
                showOmzet = showOmzet,
                showProfit = showProfit,
                showExpenses = showExpenses
            )
            licenseStorage.saveCashierVisibilityPrefs(
                uid = currentUserId,
                showOmzet = updated.cashierShowOmzet,
                showProfit = updated.cashierShowProfit,
                showExpenses = updated.cashierShowExpenses
            )
            if (canWriteToCloud()) {
                val res = financialSyncRepository.syncUserSettingsToFirestore(updated, expectedUid = currentUserId)
                if (res.isFailure) {
                    onDone(true, "Pengaturan tampilan kasir disimpan lokal (menunggu koneksi cloud).")
                    return@launch
                }
            }
            onDone(true, "Kustomisasi tampilan HP Kasir berhasil disimpan & disinkronkan!")
        }
    }

    fun updatePrinterSettings(
        printerName: String,
        printerAddress: String,
        paperSize: String,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val updated = repository.updatePrinterSettings(
                printerName = printerName,
                printerAddress = printerAddress,
                paperSize = paperSize
            )
            licenseStorage.savePrinterPrefs(
                uid = currentUserId,
                printerName = updated.printerName,
                printerAddress = updated.printerAddress,
                paperSize = updated.printerPaperSize
            )
            val label = updated.printerName.ifBlank { "Printer Sistem / PDF" }
            onDone(true, "Pengaturan print disimpan: $label (${PrinterPaperSize.label(updated.printerPaperSize)})")
        }
    }

    fun runManualDailyClosing(
        leftoverModeOverride: String? = null,
        onDone: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val todayWib = DailyClosingHelper.todayDateString()
            val result = repository.performDailyClosing(
                targetDateWib = todayWib,
                triggerType = DailyClosingTriggerType.MANUAL,
                leftoverModeOverride = leftoverModeOverride,
                applyStockLeftoverRule = true,
                includeActiveCurrentStock = true,
                forceIfEmpty = true
            )
            if (result.isFailure) {
                onDone(false, result.exceptionOrNull()?.message ?: "Gagal melakukan Tutup Buku Hari Ini.")
                return@launch
            }
            val archive = result.getOrThrow()
            if (archive.leftoverMode == AutoClosingLeftoverMode.RESET_ZERO) {
                clearCart()
            }
            syncDailyClosingArchiveAndState(archive)
            val dateLabel = DailyClosingHelper.formatDisplayDate(archive.closingDate)
            val msg = "Tutup Buku tanggal $dateLabel berhasil dicatat ke Arsip Riwayat."
            _webSyncNotification.value = msg
            onDone(true, msg)
        }
    }

    private suspend fun syncDailyClosingArchiveAndState(archive: DailyClosingArchive) {
        val updatedSettings = repository.appSettings.first()
        licenseStorage.saveAutoClosingPrefs(
            uid = currentUserId,
            enabled = updatedSettings.autoClosingEnabled,
            hour = updatedSettings.autoClosingHour,
            minute = updatedSettings.autoClosingMinute,
            leftoverMode = updatedSettings.autoClosingLeftoverMode,
            lastClosedDate = updatedSettings.lastClosedDate
        )
        if (!canWriteToCloud() || !_isOnline.value) {
            markPendingCloudSync()
            return
        }

        val items = LapakRepository.decodeClosingItemsJson(archive.itemsBreakdownJson)
        val resClose = withContext(Dispatchers.IO) {
            financialSyncRepository.syncDailyClosingToFirestore(archive, items, expectedUid = currentUserId)
        }
        val resSettings = withContext(Dispatchers.IO) {
            financialSyncRepository.syncUserSettingsToFirestore(updatedSettings, expectedUid = currentUserId)
        }
        if (resClose.isFailure || resSettings.isFailure) {
            markPendingCloudSync()
        } else {
            markCloudSyncedNow()
        }
        if (archive.leftoverMode == AutoClosingLeftoverMode.RESET_ZERO) {
            delay(150)
            syncAllVendorsToCloud()
        }
    }

    private suspend fun checkAndRunAutoClosingInternal() {
        if (!cloudHydratedFlag.get() || !sessionActiveFlag.get()) return
        val createdArchives = repository.checkAndRunAutoClosing()
        if (createdArchives.isEmpty()) return

        if (createdArchives.any { it.leftoverMode == AutoClosingLeftoverMode.RESET_ZERO }) {
            clearCart()
        }
        createdArchives.forEach { archive ->
            syncDailyClosingArchiveAndState(archive)
        }
        val latest = createdArchives.maxByOrNull { it.closingDate } ?: return
        val displayDate = DailyClosingHelper.formatDisplayDate(latest.closingDate)
        _webSyncNotification.value = "Auto Tutup Buku tanggal $displayDate telah berhasil dicatat ke Riwayat"
    }

    fun checkOperationalCycleOnResume() {
        viewModelScope.launch {
            if (!cloudHydratedFlag.get()) return@launch
            checkAndRunAutoClosingInternal()
            val resetResult = repository.checkAndRunAutoStockResetIfNeeded()
            if (resetResult != null) {
                clearCart()
                delay(150)
                if (canWriteToCloud()) {
                    syncAllVendorsToCloud()
                }
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

    private val emptyDashboardSummary = DashboardSummary(
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

    val dashboardSummary: StateFlow<DashboardSummary> = combine(_sessionActive, _timeframe) { active, tf ->
        active to tf
    }.flatMapLatest { (active, tf) ->
        if (!active) flowOf(emptyDashboardSummary) else repository.getDashboardSummary(tf)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyDashboardSummary
    )

    // Products & Stock
    val productsWithDetails: StateFlow<List<ProductDetailItem>> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList()) else repository.productsWithDetails
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Sellers
    val sellersWithStats: StateFlow<List<SellerDetailItem>> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList()) else repository.sellersWithStats
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val rawSellers: StateFlow<List<Seller>> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList()) else repository.allSellers
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Financial Report Filter & Chronological Snapshot State
    private val _financialPeriodFilter = MutableStateFlow(FinancialPeriodFilter())
    val financialPeriodFilter: StateFlow<FinancialPeriodFilter> = _financialPeriodFilter.asStateFlow()

    private val _isSyncingFinancials = MutableStateFlow(false)
    val isSyncingFinancials: StateFlow<Boolean> = _isSyncingFinancials.asStateFlow()

    fun setFinancialPeriodFilter(filter: FinancialPeriodFilter) {
        _financialPeriodFilter.value = filter
    }

    private val emptyFinancialReport = FinancialReportBuilder.buildReport(
        filter = FinancialPeriodFilter(),
        sellers = emptyList(),
        transactions = emptyList(),
        transactionItems = emptyList(),
        expenses = emptyList(),
        payments = emptyList()
    )

    val financialReport: StateFlow<FinancialReportSnapshot> = _sessionActive.flatMapLatest { active ->
        if (!active) {
            flowOf(emptyFinancialReport)
        } else {
            combine(
                _financialPeriodFilter,
                repository.inventoryData,
                repository.financialData,
                repository.returnMovements,
                repository.allDailyClosingArchives
            ) { filter, inv, fin, returns, archives ->
                FinancialReportBuilder.buildReport(
                    filter = filter,
                    sellers = inv.sellers,
                    transactions = fin.transactions,
                    transactionItems = fin.items,
                    expenses = fin.expenses,
                    payments = fin.payments,
                    stockLots = inv.lots,
                    returnMovements = returns,
                    dailyClosingArchives = archives
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyFinancialReport
    )

    // Sellers Screen Period Filter & Per-Seller Product Report State
    private val _sellerPeriodFilter = MutableStateFlow(
        FinancialPeriodFilter(period = FinancialReportPeriod.ALL_TIME)
    )
    val sellerPeriodFilter: StateFlow<FinancialPeriodFilter> = _sellerPeriodFilter.asStateFlow()

    fun setSellerPeriodFilter(filter: FinancialPeriodFilter) {
        _sellerPeriodFilter.value = filter
    }

    private val emptySellerPeriodReport = SellerPeriodReportBuilder.build(
        filter = FinancialPeriodFilter(period = FinancialReportPeriod.ALL_TIME),
        sellers = emptyList(),
        products = emptyList(),
        stockLots = emptyList(),
        transactions = emptyList(),
        transactionItems = emptyList(),
        payments = emptyList(),
        returnMovements = emptyList(),
        dailyClosingArchives = emptyList(),
        appSettings = AppSettings()
    )

    val sellerPeriodReport: StateFlow<SellerPeriodReportSnapshot> = _sessionActive.flatMapLatest { active ->
        if (!active) {
            flowOf(emptySellerPeriodReport)
        } else {
            combine(
                _sellerPeriodFilter,
                repository.inventoryData,
                repository.financialData,
                combine(repository.returnMovements, repository.allDailyClosingArchives, repository.appSettings) { ret, arch, cfg ->
                    Triple(ret, arch, cfg)
                }
            ) { filter, inv, fin, extra ->
                SellerPeriodReportBuilder.build(
                    filter = filter,
                    sellers = inv.sellers,
                    products = inv.products,
                    stockLots = inv.lots,
                    transactions = fin.transactions,
                    transactionItems = fin.items,
                    payments = fin.payments,
                    returnMovements = extra.first,
                    dailyClosingArchives = extra.second,
                    appSettings = extra.third
                )
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptySellerPeriodReport
    )

    init {
        // Strict Download-First Isolated Initialization:
        // 1. Ensure clean default ShopProfile & AppSettings (ZERO sample/dummy data!)
        // 2. Keep `isCloudLoaded = false` — strictly FORBID any Firestore upload/write!
        // 3. Attach real-time Firestore `onSnapshot` listeners FIRST and wait until all 4
        //    operational subcollections emit server-confirmed snapshots (`!isFromCache`)
        //    and finish merging into local Room state.
        // 4. ONLY THEN flip `isCloudLoaded = true` and start secondary watchers.
        //    NEVER auto-upload empty local state or overwrite `lapak_vendors/*` on startup.
        val startupJob = viewModelScope.launch {
            if (!sessionActiveFlag.get()) return@launch
            repository.ensureDefaultProfileAndSettings(initialBusinessName)

            val currentSettings = repository.appSettings.first()
            val cleanUrl = sanitizeWebPortalBaseUrl(currentSettings.webPortalBaseUrl)
            if (cleanUrl != currentSettings.webPortalBaseUrl) {
                repository.updateAppSettings(currentSettings.copy(webPortalBaseUrl = cleanUrl))
            }

            repository.ensureAllSellersHaveVendorCodes()

            if (!isActiveAuthenticatedSession()) return@launch

            val localEmptyBeforeRestore = repository.allSellers.first().isEmpty() &&
                repository.allTransactions.first().isEmpty()
            if (localEmptyBeforeRestore) {
                _isRestoringFromCloud.value = true
            }

            val vendorsServerReady = CompletableDeferred<Unit>()
            val transactionsServerReady = CompletableDeferred<Unit>()
            val expensesServerReady = CompletableDeferred<Unit>()
            val settlementsServerReady = CompletableDeferred<Unit>()
            val dailyClosingsServerReady = CompletableDeferred<Unit>()
            val userSettingsServerReady = CompletableDeferred<Unit>()

            // Step 2: Attach real-time onSnapshot listeners FIRST (Download-First)
            startMultiDeviceRealtimeListeners(
                vendorsServerReady = vendorsServerReady,
                transactionsServerReady = transactionsServerReady,
                expensesServerReady = expensesServerReady,
                settlementsServerReady = settlementsServerReady,
                dailyClosingsServerReady = dailyClosingsServerReady,
                userSettingsServerReady = userSettingsServerReady
            )

            // Explicit one-shot cloud fetch on fresh device / reinstall to guarantee complete anti-duplicate Room merge
            if (localEmptyBeforeRestore && _isOnline.value) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val cloudVendors = financialSyncRepository.fetchCloudVendorsCatalog(currentUserId)
                        if (cloudVendors.isSuccess && isActiveAuthenticatedSession()) {
                            repository.mergeCloudVendorsCatalog(
                                vendors = cloudVendors.getOrThrow(),
                                currentDeviceId = currentDeviceId,
                                ignoreSourceDeviceCheck = true
                            )
                        }
                        val cloudFin = financialSyncRepository.fetchAllCloudFinancialData(currentUserId)
                        if (cloudFin.isSuccess && isActiveAuthenticatedSession()) {
                            repository.mergeCloudFinancialData(cloudFin.getOrThrow())
                        }
                        val cloudClosings = financialSyncRepository.fetchAllCloudDailyClosings(currentUserId)
                        if (cloudClosings.isSuccess && isActiveAuthenticatedSession()) {
                            repository.mergeCloudDailyClosings(cloudClosings.getOrThrow())
                        }
                    }.onFailure { err ->
                        Log.w("LapakViewModel", "Initial cloud auto-restore fetch warning: ${err.message}")
                    }
                }
            }

            // Wait until Firestore server snapshots for all collections + user settings are downloaded & merged
            // (bounded timeout so offline startup never hangs forever)
            val hydratedOnline = withTimeoutOrNull(8_000L) {
                awaitAll(
                    vendorsServerReady,
                    transactionsServerReady,
                    expensesServerReady,
                    settlementsServerReady,
                    dailyClosingsServerReady,
                    userSettingsServerReady
                )
                true
            } ?: false

            _isRestoringFromCloud.value = false

            if (!isActiveAuthenticatedSession()) return@launch

            // Restore UID-scoped local printer preferences (device-local)
            val (savedPrinterName, savedPrinterAddr, savedPaperSize) = licenseStorage.getPrinterPrefs(currentUserId)
            val currPostHydration = repository.appSettings.first()
            if (currPostHydration.printerAddress.isBlank() && savedPrinterAddr.isNotBlank()) {
                repository.updatePrinterSettings(savedPrinterName, savedPrinterAddr, savedPaperSize)
            }

            // Step 3: Cloud data is now fully loaded into state — unlock user-driven writes!
            cloudHydratedFlag.set(true)
            _isCloudLoaded.value = true
            if (hydratedOnline && _isOnline.value) {
                markCloudSyncedNow()
            }

            // If there were offline changes queued from a previous session, flush them now!
            if (_pendingSync.value && _isOnline.value) {
                flushPendingCloudBackup()
            }

            // Step 4: Run Auto Tutup Buku & operational cycle check ONLY after hydration
            checkAndRunAutoClosingInternal()

            val startupReset = repository.checkAndRunAutoStockResetIfNeeded()
            if (startupReset != null && startupReset.resetProductsCount > 0) {
                clearCart()
                val hourLabel = OperationalCycleHelper.formatResetHour(
                    repository.appSettings.first().autoResetHour
                )
                _webSyncNotification.value =
                    "Auto-Reset Harian ($hourLabel WIB): ${startupReset.resetProductsCount} produk (${startupReset.totalArchivedQty} sisa stok) diarsipkan & direset ke 0."
            }

            startWebSubmissionsListener()
            startOperationalCycleWatcher()
        }
        trackBackgroundJob(startupJob)
    }

    /**
     * Live real-time `onSnapshot` listeners (with MetadataChanges.INCLUDE) under `users/{uid}`
     * so transactions, products, consignment stocks, settlements, expenses, daily closing archives,
     * Auto Tutup Buku / Cashier customization settings, connected device count, and Owner vs Kasir
     * role stay synchronized instantly between HP Owner and HP Kasir.
     */
    private fun startMultiDeviceRealtimeListeners(
        vendorsServerReady: CompletableDeferred<Unit>,
        transactionsServerReady: CompletableDeferred<Unit>,
        expensesServerReady: CompletableDeferred<Unit>,
        settlementsServerReady: CompletableDeferred<Unit>,
        dailyClosingsServerReady: CompletableDeferred<Unit>,
        userSettingsServerReady: CompletableDeferred<Unit>
    ) {
        if (!isActiveAuthenticatedSession()) return

        // 1. Live connected devices counter & Owner vs Kasir role detection
        trackListenerJob(
            viewModelScope.launch {
                activationAuthRepository.observeUserProfile(currentUserId).collect { profile ->
                    if (!isActiveAuthenticatedSession()) return@collect
                    if (profile != null) {
                        val count = profile.connectedDevicesCount.coerceIn(1, ActivationAuthRepository.MAX_REGISTERED_DEVICES)
                        _connectedDevicesCount.value = count
                        licenseStorage.updateConnectedDevicesCount(currentUserId, count)

                        val ownerFlag = manualRoleOverride ?: profile.isOwnerDevice(currentDeviceId)
                        _isOwner.value = ownerFlag
                        if (manualRoleOverride == null) {
                            licenseStorage.updateIsOwnerDevice(currentUserId, ownerFlag)
                        }
                        if (!ownerFlag && _currentScreen.value == AppScreen.FINANCIAL_REPORT) {
                            _currentScreen.value = AppScreen.DASHBOARD
                        }
                    }
                }
            }
        )

        // 2. Real-time sellers, products & consignment stock catalog
        trackListenerJob(
            viewModelScope.launch {
                try {
                    financialSyncRepository.observeVendorsCatalogWithMetadata(currentUserId).collect { batch ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        if (batch.items.isNotEmpty()) {
                            val changed = repository.mergeCloudVendorsCatalog(
                                vendors = batch.items,
                                currentDeviceId = currentDeviceId,
                                ignoreSourceDeviceCheck = !cloudHydratedFlag.get()
                            )
                            if (changed > 0) {
                                _lastCloudSyncAt.value = System.currentTimeMillis()
                            }
                        }
                        if (!batch.isFromCache) {
                            vendorsServerReady.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LapakViewModel", "Realtime vendors catalog listener error: ${e.message}")
                }
            }
        )

        // 3. Real-time cashier transactions
        trackListenerJob(
            viewModelScope.launch {
                try {
                    financialSyncRepository.observeTransactionsWithMetadata(currentUserId).collect { batch ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        if (batch.items.isNotEmpty()) {
                            val changed = repository.mergeCloudTransactions(batch.items)
                            if (changed > 0) {
                                _lastCloudSyncAt.value = System.currentTimeMillis()
                            }
                        }
                        if (!batch.isFromCache) {
                            transactionsServerReady.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LapakViewModel", "Realtime transactions listener error: ${e.message}")
                }
            }
        )

        // 4. Real-time operating expenses
        trackListenerJob(
            viewModelScope.launch {
                try {
                    financialSyncRepository.observeExpensesWithMetadata(currentUserId).collect { batch ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        if (batch.items.isNotEmpty()) {
                            val changed = repository.mergeCloudExpenses(batch.items)
                            if (changed > 0) {
                                _lastCloudSyncAt.value = System.currentTimeMillis()
                            }
                        }
                        if (!batch.isFromCache) {
                            expensesServerReady.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LapakViewModel", "Realtime expenses listener error: ${e.message}")
                }
            }
        )

        // 5. Real-time seller payout settlements
        trackListenerJob(
            viewModelScope.launch {
                try {
                    financialSyncRepository.observeSettlementsWithMetadata(currentUserId).collect { batch ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        if (batch.items.isNotEmpty()) {
                            val changed = repository.mergeCloudSettlements(batch.items)
                            if (changed > 0) {
                                _lastCloudSyncAt.value = System.currentTimeMillis()
                            }
                        }
                        if (!batch.isFromCache) {
                            settlementsServerReady.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LapakViewModel", "Realtime settlements listener error: ${e.message}")
                }
            }
        )

        // 6. Real-time Permanent Daily Closing Archives
        trackListenerJob(
            viewModelScope.launch {
                try {
                    financialSyncRepository.observeDailyClosingsWithMetadata(currentUserId).collect { batch ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        if (batch.items.isNotEmpty()) {
                            val changed = repository.mergeCloudDailyClosings(batch.items)
                            if (changed > 0) {
                                _lastCloudSyncAt.value = System.currentTimeMillis()
                            }
                        }
                        if (!batch.isFromCache) {
                            dailyClosingsServerReady.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LapakViewModel", "Realtime daily closings listener error: ${e.message}")
                    dailyClosingsServerReady.complete(Unit)
                }
            }
        )

        // 7. Real-time User Settings (Auto Tutup Buku schedule & Cashier UI Customization)
        trackListenerJob(
            viewModelScope.launch {
                try {
                    financialSyncRepository.observeUserSettingsWithMetadata(currentUserId).collect { cloudSettings ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        if (cloudSettings != null) {
                            val merged = repository.applyCloudUserSettings(cloudSettings)
                            licenseStorage.saveAutoClosingPrefs(
                                uid = currentUserId,
                                enabled = merged.autoClosingEnabled,
                                hour = merged.autoClosingHour,
                                minute = merged.autoClosingMinute,
                                leftoverMode = merged.autoClosingLeftoverMode,
                                lastClosedDate = merged.lastClosedDate
                            )
                            licenseStorage.saveCashierVisibilityPrefs(
                                uid = currentUserId,
                                showOmzet = merged.cashierShowOmzet,
                                showProfit = merged.cashierShowProfit,
                                showExpenses = merged.cashierShowExpenses
                            )
                            if (!cloudSettings.isFromCache) {
                                userSettingsServerReady.complete(Unit)
                            }
                        } else {
                            userSettingsServerReady.complete(Unit)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("LapakViewModel", "Realtime user settings listener error: ${e.message}")
                    userSettingsServerReady.complete(Unit)
                }
            }
        )
    }

    private fun startOperationalCycleWatcher() {
        trackBackgroundJob(
            viewModelScope.launch {
                while (sessionActiveFlag.get()) {
                    delay(60_000)
                    if (!sessionActiveFlag.get()) break
                    checkAndRunAutoClosingInternal()
                    val resetResult = repository.checkAndRunAutoStockResetIfNeeded()
                    if (resetResult != null && sessionActiveFlag.get()) {
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
        )
    }

    private fun startWebSubmissionsListener() {
        if (!isActiveAuthenticatedSession()) return
        trackListenerJob(
            viewModelScope.launch {
                try {
                    vendorSyncRepository.observePendingSubmissions(currentUserId).collect { pendingList ->
                        if (!isActiveAuthenticatedSession()) return@collect
                        for (sub in pendingList) {
                            if (!isActiveAuthenticatedSession()) break
                            if (sub.ownerUid != currentUserId) continue
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
        )
    }

    private suspend fun handleIncomingWebSubmission(submission: VendorSubmissionRemote) {
        if (!isActiveAuthenticatedSession() || submission.ownerUid != currentUserId) return
        val applyResult = repository.applyWebVendorSubmission(submission)
        if (applyResult.isSuccess) {
            val message = applyResult.getOrNull() ?: "Pembaruan dari Web Penyetor diterima"
            vendorSyncRepository.markSubmissionStatus(
                submissionId = submission.submissionId,
                status = "PROCESSED",
                expectedUid = currentUserId
            )
            if (isActiveAuthenticatedSession()) {
                syncVendorByCodeToCloud(submission.vendorCode, submission.submissionId)
                _webSyncNotification.value = message
            }
        } else {
            Log.w("LapakViewModel", "Submission ${submission.submissionId} rejected: ${applyResult.exceptionOrNull()?.message}")
            vendorSyncRepository.markSubmissionStatus(
                submissionId = submission.submissionId,
                status = "REJECTED",
                expectedUid = currentUserId
            )
        }
    }

    fun syncAllVendorsToCloud(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!canWriteToCloud()) {
            onComplete?.invoke(false, "Menunggu data cloud selesai dimuat sebelum mengunggah ke server.")
            return
        }
        val job = viewModelScope.launch {
            _isSyncingCloud.value = true
            try {
                if (!canWriteToCloud()) return@launch
                repository.ensureAllSellersHaveVendorCodes()
                val sellers = repository.allSellers.first()
                if (sellers.isEmpty()) {
                    _lastCloudSyncAt.value = System.currentTimeMillis()
                    onComplete?.invoke(true, "Belum ada penyetor lokal untuk disinkronkan.")
                    return@launch
                }

                var successCount = 0
                var lastError: String? = null
                for (seller in sellers) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val directSnapshot = repository.getSellerSyncSnapshotDirect(seller.id) ?: continue
                    val res = syncSellerSnapshotConcurrently(directSnapshot)
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
        trackBackgroundJob(job)
    }

    /**
     * Two-way Firestore financial synchronization:
     * 1. Pulls chronological transactions, operating expenses, and seller settlements from Firestore
     *    and merges any missing records into local Room DB (new-device cross-device recovery).
     * 2. Pushes non-empty local transactions, expenses, and settlements to Firestore strictly under
     *    `users/{currentUserId}/...` after verifying active UID match.
     */
    fun syncAllFinancialDataWithCloud(onComplete: ((Boolean, String) -> Unit)? = null) {
        if (!isActiveAuthenticatedSession()) {
            onComplete?.invoke(false, "Sesi akun tidak aktif.")
            return
        }
        val job = viewModelScope.launch {
            _isSyncingFinancials.value = true
            try {
                if (!isActiveAuthenticatedSession()) return@launch
                var pulledCount = 0
                var vendorsFetchedOk = false
                var financialFetchedOk = false
                val cloudVendors = financialSyncRepository.fetchCloudVendorsCatalog(currentUserId)
                if (cloudVendors.isSuccess && isActiveAuthenticatedSession()) {
                    vendorsFetchedOk = true
                    pulledCount += repository.mergeCloudVendorsCatalog(
                        vendors = cloudVendors.getOrThrow(),
                        currentDeviceId = currentDeviceId,
                        ignoreSourceDeviceCheck = true
                    )
                }

                val cloudFetch = financialSyncRepository.fetchAllCloudFinancialData(currentUserId)
                if (cloudFetch.isSuccess && isActiveAuthenticatedSession()) {
                    financialFetchedOk = true
                    val bundle = cloudFetch.getOrThrow()
                    pulledCount += repository.mergeCloudFinancialData(bundle)
                }

                if (vendorsFetchedOk && financialFetchedOk && isActiveAuthenticatedSession()) {
                    cloudHydratedFlag.set(true)
                    _isCloudLoaded.value = true
                }

                if (!canWriteToCloud()) {
                    onComplete?.invoke(false, "Gagal mengunduh data terbaru dari cloud. Pengunggahan dibatalkan untuk melindungi data server.")
                    return@launch
                }

                val snapshot = repository.getFinancialSyncSnapshot()
                val sellersById = snapshot.sellers.associateBy { it.id }
                val sellerStatusById = repository.sellersWithStats.first()
                    .associate { it.seller.id to it.paymentStatus }

                var uploadedTrx = 0
                var uploadedExp = 0
                var uploadedPay = 0

                for (trx in snapshot.transactions) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val trxItems = snapshot.itemsByTransactionId[trx.id].orEmpty()
                    if (financialSyncRepository.syncTransactionToFirestore(trx, trxItems, expectedUid = currentUserId).isSuccess) {
                        uploadedTrx++
                    }
                }
                for (exp in snapshot.expenses) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    if (financialSyncRepository.syncExpenseToFirestore(exp, expectedUid = currentUserId).isSuccess) {
                        uploadedExp++
                    }
                }
                for (pay in snapshot.payments) {
                    if (!isActiveAuthenticatedSession()) return@launch
                    val seller = sellersById[pay.sellerId]
                    val status = sellerStatusById[pay.sellerId] ?: "SEBAGIAN"
                    if (financialSyncRepository.syncSettlementToFirestore(pay, seller, status, expectedUid = currentUserId).isSuccess) {
                        uploadedPay++
                    }
                }

                _lastCloudSyncAt.value = System.currentTimeMillis()
                val msg = buildString {
                    append("Sinkronisasi laporan selesai ($uploadedTrx transaksi, $uploadedExp biaya, $uploadedPay setoran)")
                    if (pulledCount > 0) append(" • $pulledCount catatan dipulihkan dari cloud")
                }
                onComplete?.invoke(true, msg)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LapakViewModel", "Financial cloud sync failed", e)
                onComplete?.invoke(false, e.message ?: "Gagal menyinkronkan laporan keuangan")
            } finally {
                _isSyncingFinancials.value = false
            }
        }
        trackBackgroundJob(job)
    }

    private suspend fun syncSellerSnapshotConcurrently(
        snapshot: SellerCloudSyncSnapshot,
        lastProcessedSubmissionId: String = ""
    ): Result<Unit> {
        if (!canWriteToCloud()) {
            return Result.failure(IllegalStateException("Menunggu data cloud selesai dimuat sebelum menyinkronkan"))
        }
        val mutex = sellerSyncMutexes.computeIfAbsent(snapshot.sellerDetail.seller.id) { Mutex() }
        return mutex.withLock {
            if (!canWriteToCloud()) {
                return@withLock Result.failure(IllegalStateException("Menunggu data cloud selesai dimuat sebelum menyinkronkan"))
            }
            coroutineScope {
                val publicPortalDeferred = async {
                    vendorSyncRepository.syncVendorToFirestore(
                        sellerDetail = snapshot.sellerDetail,
                        sellerProducts = snapshot.sellerProducts,
                        shopProfile = snapshot.shopProfile,
                        appSettings = snapshot.appSettings,
                        lastProcessedSubmissionId = lastProcessedSubmissionId,
                        expectedUid = currentUserId
                    )
                }
                val userCatalogDeferred = async {
                    financialSyncRepository.syncVendorCatalogToUserCloud(
                        sellerDetail = snapshot.sellerDetail,
                        sellerProducts = snapshot.sellerProducts,
                        sourceDeviceId = currentDeviceId,
                        expectedUid = currentUserId
                    )
                }
                val publicResult = publicPortalDeferred.await()
                val userCatalogResult = userCatalogDeferred.await()
                if (userCatalogResult.isFailure) {
                    Log.w(
                        "LapakViewModel",
                        "User cloud vendor catalog sync warning: ${userCatalogResult.exceptionOrNull()?.message}"
                    )
                }
                publicResult
            }
        }
    }

    fun syncSingleSellerToCloud(
        sellerId: Long,
        showSuccessFeedback: Boolean = false,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (!canWriteToCloud() || !_isOnline.value) {
            markPendingCloudSync()
            val err = if (!isActiveAuthenticatedSession()) {
                "Sesi akun tidak aktif. Silakan login Google terlebih dahulu."
            } else if (!_isOnline.value) {
                "Menunggu koneksi internet untuk menyinkronkan data penyetor..."
            } else {
                "Sedang memuat data terbaru dari cloud. Data masuk antrean sinkronisasi otomatis."
            }
            if (showSuccessFeedback) {
                emitWebPortalSyncFeedback(false, err)
            }
            onComplete?.invoke(false, err)
            return
        }
        val job = viewModelScope.launch(Dispatchers.IO) {
            _isSyncingCloud.value = true
            try {
                if (!canWriteToCloud()) return@launch
                val snapshot = repository.getSellerSyncSnapshotDirect(sellerId) ?: run {
                    val err = "Data penyetor tidak ditemukan."
                    emitWebPortalSyncFeedback(false, "Gagal sinkron ke Web Portal: $err")
                    withContext(Dispatchers.Main) { onComplete?.invoke(false, err) }
                    return@launch
                }
                val vendorCode = snapshot.sellerDetail.seller.vendorCode.trim().uppercase()
                val res = syncSellerSnapshotConcurrently(snapshot)
                if (res.isSuccess) {
                    markCloudSyncedNow()
                    val okMsg = "Tersinkron ke Web Portal ($vendorCode)"
                    if (showSuccessFeedback) {
                        emitWebPortalSyncFeedback(true, okMsg)
                    }
                    withContext(Dispatchers.Main) { onComplete?.invoke(true, okMsg) }
                } else {
                    markPendingCloudSync()
                    val errMsg = res.exceptionOrNull()?.message ?: "Gagal menyimpan ke Firestore."
                    emitWebPortalSyncFeedback(false, "Gagal sinkron ke Web Portal ($vendorCode): $errMsg")
                    withContext(Dispatchers.Main) { onComplete?.invoke(false, errMsg) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("LapakViewModel", "Single seller sync error", e)
                markPendingCloudSync()
                val errMsg = e.message ?: "Gagal menyinkronkan ke Web Portal."
                emitWebPortalSyncFeedback(false, "Gagal sinkron ke Web Portal: $errMsg")
                withContext(Dispatchers.Main) { onComplete?.invoke(false, errMsg) }
            } finally {
                _isSyncingCloud.value = false
            }
        }
        trackBackgroundJob(job)
    }

    /**
     * Explicit manual sync for Web Portal (`lapak_vendors/{kode}` + `users/{uid}/vendors/{kode}`).
     * ONLY invoked when Admin explicitly taps a Save / Sync button (never automatically in loops).
     */
    fun syncAllSellersToWebPortal(notifyOnSuccess: Boolean = false) {
        if (!canWriteToCloud()) {
            if (notifyOnSuccess) {
                emitWebPortalSyncFeedback(
                    isSuccess = false,
                    message = "Menunggu data cloud selesai dimuat sebelum menyinkronkan ke Web Portal."
                )
            }
            return
        }
        if (!activeVendorsSyncInProgress.compareAndSet(false, true)) return
        val job = viewModelScope.launch {
            try {
                if (!canWriteToCloud()) return@launch
                repository.ensureAllSellersHaveVendorCodes()
                val allSellersList = repository.allSellers.first()
                if (allSellersList.isEmpty()) return@launch

                var syncedCount = 0
                val failedCodes = mutableListOf<String>()
                var lastError: String? = null
                for (seller in allSellersList) {
                    if (!canWriteToCloud()) return@launch
                    val snapshot = repository.getSellerSyncSnapshotDirect(seller.id) ?: continue
                    val res = syncSellerSnapshotConcurrently(snapshot)
                    if (res.isSuccess) {
                        syncedCount++
                    } else {
                        failedCodes.add(seller.vendorCode.ifBlank { seller.name })
                        lastError = res.exceptionOrNull()?.message
                    }
                }
                if (syncedCount > 0) {
                    _lastCloudSyncAt.value = System.currentTimeMillis()
                }
                if (failedCodes.isNotEmpty()) {
                    emitWebPortalSyncFeedback(
                        isSuccess = false,
                        message = "Gagal sinkron ke Web Portal (${failedCodes.take(3).joinToString(", ")}): ${lastError ?: "Periksa koneksi / izin Firestore"}"
                    )
                } else if (notifyOnSuccess && syncedCount > 0) {
                    emitWebPortalSyncFeedback(
                        isSuccess = true,
                        message = "Tersinkron ke Web Portal ($syncedCount penyetor)"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("LapakViewModel", "Manual sync all sellers warning: ${e.message}")
                emitWebPortalSyncFeedback(
                    isSuccess = false,
                    message = "Gagal sinkron ke Web Portal: ${e.message ?: "Koneksi bermasalah"}"
                )
            } finally {
                activeVendorsSyncInProgress.set(false)
            }
        }
        trackBackgroundJob(job)
    }

    private suspend fun syncVendorByCodeToCloud(vendorCode: String, lastProcessedSubmissionId: String) {
        if (!canWriteToCloud()) return
        val snapshot = repository.getSellerSyncSnapshotByCodeDirect(vendorCode) ?: return
        val res = syncSellerSnapshotConcurrently(snapshot, lastProcessedSubmissionId)
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
            syncSingleSellerToCloud(newId, showSuccessFeedback = true)
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
            syncSingleSellerToCloud(safeSeller.id, showSuccessFeedback = true)
            onDone()
        }
    }

    fun deleteSeller(seller: Seller, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteSeller(seller)
            if (canWriteToCloud()) {
                coroutineScope {
                    val publicDelete = async {
                        vendorSyncRepository.deleteVendorFromFirestore(seller.vendorCode, expectedUid = currentUserId)
                    }
                    val userDelete = async {
                        financialSyncRepository.deleteVendorCatalogFromUserCloud(seller.vendorCode, expectedUid = currentUserId)
                    }
                    publicDelete.await()
                    userDelete.await()
                }
            }
            onDone()
        }
    }

    fun updateProduct(product: Product, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.updateProduct(product)
            syncSingleSellerToCloud(product.sellerId, showSuccessFeedback = true)
            onDone()
        }
    }

    fun deleteProduct(product: Product, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            syncSingleSellerToCloud(product.sellerId, showSuccessFeedback = true)
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
            syncSingleSellerToCloud(sellerId, showSuccessFeedback = true)
        }
    }

    fun reactivateSeller(sellerId: Long) {
        viewModelScope.launch {
            repository.updateSellerStatus(sellerId, SellerStatus.AKTIF, null, null, "Diaktifkan kembali oleh admin")
            syncSingleSellerToCloud(sellerId, showSuccessFeedback = true)
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
            syncSingleSellerToCloud(sellerId, showSuccessFeedback = true)
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
                syncSingleSellerToCloud(sellerId, showSuccessFeedback = true)
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "Gagal menambah setoran stok")
            }
        }
    }

    // Full Admin Stock Adjustment (increase, decrease, or set exact stock including 0)
    fun adjustProductStock(
        sellerId: Long,
        productId: Long,
        targetStock: Int,
        reason: String,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.adjustProductStock(
                sellerId = sellerId,
                productId = productId,
                targetStock = targetStock,
                reason = reason
            )
            if (result.isSuccess) {
                val finalStock = result.getOrThrow()
                // Also clamp any item in cart if stock was reduced below cart qty
                val updatedCart = _cart.value.mapNotNull { item ->
                    if (item.product.id == productId) {
                        if (finalStock <= 0) null
                        else item.copy(quantity = minOf(item.quantity, finalStock))
                    } else item
                }
                _cart.value = updatedCart
                syncSingleSellerToCloud(sellerId, showSuccessFeedback = true)
                onSuccess(finalStock)
            } else {
                onError(result.exceptionOrNull()?.message ?: "Gagal mengubah stok produk")
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
                // Real-time background auto-backup of transaction + itemized shares to Firestore
                val savedItems = repository.getItemsForTransactionDirect(trx.id)
                if (canWriteToCloud() && _isOnline.value) {
                    trackBackgroundJob(
                        viewModelScope.launch(Dispatchers.IO) {
                            val syncRes = financialSyncRepository.syncTransactionToFirestore(
                                trx,
                                savedItems,
                                expectedUid = currentUserId
                            )
                            if (syncRes.isSuccess) markCloudSyncedNow() else markPendingCloudSync()
                        }
                    )
                } else {
                    markPendingCloudSync()
                }
                onSuccess(trx)
            } else {
                onError(result.exceptionOrNull()?.message ?: "Gagal memproses transaksi")
            }
        }
    }

    // Transactions History
    val allTransactions: StateFlow<List<LapakTransaction>> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList()) else repository.allTransactions
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun getTransactionItems(trxId: Long): Flow<List<TransactionItem>> = repository.getItemsForTransaction(trxId)

    fun cancelTransaction(trxId: Long, reason: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val savedItems = repository.getItemsForTransactionDirect(trxId)
            val affectedSellers = savedItems.map { it.sellerId }.distinct()
            val res = repository.cancelTransaction(trxId, reason)
            if (res.isSuccess) {
                affectedSellers.forEach { syncSingleSellerToCloud(it) }
                // Update cancelled status in Firestore immediately in background
                if (canWriteToCloud() && _isOnline.value) {
                    repository.getTransactionById(trxId)?.let { updatedTrx ->
                        trackBackgroundJob(
                            viewModelScope.launch(Dispatchers.IO) {
                                val syncRes = financialSyncRepository.syncTransactionToFirestore(
                                    updatedTrx,
                                    savedItems,
                                    expectedUid = currentUserId
                                )
                                if (syncRes.isSuccess) markCloudSyncedNow() else markPendingCloudSync()
                            }
                        )
                    }
                } else {
                    markPendingCloudSync()
                }
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
            val currentDetail = sellersWithStats.value.firstOrNull { it.seller.id == sellerId }
            val newRemaining = ((currentDetail?.remainingBalance ?: 0.0) - amount).coerceAtLeast(0.0)
            val payoutStatus = when {
                newRemaining <= 0.0 -> "LUNAS"
                amount > 0.0 -> "SEBAGIAN"
                else -> "BELUM_DIBAYAR"
            }
            if (seller != null) {
                _lastSellerPaymentReceipt.value = SellerPayoutReceiptState(savedPayment, seller, newRemaining, soldItems)
            }
            if (canWriteToCloud() && _isOnline.value) {
                trackBackgroundJob(
                    viewModelScope.launch(Dispatchers.IO) {
                        val syncRes = financialSyncRepository.syncSettlementToFirestore(
                            payment = savedPayment,
                            seller = seller,
                            status = payoutStatus,
                            expectedUid = currentUserId
                        )
                        if (syncRes.isSuccess) markCloudSyncedNow() else markPendingCloudSync()
                    }
                )
            } else {
                markPendingCloudSync()
            }
            syncSingleSellerToCloud(sellerId)
            onDone(savedPayment)
        }
    }

    fun printCustomerReceipt(context: Context, transaction: LapakTransaction, items: List<TransactionItem>) {
        val settings = appSettings.value
        val paperSize = settings.printerPaperSize
        val btAddress = settings.printerAddress.trim()

        if (btAddress.isNotBlank() && paperSize != PrinterPaperSize.A4) {
            viewModelScope.launch {
                val escText = com.example.util.BluetoothThermalPrinter.formatCustomerReceiptEscPos(
                    transaction = transaction,
                    items = items,
                    shopProfile = shopProfile.value,
                    paperSize = paperSize
                )
                val res = com.example.util.BluetoothThermalPrinter.printEscPosText(context, btAddress, escText)
                if (res.isSuccess) {
                    Toast.makeText(context, "Struk dicetak ke ${settings.printerName.ifBlank { "Printer Bluetooth" }}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "${res.exceptionOrNull()?.message ?: "Printer Bluetooth gagal"} — membuka dialog cetak sistem.",
                        Toast.LENGTH_LONG
                    ).show()
                    val html = com.example.util.ReceiptPrinter.buildCustomerReceiptHtml(
                        transaction, items, shopProfile.value, paperSize
                    )
                    com.example.util.ReceiptPrinter.printHtml(context, html, "Struk_${transaction.transactionNumber}")
                }
            }
        } else {
            val html = com.example.util.ReceiptPrinter.buildCustomerReceiptHtml(
                transaction, items, shopProfile.value, paperSize
            )
            com.example.util.ReceiptPrinter.printHtml(context, html, "Struk_${transaction.transactionNumber}")
        }
    }

    fun printSellerPayoutReceipt(
        context: Context,
        payment: SellerPayment,
        seller: Seller,
        remainingBalance: Double,
        soldItems: List<SoldItemSummary> = emptyList()
    ) {
        val settings = appSettings.value
        val paperSize = settings.printerPaperSize
        val btAddress = settings.printerAddress.trim()

        if (btAddress.isNotBlank() && paperSize != PrinterPaperSize.A4) {
            viewModelScope.launch {
                val escText = com.example.util.BluetoothThermalPrinter.formatSellerPayoutReceiptEscPos(
                    payment = payment,
                    seller = seller,
                    shopProfile = shopProfile.value,
                    remainingBalance = remainingBalance,
                    soldItems = soldItems,
                    paperSize = paperSize
                )
                val res = com.example.util.BluetoothThermalPrinter.printEscPosText(context, btAddress, escText)
                if (res.isSuccess) {
                    Toast.makeText(context, "Bukti bayar dicetak ke ${settings.printerName.ifBlank { "Printer Bluetooth" }}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(
                        context,
                        "${res.exceptionOrNull()?.message ?: "Printer Bluetooth gagal"} — membuka dialog cetak sistem.",
                        Toast.LENGTH_LONG
                    ).show()
                    val html = com.example.util.ReceiptPrinter.buildSellerPayoutReceiptHtml(
                        payment, seller, shopProfile.value, remainingBalance, soldItems, paperSize
                    )
                    com.example.util.ReceiptPrinter.printHtml(context, html, "Bukti_Bayar_${seller.name}")
                }
            }
        } else {
            val html = com.example.util.ReceiptPrinter.buildSellerPayoutReceiptHtml(
                payment, seller, shopProfile.value, remainingBalance, soldItems, paperSize
            )
            com.example.util.ReceiptPrinter.printHtml(context, html, "Bukti_Bayar_${seller.name}")
        }
    }

    fun testPrintReceipt(
        context: Context,
        printerName: String,
        printerAddress: String,
        paperSize: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (printerAddress.isNotBlank() && paperSize != PrinterPaperSize.A4) {
                val escText = com.example.util.BluetoothThermalPrinter.formatTestPrintEscPos(
                    shopProfile = shopProfile.value,
                    printerName = printerName,
                    paperSize = paperSize
                )
                val res = com.example.util.BluetoothThermalPrinter.printEscPosText(context, printerAddress, escText)
                if (res.isSuccess) {
                    onResult(true, "Tes cetak berhasil dikirim ke ${printerName.ifBlank { printerAddress }}!")
                } else {
                    onResult(false, res.exceptionOrNull()?.message ?: "Gagal mengirim tes cetak ke printer Bluetooth.")
                }
            } else {
                val html = com.example.util.ReceiptPrinter.buildTestPrintHtml(
                    shopProfile = shopProfile.value,
                    printerName = printerName,
                    paperSize = paperSize
                )
                com.example.util.ReceiptPrinter.printHtml(context, html, "Tes_Cetak_LapakKita")
                onResult(true, "Membuka pratinjau cetak sistem (${PrinterPaperSize.label(paperSize)})...")
            }
        }
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
    val allExpenses: StateFlow<List<OperatingExpense>> = _sessionActive.flatMapLatest { active ->
        if (!active) flowOf(emptyList()) else repository.allExpenses
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun addExpense(title: String, amount: Double, notes: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val expense = OperatingExpense(
                title = title,
                amount = amount,
                notes = notes
            )
            val insertedId = repository.addExpense(expense)
            if (canWriteToCloud() && _isOnline.value) {
                trackBackgroundJob(
                    viewModelScope.launch(Dispatchers.IO) {
                        val syncRes = financialSyncRepository.syncExpenseToFirestore(
                            expense.copy(id = insertedId),
                            expectedUid = currentUserId
                        )
                        if (syncRes.isSuccess) markCloudSyncedNow() else markPendingCloudSync()
                    }
                )
            } else {
                markPendingCloudSync()
            }
            onDone()
        }
    }

    fun deleteExpense(expense: OperatingExpense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            if (canWriteToCloud() && _isOnline.value) {
                trackBackgroundJob(
                    viewModelScope.launch(Dispatchers.IO) {
                        val syncRes = financialSyncRepository.deleteExpenseFromFirestore(
                            expense,
                            expectedUid = currentUserId
                        )
                        if (syncRes.isSuccess) markCloudSyncedNow() else markPendingCloudSync()
                    }
                )
            } else {
                markPendingCloudSync()
            }
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
                if (canWriteToCloud()) {
                    syncAllVendorsToCloud()
                }
                onDone(true, "Data cadangan berhasil dipulihkan!")
            } else {
                onDone(false, res.exceptionOrNull()?.message ?: "Gagal memulihkan cadangan!")
            }
        }
    }

    fun recordGoogleDriveBackupSuccess(accountEmail: String? = null) {
        viewModelScope.launch {
            val curr = repository.appSettings.first()
            val resolvedEmail = accountEmail?.trim()?.takeIf { it.isNotBlank() }
                ?: Firebase.auth.currentUser?.email
                ?: curr.driveAccountEmail
            repository.updateAppSettings(
                curr.copy(
                    isGoogleDriveConnected = true,
                    lastBackupDate = System.currentTimeMillis(),
                    driveAccountEmail = resolvedEmail
                )
            )
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
