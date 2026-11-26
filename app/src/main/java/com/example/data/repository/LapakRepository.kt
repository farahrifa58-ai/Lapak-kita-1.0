package com.example.data.repository

import com.example.data.dao.LapakDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

data class CartItem(
    val product: Product,
    val seller: Seller,
    val stockLot: StockLot,
    var quantity: Int
) {
    val subtotal: Double get() = quantity * product.sellingPrice
    val totalSellerShare: Double get() = quantity * product.sellerShare
    val totalAdminProfit: Double get() = quantity * (product.sellingPrice - product.sellerShare)
}

data class ProductDetailItem(
    val product: Product,
    val seller: Seller,
    val currentStock: Int,
    val activeLots: List<StockLot>
)

data class SellerDetailItem(
    val seller: Seller,
    val products: List<Product>,
    val totalStock: Int,
    val totalSoldQty: Int,
    val totalRights: Double,
    val totalPaid: Double,
    val remainingBalance: Double,
    val paymentStatus: String // "Lunas", "Dibayar Sebagian", "Belum Dibayar"
)

data class DashboardSummary(
    val totalOmzet: Double,
    val totalSellerShare: Double,
    val grossAdminProfit: Double,
    val operatingExpenses: Double,
    val netAdminProfit: Double,
    val totalItemsSold: Int,
    val activeSellersCount: Int,
    val leaveSellersCount: Int,
    val availableStockCount: Int,
    val totalUnpaidSellerPayables: Double,
    val topSellingProducts: List<TopProductSummary>,
    val lowStockProducts: List<LowStockItem>,
    val dailyTrend: List<DailyMetric>
)

data class TopProductSummary(
    val productName: String,
    val sellerName: String, // Internal admin view
    val soldQty: Int,
    val revenue: Double,
    val adminProfit: Double
)

data class LowStockItem(
    val product: Product,
    val sellerName: String,
    val currentStock: Int
)

data class DailyMetric(
    val dateLabel: String,
    val revenue: Double,
    val adminProfit: Double,
    val itemsCount: Int
)

data class StockResetResult(
    val resetProductsCount: Int,
    val totalArchivedQty: Int,
    val cycleId: String,
    val isAutoReset: Boolean
)

data class LocalFinancialSyncSnapshot(
    val sellers: List<Seller>,
    val transactions: List<LapakTransaction>,
    val itemsByTransactionId: Map<Long, List<TransactionItem>>,
    val expenses: List<OperatingExpense>,
    val payments: List<SellerPayment>
)

data class SellerCloudSyncSnapshot(
    val sellerDetail: SellerDetailItem,
    val sellerProducts: List<ProductDetailItem>,
    val shopProfile: ShopProfile,
    val appSettings: AppSettings
)

class LapakRepository(private val dao: LapakDao) {

    // Shop Profile
    val shopProfile: Flow<ShopProfile> = dao.getShopProfileListFlow().map {
        it.firstOrNull() ?: ShopProfile()
    }

    suspend fun updateShopProfile(profile: ShopProfile) = withContext(Dispatchers.IO) {
        dao.insertOrUpdateShopProfile(profile)
    }

    // App Settings
    val appSettings: Flow<AppSettings> = dao.getAppSettingsListFlow().map {
        it.firstOrNull() ?: AppSettings()
    }

    suspend fun updateAppSettings(settings: AppSettings) = withContext(Dispatchers.IO) {
        dao.insertOrUpdateAppSettings(settings)
    }

    /**
     * Ensures a fresh per-UID local database has clean default profile & settings rows
     * without inserting any demo sellers, products, transactions, or expenses.
     */
    suspend fun ensureDefaultProfileAndSettings(initialBusinessName: String? = null) = withContext(Dispatchers.IO) {
        if (dao.getShopProfile() == null) {
            val cleanShopName = initialBusinessName?.trim().orEmpty().ifBlank { "Lapak Kita" }
            dao.insertOrUpdateShopProfile(
                ShopProfile(
                    id = 1,
                    shopName = cleanShopName,
                    ownerName = "",
                    whatsappNumber = "",
                    address = "",
                    footerMessage = "Terima kasih atas kunjungan & kepercayaan Anda berbelanja di $cleanShopName!"
                )
            )
        }
        if (dao.getAppSettings() == null) {
            dao.insertOrUpdateAppSettings(
                AppSettings(
                    id = 1,
                    themeMode = ThemeMode.SYSTEM,
                    language = "id",
                    adminFeeType = AdminFeeType.FIXED,
                    adminFeeValue = 2000.0,
                    webPortalBaseUrl = DEFAULT_WEB_PORTAL_BASE_URL,
                    autoResetStockEnabled = true,
                    autoResetHour = 17,
                    lastStockResetCycleId = OperationalCycleHelper.getOperationalCycleDate(17)
                )
            )
        }
    }

    // Sellers
    val allSellers: Flow<List<Seller>> = dao.getAllSellersFlow()

    suspend fun addSeller(seller: Seller): Long = withContext(Dispatchers.IO) {
        val enriched = seller.copy(
            vendorCode = seller.vendorCode.takeIf { VendorCodeGenerator.isValidVendorCode(it) }
                ?: VendorCodeGenerator.generateVendorCode(),
            writeToken = seller.writeToken.takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                ?: VendorCodeGenerator.generateWriteToken()
        )
        dao.insertSeller(enriched)
    }

    suspend fun ensureAllSellersHaveVendorCodes(): List<Seller> = withContext(Dispatchers.IO) {
        val all = dao.getAllSellersDirect()
        val updatedList = mutableListOf<Seller>()
        for (s in all) {
            if (!VendorCodeGenerator.isValidVendorCode(s.vendorCode) || !VendorCodeGenerator.isValidWriteToken(s.writeToken)) {
                val fixed = s.copy(
                    vendorCode = s.vendorCode.takeIf { VendorCodeGenerator.isValidVendorCode(it) }
                        ?: VendorCodeGenerator.generateVendorCode(),
                    writeToken = s.writeToken.takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                        ?: VendorCodeGenerator.generateWriteToken()
                )
                dao.updateSeller(fixed)
                updatedList.add(fixed)
            } else {
                updatedList.add(s)
            }
        }
        updatedList
    }

    suspend fun ensureSampleKitchensSeeded(): Int = withContext(Dispatchers.IO) {
        com.example.data.database.LapakDatabase.ensureSampleKitchensSeeded(dao)
    }

    private val stockResetMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Checks WIB operational cycle (default 17:00 -> 17:00 next day).
     * - Initializes baseline cycle ID on first run without wiping pre-existing stock.
     * - When cycle rolls over past resetHour and autoResetStockEnabled is ON,
     *   archives remaining stock into StockMovement history and resets all product stocks to 0.
     */
    suspend fun checkAndRunAutoStockResetIfNeeded(): StockResetResult? = withContext(Dispatchers.IO) {
        stockResetMutex.lock()
        try {
            val settings = dao.getAppSettings() ?: AppSettings().also {
                dao.insertOrUpdateAppSettings(it)
            }
            val currentCycleId = OperationalCycleHelper.getOperationalCycleDate(settings.autoResetHour)

            if (settings.lastStockResetCycleId.isBlank()) {
                dao.insertOrUpdateAppSettings(settings.copy(lastStockResetCycleId = currentCycleId))
                return@withContext null
            }

            if (!settings.autoResetStockEnabled) {
                return@withContext null
            }

            if (settings.lastStockResetCycleId != currentCycleId) {
                return@withContext performResetAllStocksLocked(
                    settings = settings,
                    cycleId = currentCycleId,
                    isAutoReset = true
                )
            }
            null
        } finally {
            stockResetMutex.unlock()
        }
    }

    /**
     * Manually archives all remaining stock to daily history and resets every product stock to 0.
     */
    suspend fun resetAllStocksManually(): StockResetResult = withContext(Dispatchers.IO) {
        stockResetMutex.lock()
        try {
            val settings = dao.getAppSettings() ?: AppSettings()
            val currentCycleId = OperationalCycleHelper.getOperationalCycleDate(settings.autoResetHour)
            performResetAllStocksLocked(
                settings = settings,
                cycleId = currentCycleId,
                isAutoReset = false
            )
        } finally {
            stockResetMutex.unlock()
        }
    }

    private suspend fun performResetAllStocksLocked(
        settings: AppSettings,
        cycleId: String,
        isAutoReset: Boolean
    ): StockResetResult {
        val activeLots = dao.getAllStockLotsDirect().filter { it.currentQty > 0 }
        val lotsByProduct = activeLots.groupBy { it.productId }
        val resetHourLabel = OperationalCycleHelper.formatResetHour(settings.autoResetHour)

        var resetProductsCount = 0
        var totalArchivedQty = 0

        for ((productId, lots) in lotsByProduct) {
            val sumRemaining = lots.sumOf { it.currentQty }
            if (sumRemaining <= 0) continue

            val firstLot = lots.first()
            for (lot in lots) {
                dao.updateStockLot(lot.copy(currentQty = 0))
            }

            val archiveNote = if (isAutoReset) {
                "Arsip Auto-Reset Harian ($resetHourLabel WIB • Siklus $cycleId): sisa $sumRemaining ${firstLot.unit} direset ke 0"
            } else {
                "Arsip Reset Stok Manual (Siklus $cycleId): sisa $sumRemaining ${firstLot.unit} direset ke 0"
            }

            dao.insertMovement(
                StockMovement(
                    productId = productId,
                    sellerId = firstLot.sellerId,
                    stockLotId = firstLot.id,
                    type = StockMovementType.PENGEMBALIAN,
                    quantityChange = -sumRemaining,
                    finalStock = 0,
                    reasonOrNote = archiveNote
                )
            )
            resetProductsCount++
            totalArchivedQty += sumRemaining
        }

        dao.insertOrUpdateAppSettings(settings.copy(lastStockResetCycleId = cycleId))
        return StockResetResult(
            resetProductsCount = resetProductsCount,
            totalArchivedQty = totalArchivedQty,
            cycleId = cycleId,
            isAutoReset = isAutoReset
        )
    }

    suspend fun applyWebVendorSubmission(
        submission: com.example.data.firebase.VendorSubmissionRemote
    ): Result<String> = withContext(Dispatchers.IO) {
        val seller = dao.getSellerByVendorCode(submission.vendorCode)
            ?: return@withContext Result.failure(Exception("Penyetor ${submission.vendorCode} tidak ditemukan"))
        val settings = dao.getAppSettings() ?: AppSettings()
        val (feeType, feeValue) = AdminFeeCalculator.resolveEffectiveFee(seller, settings)

        var addedProductsCount = 0
        var editedProductsCount = 0
        var totalAddedStock = 0

        // 1. Add new products from Web Penyetor (with strict anti-duplication check)
        val existingByNormName = dao.getProductsBySellerDirect(seller.id)
            .associateBy { it.name.trim().lowercase() }
            .toMutableMap()
        val seenClientIds = mutableSetOf<String>()
        val seenBatchNames = mutableSetOf<String>()

        for (np in submission.newProducts) {
            val cleanName = np.name.trim()
            if (cleanName.isEmpty()) continue
            val normName = cleanName.lowercase()
            if (np.clientItemId.isNotBlank() && !seenClientIds.add(np.clientItemId)) continue
            if (!seenBatchNames.add(normName)) continue

            val calc = AdminFeeCalculator.calculate(np.sellingPrice, feeType, feeValue)
            val alreadyExists = existingByNormName[normName]
            val targetProdId = if (alreadyExists != null) {
                // Prevent duplicate product creation on slow-network retries: update existing product instead
                val updated = alreadyExists.copy(
                    category = np.category,
                    unit = np.unit,
                    sellingPrice = calc.sellingPrice,
                    sellerShare = calc.sellerShare,
                    adminProfitPerUnit = calc.adminFee,
                    isActive = true
                )
                dao.updateProduct(updated)
                existingByNormName[normName] = updated
                editedProductsCount++
                updated.id
            } else {
                val created = Product(
                    sellerId = seller.id,
                    name = cleanName,
                    category = np.category,
                    unit = np.unit,
                    sellingPrice = calc.sellingPrice,
                    sellerShare = calc.sellerShare,
                    adminProfitPerUnit = calc.adminFee
                )
                val insertedId = dao.insertProduct(created)
                existingByNormName[normName] = created.copy(id = insertedId)
                addedProductsCount++
                insertedId
            }

            if (np.initialStock > 0) {
                val noteText = buildString {
                    append("Kiriman via Web Penyetor")
                    if (submission.note.isNotBlank()) append(" (${submission.note})")
                }
                depositStock(
                    sellerId = seller.id,
                    productId = targetProdId,
                    quantity = np.initialStock,
                    unit = np.unit,
                    sellingPrice = calc.sellingPrice,
                    sellerShare = calc.sellerShare,
                    notes = noteText
                )
                totalAddedStock += np.initialStock
            }
        }

        // 2. Edit existing products belonging strictly to this seller
        // Preserve Admin's custom per-item pricing/commission whenever price is unchanged or has a custom commission.
        val existingSellerProducts = dao.getProductsBySellerDirect(seller.id).associateBy { it.id }
        for (ep in submission.editedProducts) {
            val existing = existingSellerProducts[ep.localProductId] ?: continue
            val existingPrice = existing.sellingPrice.coerceAtLeast(0.0)
            val existingShare = existing.sellerShare.coerceIn(0.0, existingPrice)
            val existingCommission = (existingPrice - existingShare).coerceAtLeast(0.0)
            val globalCalcForOldPrice = AdminFeeCalculator.calculate(existingPrice, feeType, feeValue)
            val hasCustomItemCommission = kotlin.math.abs(existingCommission - globalCalcForOldPrice.adminFee) > 0.01
            val priceUnchanged = kotlin.math.abs(ep.sellingPrice - existingPrice) < 0.01

            val (finalPrice, finalShare, finalCommission) = when {
                priceUnchanged -> Triple(existingPrice, existingShare, existingCommission)
                hasCustomItemCommission -> {
                    val safeNewPrice = ep.sellingPrice.coerceAtLeast(0.0)
                    val preservedCommission = existingCommission.coerceIn(0.0, safeNewPrice)
                    val recalculatedShare = (safeNewPrice - preservedCommission).coerceAtLeast(0.0)
                    Triple(safeNewPrice, recalculatedShare, preservedCommission)
                }
                else -> {
                    val calc = AdminFeeCalculator.calculate(ep.sellingPrice, feeType, feeValue)
                    Triple(calc.sellingPrice, calc.sellerShare, calc.adminFee)
                }
            }

            dao.updateProduct(
                existing.copy(
                    name = ep.name,
                    category = ep.category,
                    unit = ep.unit,
                    sellingPrice = finalPrice,
                    sellerShare = finalShare,
                    adminProfitPerUnit = finalCommission
                )
            )
            editedProductsCount++
        }

        // 3. Apply daily stock additions belonging strictly to this seller
        val refreshedSellerProducts = dao.getProductsBySellerDirect(seller.id).associateBy { it.id }
        for (sa in submission.stockAdditions) {
            val prod = refreshedSellerProducts[sa.localProductId] ?: continue
            if (sa.addedQty <= 0) continue
            val noteText = buildString {
                append("Kiriman via Web Penyetor")
                if (submission.note.isNotBlank()) append(" (${submission.note})")
            }
            depositStock(
                sellerId = seller.id,
                productId = prod.id,
                quantity = sa.addedQty,
                unit = prod.unit,
                sellingPrice = prod.sellingPrice,
                sellerShare = prod.sellerShare,
                notes = noteText
            )
            totalAddedStock += sa.addedQty
        }

        val summaryParts = mutableListOf<String>()
        if (addedProductsCount > 0) summaryParts.add("$addedProductsCount produk baru")
        if (editedProductsCount > 0) summaryParts.add("$editedProductsCount produk diperbarui")
        if (totalAddedStock > 0) summaryParts.add("+$totalAddedStock stok masuk")
        val summaryMsg = if (summaryParts.isEmpty()) {
            "Update dari ${seller.name} diterima"
        } else {
            "Web Penyetor (${seller.name}): ${summaryParts.joinToString(", ")}"
        }
        Result.success(summaryMsg)
    }

    suspend fun updateSeller(seller: Seller) = withContext(Dispatchers.IO) {
        dao.updateSeller(seller)
    }

    suspend fun deleteSeller(seller: Seller) = withContext(Dispatchers.IO) {
        dao.deleteSeller(seller)
    }

    suspend fun updateSellerStatus(
        sellerId: Long,
        newStatus: SellerStatus,
        leaveStartDate: Long? = null,
        leaveEndDate: Long? = null,
        leaveReason: String? = null
    ) = withContext(Dispatchers.IO) {
        val seller = dao.getSellerById(sellerId) ?: return@withContext
        val oldStatus = seller.status
        val updated = seller.copy(
            status = newStatus,
            leaveStartDate = if (newStatus == SellerStatus.LIBUR) leaveStartDate ?: System.currentTimeMillis() else null,
            leaveEndDate = if (newStatus == SellerStatus.LIBUR) leaveEndDate else null,
            leaveReason = if (newStatus == SellerStatus.LIBUR) leaveReason else null
        )
        dao.updateSeller(updated)
        dao.insertStatusHistory(
            SellerStatusHistory(
                sellerId = sellerId,
                previousStatus = oldStatus,
                newStatus = newStatus,
                reason = leaveReason ?: "Status diubah ke $newStatus"
            )
        )
    }

    fun getStatusHistoryForSeller(sellerId: Long) = dao.getStatusHistoryForSellerFlow(sellerId)

    // Products
    val allProducts: Flow<List<Product>> = dao.getAllProductsFlow()

    suspend fun addProduct(product: Product): Long = withContext(Dispatchers.IO) {
        dao.insertProduct(product)
    }

    suspend fun updateProduct(product: Product) = withContext(Dispatchers.IO) {
        val safePrice = product.sellingPrice.coerceAtLeast(0.0)
        val safeShare = product.sellerShare.coerceIn(0.0, safePrice)
        val safeProfit = (safePrice - safeShare).coerceAtLeast(0.0)
        val normalized = product.copy(
            sellingPrice = safePrice,
            sellerShare = safeShare,
            adminProfitPerUnit = safeProfit
        )
        dao.updateProduct(normalized)
        // Keep active stock lots aligned with updated product price/share/unit
        val activeLots = dao.getActiveStockLotsForProduct(normalized.id)
        for (lot in activeLots) {
            if (lot.sellingPrice != safePrice || lot.sellerShare != safeShare || lot.unit != normalized.unit) {
                dao.updateStockLot(
                    lot.copy(
                        unit = normalized.unit,
                        sellingPrice = safePrice,
                        sellerShare = safeShare
                    )
                )
            }
        }
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        dao.deleteProduct(product)
    }

    /**
     * Builds an immediately consistent, fresh cloud-sync snapshot directly from Room DAO
     * (never relies on possibly stale Flow emissions or arbitrary delays after a local edit).
     */
    suspend fun getSellerSyncSnapshotDirect(sellerId: Long): SellerCloudSyncSnapshot? = withContext(Dispatchers.IO) {
        val rawSeller = dao.getSellerById(sellerId) ?: return@withContext null
        val seller = if (!VendorCodeGenerator.isValidVendorCode(rawSeller.vendorCode) ||
            !VendorCodeGenerator.isValidWriteToken(rawSeller.writeToken)
        ) {
            val fixed = rawSeller.copy(
                vendorCode = rawSeller.vendorCode.takeIf { VendorCodeGenerator.isValidVendorCode(it) }
                    ?: VendorCodeGenerator.generateVendorCode(),
                writeToken = rawSeller.writeToken.takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                    ?: VendorCodeGenerator.generateWriteToken()
            )
            dao.updateSeller(fixed)
            fixed
        } else {
            rawSeller
        }

        val sellerProductsRaw = dao.getProductsBySellerDirect(seller.id)
        val allLots = dao.getAllStockLotsDirect()
        val sellerLots = allLots.filter { it.sellerId == seller.id }
        val lotsByProd = sellerLots.groupBy { it.productId }

        val productDetails = sellerProductsRaw.map { prod ->
            val activeLots = lotsByProd[prod.id]?.filter { it.currentQty > 0 }.orEmpty()
            ProductDetailItem(
                product = prod,
                seller = seller,
                currentStock = activeLots.sumOf { it.currentQty },
                activeLots = activeLots
            )
        }

        val validTrxIds = dao.getAllTransactionsDirect()
            .filter { !it.isCancelled }
            .map { it.id }
            .toSet()
        val sellerSoldItems = dao.getAllTransactionItemsDirect()
            .filter { it.sellerId == seller.id && it.transactionId in validTrxIds }
        val sellerPayments = dao.getAllSellerPaymentsDirect()
            .filter { it.sellerId == seller.id }

        val totalStock = sellerLots.sumOf { it.currentQty }
        val totalSoldQty = sellerSoldItems.sumOf { it.quantity }
        val totalRights = sellerSoldItems.sumOf { it.totalSellerShare }
        val totalPaid = sellerPayments.sumOf { it.amount }
        val remainingBalance = (totalRights - totalPaid).coerceAtLeast(0.0)
        val paymentStatus = when {
            totalRights == 0.0 -> "Lunas"
            remainingBalance <= 0.0 -> "Lunas"
            totalPaid > 0.0 -> "Dibayar Sebagian"
            else -> "Belum Dibayar"
        }

        SellerCloudSyncSnapshot(
            sellerDetail = SellerDetailItem(
                seller = seller,
                products = sellerProductsRaw,
                totalStock = totalStock,
                totalSoldQty = totalSoldQty,
                totalRights = totalRights,
                totalPaid = totalPaid,
                remainingBalance = remainingBalance,
                paymentStatus = paymentStatus
            ),
            sellerProducts = productDetails,
            shopProfile = dao.getShopProfile() ?: ShopProfile(),
            appSettings = dao.getAppSettings() ?: AppSettings()
        )
    }

    suspend fun getSellerSyncSnapshotByCodeDirect(vendorCode: String): SellerCloudSyncSnapshot? = withContext(Dispatchers.IO) {
        val seller = dao.getSellerByVendorCode(vendorCode.trim().uppercase()) ?: return@withContext null
        getSellerSyncSnapshotDirect(seller.id)
    }

    // Combined Products with real-time stock & seller details
    val productsWithDetails: Flow<List<ProductDetailItem>> = combine(
        dao.getAllProductsFlow(),
        dao.getAllSellersFlow(),
        dao.getAllStockLotsFlow()
    ) { products, sellers, lots ->
        val sellerMap = sellers.associateBy { it.id }
        val lotMap = lots.groupBy { it.productId }

        products.map { product ->
            val seller = sellerMap[product.sellerId] ?: Seller(id = product.sellerId, name = "Penyetor #${product.sellerId}", whatsappNumber = "")
            val activeLots = lotMap[product.id]?.filter { it.currentQty > 0 } ?: emptyList()
            val currentStock = activeLots.sumOf { it.currentQty }
            ProductDetailItem(
                product = product,
                seller = seller,
                currentStock = currentStock,
                activeLots = activeLots
            )
        }
    }

data class InventoryData(
    val sellers: List<Seller>,
    val products: List<Product>,
    val lots: List<StockLot>
)

data class FinancialData(
    val transactions: List<LapakTransaction>,
    val items: List<TransactionItem>,
    val expenses: List<OperatingExpense>,
    val payments: List<SellerPayment>
)

    val inventoryData: Flow<InventoryData> = combine(
        dao.getAllSellersFlow(),
        dao.getAllProductsFlow(),
        dao.getAllStockLotsFlow()
    ) { s, p, l -> InventoryData(s, p, l) }

    val financialData: Flow<FinancialData> = combine(
        dao.getAllTransactionsFlow(),
        dao.getAllTransactionItemsFlow(),
        dao.getAllExpensesFlow(),
        dao.getAllPaymentsFlow()
    ) { t, i, e, p -> FinancialData(t, i, e, p) }

    // Combined Sellers with financial & stock statistics
    val sellersWithStats: Flow<List<SellerDetailItem>> = combine(
        inventoryData,
        financialData
    ) { inv, fin ->
        val validTrxIds = fin.transactions.filter { !it.isCancelled }.map { it.id }.toSet()
        val validItems = fin.items.filter { it.transactionId in validTrxIds }

        val productsBySeller = inv.products.groupBy { it.sellerId }
        val lotsBySeller = inv.lots.groupBy { it.sellerId }
        val itemsBySeller = validItems.groupBy { it.sellerId }
        val paymentsBySeller = fin.payments.groupBy { it.sellerId }

        inv.sellers.map { seller ->
            val sellerProducts = productsBySeller[seller.id] ?: emptyList()
            val sellerLots = lotsBySeller[seller.id] ?: emptyList()
            val sellerSoldItems = itemsBySeller[seller.id] ?: emptyList()
            val sellerPayments = paymentsBySeller[seller.id] ?: emptyList()

            val totalStock = sellerLots.sumOf { it.currentQty }
            val totalSoldQty = sellerSoldItems.sumOf { it.quantity }
            val totalRights = sellerSoldItems.sumOf { it.totalSellerShare }
            val totalPaid = sellerPayments.sumOf { it.amount }
            val remainingBalance = (totalRights - totalPaid).coerceAtLeast(0.0)

            val paymentStatus = when {
                totalRights == 0.0 -> "Lunas"
                remainingBalance <= 0.0 -> "Lunas"
                totalPaid > 0.0 -> "Dibayar Sebagian"
                else -> "Belum Dibayar"
            }

            SellerDetailItem(
                seller = seller,
                products = sellerProducts,
                totalStock = totalStock,
                totalSoldQty = totalSoldQty,
                totalRights = totalRights,
                totalPaid = totalPaid,
                remainingBalance = remainingBalance,
                paymentStatus = paymentStatus
            )
        }
    }

    // Consignment Deposit
    suspend fun depositStock(
        sellerId: Long,
        productId: Long,
        quantity: Int,
        unit: String,
        sellingPrice: Double,
        sellerShare: Double,
        notes: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val seller = dao.getSellerById(sellerId) ?: return@withContext Result.failure(Exception("Penyetor tidak ditemukan"))
        val product = dao.getProductById(productId) ?: return@withContext Result.failure(Exception("Produk tidak ditemukan"))

        val lotId = dao.insertStockLot(
            StockLot(
                productId = productId,
                sellerId = sellerId,
                initialQty = quantity,
                currentQty = quantity,
                unit = unit,
                sellingPrice = sellingPrice,
                sellerShare = sellerShare,
                notes = notes
            )
        )

        val currentTotalStock = dao.getProductTotalStock(productId)
        dao.insertMovement(
            StockMovement(
                productId = productId,
                sellerId = sellerId,
                stockLotId = lotId,
                type = StockMovementType.SETORAN_TAMBAHAN,
                quantityChange = quantity,
                finalStock = currentTotalStock,
                reasonOrNote = notes.ifEmpty { "Setoran baru: $quantity $unit" }
            )
        )

        Result.success(lotId)
    }

    // Checkout Transaction
    suspend fun checkout(
        cartItems: List<CartItem>,
        cashReceived: Double,
        paymentMethod: String
    ): Result<LapakTransaction> = withContext(Dispatchers.IO) {
        if (cartItems.isEmpty()) {
            return@withContext Result.failure(Exception("Keranjang belanja kosong"))
        }

        // Verify stock sufficiency for every item
        for (item in cartItems) {
            val freshLots = dao.getActiveStockLotsForProduct(item.product.id)
            val available = freshLots.sumOf { it.currentQty }
            if (available < item.quantity) {
                return@withContext Result.failure(
                    Exception("Stok '${item.product.name}' milik '${item.seller.name}' tidak mencukupi (Tersedia: $available, Diminta: ${item.quantity})")
                )
            }
        }

        val totalAmount = cartItems.sumOf { it.subtotal }
        val totalItemsCount = cartItems.sumOf { it.quantity }
        val totalSellerShare = cartItems.sumOf { it.totalSellerShare }
        val totalAdminProfit = cartItems.sumOf { it.totalAdminProfit }
        val changeGiven = (cashReceived - totalAmount).coerceAtLeast(0.0)

        val sdf = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault())
        val trxNumber = "TRX-${sdf.format(Date())}-${(100..999).random()}"

        val transaction = LapakTransaction(
            transactionNumber = trxNumber,
            timestamp = System.currentTimeMillis(),
            totalAmount = totalAmount,
            totalItemsCount = totalItemsCount,
            cashReceived = cashReceived,
            changeGiven = changeGiven,
            paymentMethod = paymentMethod,
            totalAdminProfit = totalAdminProfit,
            totalSellerShare = totalSellerShare
        )

        val trxId = dao.insertTransaction(transaction)
        val savedTrx = transaction.copy(id = trxId)

        val trxItems = mutableListOf<TransactionItem>()

        // Deduct stock from the seller's specific stock lots FIFO
        for (item in cartItems) {
            var neededQty = item.quantity
            val activeLots = dao.getActiveStockLotsForProduct(item.product.id)

            for (lot in activeLots) {
                if (neededQty <= 0) break
                val deduct = minOf(lot.currentQty, neededQty)
                val newQty = lot.currentQty - deduct
                dao.updateStockLot(lot.copy(currentQty = newQty))
                neededQty -= deduct

                val finalProductStock = dao.getProductTotalStock(item.product.id)
                dao.insertMovement(
                    StockMovement(
                        productId = item.product.id,
                        sellerId = item.seller.id,
                        stockLotId = lot.id,
                        type = StockMovementType.PENJUALAN,
                        quantityChange = -deduct,
                        finalStock = finalProductStock,
                        reasonOrNote = "Penjualan No: $trxNumber"
                    )
                )
            }

            trxItems.add(
                TransactionItem(
                    transactionId = trxId,
                    productId = item.product.id,
                    sellerId = item.seller.id,
                    stockLotId = item.stockLot.id,
                    productName = item.product.name,
                    sellerName = item.seller.name,
                    unit = item.product.unit,
                    unitPrice = item.product.sellingPrice,
                    sellerSharePerUnit = item.product.sellerShare,
                    adminProfitPerUnit = item.product.sellingPrice - item.product.sellerShare,
                    quantity = item.quantity,
                    subtotal = item.subtotal,
                    totalSellerShare = item.totalSellerShare,
                    totalAdminProfit = item.totalAdminProfit
                )
            )
        }

        dao.insertTransactionItems(trxItems)
        Result.success(savedTrx)
    }

    // Cancel Transaction (Return stock to original seller & adjust rights)
    suspend fun cancelTransaction(transactionId: Long, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        val trx = dao.getTransactionById(transactionId) ?: return@withContext Result.failure(Exception("Transaksi tidak ditemukan"))
        if (trx.isCancelled) {
            return@withContext Result.failure(Exception("Transaksi sudah dibatalkan sebelumnya"))
        }

        val items = dao.getItemsForTransaction(transactionId)

        // Restore stock to seller lots
        for (item in items) {
            // Find active or latest lot for this product and seller
            val lots = dao.getActiveStockLotsForProduct(item.productId)
            val targetLot = lots.firstOrNull { it.sellerId == item.sellerId }
                ?: dao.getAllStockLotsForProductFlow(item.productId).first().firstOrNull { it.sellerId == item.sellerId }

            if (targetLot != null) {
                val restoredQty = targetLot.currentQty + item.quantity
                dao.updateStockLot(targetLot.copy(currentQty = restoredQty))
            } else {
                // If no lot exists, recreate one
                dao.insertStockLot(
                    StockLot(
                        productId = item.productId,
                        sellerId = item.sellerId,
                        initialQty = item.quantity,
                        currentQty = item.quantity,
                        unit = item.unit,
                        sellingPrice = item.unitPrice,
                        sellerShare = item.sellerSharePerUnit,
                        notes = "Restorasi stok dari pembatalan transaksi ${trx.transactionNumber}"
                    )
                )
            }

            val finalStock = dao.getProductTotalStock(item.productId)
            dao.insertMovement(
                StockMovement(
                    productId = item.productId,
                    sellerId = item.sellerId,
                    type = StockMovementType.PENGEMBALIAN,
                    quantityChange = item.quantity,
                    finalStock = finalStock,
                    reasonOrNote = "Pembatalan TRX ${trx.transactionNumber}: $reason"
                )
            )
        }

        val updatedTrx = trx.copy(
            isCancelled = true,
            cancellationReason = reason,
            cancelledAt = System.currentTimeMillis()
        )
        dao.updateTransaction(updatedTrx)

        Result.success(Unit)
    }

    // Transactions list & items
    val allTransactions: Flow<List<LapakTransaction>> = dao.getAllTransactionsFlow()

    val allTransactionItems: Flow<List<TransactionItem>> = dao.getAllTransactionItemsFlow()

    fun getItemsForTransaction(transactionId: Long): Flow<List<TransactionItem>> =
        dao.getItemsForTransactionFlow(transactionId)

    suspend fun getTransactionById(transactionId: Long): LapakTransaction? = withContext(Dispatchers.IO) {
        dao.getTransactionById(transactionId)
    }

    suspend fun getItemsForTransactionDirect(transactionId: Long): List<TransactionItem> = withContext(Dispatchers.IO) {
        dao.getItemsForTransaction(transactionId)
    }

    suspend fun getSoldItemsForSeller(sellerId: Long): List<TransactionItem> = withContext(Dispatchers.IO) {
        dao.getSoldItemsForSeller(sellerId)
    }

    // Seller Payments
    val allPayments: Flow<List<SellerPayment>> = dao.getAllPaymentsFlow()

    suspend fun recordSellerPayment(payment: SellerPayment): Long = withContext(Dispatchers.IO) {
        dao.insertPayment(payment)
    }

    suspend fun deleteSellerPayment(payment: SellerPayment) = withContext(Dispatchers.IO) {
        dao.deletePayment(payment)
    }

    // Operating Expenses
    val allExpenses: Flow<List<OperatingExpense>> = dao.getAllExpensesFlow()

    suspend fun addExpense(expense: OperatingExpense): Long = withContext(Dispatchers.IO) {
        dao.insertExpense(expense)
    }

    suspend fun deleteExpense(expense: OperatingExpense) = withContext(Dispatchers.IO) {
        dao.deleteExpense(expense)
    }

    // Stock Movements
    val allMovements: Flow<List<StockMovement>> = dao.getAllMovementsFlow()

    // Dashboard calculations based on selected timeframe
    fun getDashboardSummary(timeframe: String, customStart: Long? = null, customEnd: Long? = null): Flow<DashboardSummary> {
        return combine(
            inventoryData,
            financialData
        ) { inv, fin ->
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()

            val startTime: Long = when (timeframe) {
                "TODAY" -> {
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                "7_DAYS" -> now - (7 * 24 * 60 * 60 * 1000L)
                "THIS_MONTH" -> {
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    cal.timeInMillis
                }
                "CUSTOM" -> customStart ?: 0L
                else -> 0L // "ALL"
            }

            val endTime: Long = when (timeframe) {
                "CUSTOM" -> customEnd ?: now
                else -> Long.MAX_VALUE
            }

            // Filter non-cancelled transactions in range
            val periodTrx = fin.transactions.filter {
                !it.isCancelled && it.timestamp in startTime..endTime
            }
            val periodTrxIds = periodTrx.map { it.id }.toSet()
            val periodItems = fin.items.filter { it.transactionId in periodTrxIds }

            val totalOmzet = periodTrx.sumOf { it.totalAmount }
            val totalSellerShare = periodTrx.sumOf { it.totalSellerShare }
            val grossAdminProfit = periodTrx.sumOf { it.totalAdminProfit }
            val totalItemsSold = periodTrx.sumOf { it.totalItemsCount }

            val periodExpenses = fin.expenses.filter { it.expenseDate in startTime..endTime }.sumOf { it.amount }
            val netAdminProfit = grossAdminProfit - periodExpenses

            val activeSellers = inv.sellers.count { it.status == SellerStatus.AKTIF }
            val leaveSellers = inv.sellers.count { it.status == SellerStatus.LIBUR }
            val availableStock = inv.lots.sumOf { it.currentQty }

            // All-time unpaid rights
            val allValidTrxIds = fin.transactions.filter { !it.isCancelled }.map { it.id }.toSet()
            val allValidItems = fin.items.filter { it.transactionId in allValidTrxIds }
            val totalAllSellerRights = allValidItems.sumOf { it.totalSellerShare }
            val totalAllPayments = fin.payments.sumOf { it.amount }
            val totalUnpaid = (totalAllSellerRights - totalAllPayments).coerceAtLeast(0.0)

            // Top selling products
            val topMap = periodItems.groupBy { it.productId to it.productName }
                .map { (key, itemList) ->
                    TopProductSummary(
                        productName = key.second,
                        sellerName = itemList.firstOrNull()?.sellerName ?: "-",
                        soldQty = itemList.sumOf { it.quantity },
                        revenue = itemList.sumOf { it.subtotal },
                        adminProfit = itemList.sumOf { it.totalAdminProfit }
                    )
                }
                .sortedByDescending { it.soldQty }
                .take(5)

            // Low stock warning (stock <= 5)
            val lotsByProd = inv.lots.groupBy { it.productId }
            val sellerMap = inv.sellers.associateBy { it.id }

            val lowStock = inv.products.mapNotNull { prod ->
                val stock = lotsByProd[prod.id]?.sumOf { it.currentQty } ?: 0
                if (stock <= 5) {
                    LowStockItem(
                        product = prod,
                        sellerName = sellerMap[prod.sellerId]?.name ?: "-",
                        currentStock = stock
                    )
                } else null
            }.sortedBy { it.currentStock }.take(5)

            // Daily metric trend (last 7 days)
            val dayFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
            val dailyMap = mutableMapOf<String, DailyMetric>()

            for (i in 6 downTo 0) {
                val d = Calendar.getInstance()
                d.timeInMillis = now - (i * 24 * 60 * 60 * 1000L)
                val label = dayFormat.format(d.time)
                dailyMap[label] = DailyMetric(label, 0.0, 0.0, 0)
            }

            for (trx in periodTrx) {
                val label = dayFormat.format(Date(trx.timestamp))
                if (dailyMap.containsKey(label)) {
                    val curr = dailyMap[label]!!
                    dailyMap[label] = curr.copy(
                        revenue = curr.revenue + trx.totalAmount,
                        adminProfit = curr.adminProfit + trx.totalAdminProfit,
                        itemsCount = curr.itemsCount + trx.totalItemsCount
                    )
                }
            }

            DashboardSummary(
                totalOmzet = totalOmzet,
                totalSellerShare = totalSellerShare,
                grossAdminProfit = grossAdminProfit,
                operatingExpenses = periodExpenses,
                netAdminProfit = netAdminProfit,
                totalItemsSold = totalItemsSold,
                activeSellersCount = activeSellers,
                leaveSellersCount = leaveSellers,
                availableStockCount = availableStock,
                totalUnpaidSellerPayables = totalUnpaid,
                topSellingProducts = topMap,
                lowStockProducts = lowStock,
                dailyTrend = dailyMap.values.toList()
            )
        }
    }

    // Full JSON Backup Export & Import
    suspend fun exportFullBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("appName", "Lapak Kita")
        root.put("exportedAt", System.currentTimeMillis())

        // Shop Profile
        val profile = dao.getShopProfile() ?: ShopProfile()
        val profObj = JSONObject()
        profObj.put("shopName", profile.shopName)
        profObj.put("ownerName", profile.ownerName)
        profObj.put("whatsappNumber", profile.whatsappNumber)
        profObj.put("address", profile.address)
        profObj.put("footerMessage", profile.footerMessage)
        root.put("shopProfile", profObj)

        // Sellers
        val sellers = dao.getAllSellersDirect()
        val sellersArr = JSONArray()
        for (s in sellers) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("whatsappNumber", s.whatsappNumber)
            o.put("address", s.address)
            o.put("notes", s.notes)
            o.put("status", s.status.name)
            o.put("leaveStartDate", s.leaveStartDate ?: JSONObject.NULL)
            o.put("leaveEndDate", s.leaveEndDate ?: JSONObject.NULL)
            o.put("leaveReason", s.leaveReason ?: JSONObject.NULL)
            o.put("vendorCode", s.vendorCode)
            o.put("writeToken", s.writeToken)
            o.put("customAdminFeeType", s.customAdminFeeType ?: JSONObject.NULL)
            o.put("customAdminFeeValue", s.customAdminFeeValue ?: JSONObject.NULL)
            sellersArr.put(o)
        }
        root.put("sellers", sellersArr)

        // App Settings (Admin fee & theme)
        val settings = dao.getAppSettings() ?: AppSettings()
        val settingsObj = JSONObject()
        settingsObj.put("themeMode", settings.themeMode)
        settingsObj.put("language", settings.language)
        settingsObj.put("adminFeeType", settings.adminFeeType)
        settingsObj.put("adminFeeValue", settings.adminFeeValue)
        settingsObj.put("webPortalBaseUrl", settings.webPortalBaseUrl)
        root.put("appSettings", settingsObj)

        // Products
        val products = dao.getAllProductsDirect()
        val prodArr = JSONArray()
        for (p in products) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("sellerId", p.sellerId)
            o.put("name", p.name)
            o.put("category", p.category)
            o.put("unit", p.unit)
            o.put("sellingPrice", p.sellingPrice)
            o.put("sellerShare", p.sellerShare)
            o.put("adminProfitPerUnit", p.adminProfitPerUnit)
            o.put("isActive", p.isActive)
            prodArr.put(o)
        }
        root.put("products", prodArr)

        // Stock Lots
        val lots = dao.getAllStockLotsDirect()
        val lotArr = JSONArray()
        for (l in lots) {
            val o = JSONObject()
            o.put("id", l.id)
            o.put("productId", l.productId)
            o.put("sellerId", l.sellerId)
            o.put("initialQty", l.initialQty)
            o.put("currentQty", l.currentQty)
            o.put("unit", l.unit)
            o.put("sellingPrice", l.sellingPrice)
            o.put("sellerShare", l.sellerShare)
            o.put("notes", l.notes)
            o.put("depositDate", l.depositDate)
            lotArr.put(o)
        }
        root.put("stockLots", lotArr)

        // Transactions
        val trxs = dao.getAllTransactionsDirect()
        val trxArr = JSONArray()
        for (t in trxs) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("transactionNumber", t.transactionNumber)
            o.put("timestamp", t.timestamp)
            o.put("totalAmount", t.totalAmount)
            o.put("totalItemsCount", t.totalItemsCount)
            o.put("cashReceived", t.cashReceived)
            o.put("changeGiven", t.changeGiven)
            o.put("paymentMethod", t.paymentMethod)
            o.put("totalAdminProfit", t.totalAdminProfit)
            o.put("totalSellerShare", t.totalSellerShare)
            o.put("isCancelled", t.isCancelled)
            o.put("cancellationReason", t.cancellationReason ?: JSONObject.NULL)
            trxArr.put(o)
        }
        root.put("transactions", trxArr)

        // Transaction Items
        val items = dao.getAllTransactionItemsDirect()
        val itemArr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("transactionId", it.transactionId)
            o.put("productId", it.productId)
            o.put("sellerId", it.sellerId)
            o.put("productName", it.productName)
            o.put("sellerName", it.sellerName)
            o.put("unit", it.unit)
            o.put("unitPrice", it.unitPrice)
            o.put("sellerSharePerUnit", it.sellerSharePerUnit)
            o.put("adminProfitPerUnit", it.adminProfitPerUnit)
            o.put("quantity", it.quantity)
            o.put("subtotal", it.subtotal)
            o.put("totalSellerShare", it.totalSellerShare)
            o.put("totalAdminProfit", it.totalAdminProfit)
            itemArr.put(o)
        }
        root.put("transactionItems", itemArr)

        // Payments
        val payments = dao.getAllSellerPaymentsDirect()
        val payArr = JSONArray()
        for (p in payments) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("sellerId", p.sellerId)
            o.put("paymentDate", p.paymentDate)
            o.put("amount", p.amount)
            o.put("notes", p.notes)
            o.put("paymentMethod", p.paymentMethod)
            payArr.put(o)
        }
        root.put("sellerPayments", payArr)

        // Operating Expenses
        val expenses = dao.getAllOperatingExpensesDirect()
        val expArr = JSONArray()
        for (e in expenses) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("title", e.title)
            o.put("amount", e.amount)
            o.put("expenseDate", e.expenseDate)
            o.put("notes", e.notes)
            expArr.put(o)
        }
        root.put("operatingExpenses", expArr)

        root.toString(2)
    }

    suspend fun importFullBackupJson(jsonString: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("sellers") || !root.has("products")) {
                return@withContext Result.failure(Exception("Format file cadangan tidak valid!"))
            }

            // Restore Shop Profile
            if (root.has("shopProfile")) {
                val pObj = root.getJSONObject("shopProfile")
                dao.insertOrUpdateShopProfile(
                    ShopProfile(
                        id = 1,
                        shopName = pObj.optString("shopName", "Lapak Kita"),
                        ownerName = pObj.optString("ownerName", "Rafline Dev"),
                        whatsappNumber = pObj.optString("whatsappNumber", ""),
                        address = pObj.optString("address", ""),
                        footerMessage = pObj.optString("footerMessage", "")
                    )
                )
            }

            // Clear old data safely
            dao.clearTransactionItems()
            dao.clearTransactions()
            dao.clearStockMovements()
            dao.clearStockLots()
            dao.clearProducts()
            dao.clearSellerPayments()
            dao.clearSellerStatusHistory()
            dao.clearOperatingExpenses()
            dao.clearSellers()

            // Insert Sellers
            val sellersArr = root.getJSONArray("sellers")
            for (i in 0 until sellersArr.length()) {
                val o = sellersArr.getJSONObject(i)
                dao.insertSeller(
                    Seller(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        whatsappNumber = o.getString("whatsappNumber"),
                        address = o.optString("address", ""),
                        notes = o.optString("notes", ""),
                        status = SellerStatus.valueOf(o.optString("status", "AKTIF")),
                        leaveStartDate = if (o.isNull("leaveStartDate")) null else o.optLong("leaveStartDate"),
                        leaveEndDate = if (o.isNull("leaveEndDate")) null else o.optLong("leaveEndDate"),
                        leaveReason = if (o.isNull("leaveReason")) null else o.optString("leaveReason"),
                        vendorCode = o.optString("vendorCode", "").takeIf { VendorCodeGenerator.isValidVendorCode(it) }
                            ?: VendorCodeGenerator.generateVendorCode(),
                        writeToken = o.optString("writeToken", "").takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                            ?: VendorCodeGenerator.generateWriteToken(),
                        customAdminFeeType = if (o.isNull("customAdminFeeType")) null else o.optString("customAdminFeeType"),
                        customAdminFeeValue = if (o.isNull("customAdminFeeValue")) null else o.optDouble("customAdminFeeValue")
                    )
                )
            }

            if (root.has("appSettings")) {
                val sObj = root.getJSONObject("appSettings")
                val curr = dao.getAppSettings() ?: AppSettings()
                dao.insertOrUpdateAppSettings(
                    curr.copy(
                        themeMode = sObj.optString("themeMode", curr.themeMode),
                        language = sObj.optString("language", curr.language),
                        adminFeeType = sObj.optString("adminFeeType", curr.adminFeeType),
                        adminFeeValue = sObj.optDouble("adminFeeValue", curr.adminFeeValue),
                        webPortalBaseUrl = sObj.optString("webPortalBaseUrl", curr.webPortalBaseUrl)
                    )
                )
            }

            // Insert Products
            val prodArr = root.getJSONArray("products")
            for (i in 0 until prodArr.length()) {
                val o = prodArr.getJSONObject(i)
                dao.insertProduct(
                    Product(
                        id = o.getLong("id"),
                        sellerId = o.getLong("sellerId"),
                        name = o.getString("name"),
                        category = o.optString("category", "Makanan"),
                        unit = o.optString("unit", "pcs"),
                        sellingPrice = o.getDouble("sellingPrice"),
                        sellerShare = o.getDouble("sellerShare"),
                        adminProfitPerUnit = o.optDouble("adminProfitPerUnit", o.getDouble("sellingPrice") - o.getDouble("sellerShare")),
                        isActive = o.optBoolean("isActive", true)
                    )
                )
            }

            // Insert Stock Lots
            if (root.has("stockLots")) {
                val lotArr = root.getJSONArray("stockLots")
                for (i in 0 until lotArr.length()) {
                    val o = lotArr.getJSONObject(i)
                    dao.insertStockLot(
                        StockLot(
                            id = o.getLong("id"),
                            productId = o.getLong("productId"),
                            sellerId = o.getLong("sellerId"),
                            initialQty = o.getInt("initialQty"),
                            currentQty = o.getInt("currentQty"),
                            unit = o.optString("unit", "pcs"),
                            sellingPrice = o.getDouble("sellingPrice"),
                            sellerShare = o.getDouble("sellerShare"),
                            notes = o.optString("notes", ""),
                            depositDate = o.optLong("depositDate", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Insert Transactions & Items
            if (root.has("transactions")) {
                val trxArr = root.getJSONArray("transactions")
                for (i in 0 until trxArr.length()) {
                    val o = trxArr.getJSONObject(i)
                    dao.insertTransaction(
                        LapakTransaction(
                            id = o.getLong("id"),
                            transactionNumber = o.getString("transactionNumber"),
                            timestamp = o.getLong("timestamp"),
                            totalAmount = o.getDouble("totalAmount"),
                            totalItemsCount = o.getInt("totalItemsCount"),
                            cashReceived = o.getDouble("cashReceived"),
                            changeGiven = o.getDouble("changeGiven"),
                            paymentMethod = o.optString("paymentMethod", "Tunai"),
                            totalAdminProfit = o.getDouble("totalAdminProfit"),
                            totalSellerShare = o.getDouble("totalSellerShare"),
                            isCancelled = o.optBoolean("isCancelled", false),
                            cancellationReason = if (o.isNull("cancellationReason")) null else o.optString("cancellationReason")
                        )
                    )
                }
            }

            if (root.has("transactionItems")) {
                val itemArr = root.getJSONArray("transactionItems")
                val itemList = mutableListOf<TransactionItem>()
                for (i in 0 until itemArr.length()) {
                    val o = itemArr.getJSONObject(i)
                    itemList.add(
                        TransactionItem(
                            id = o.getLong("id"),
                            transactionId = o.getLong("transactionId"),
                            productId = o.getLong("productId"),
                            sellerId = o.getLong("sellerId"),
                            productName = o.getString("productName"),
                            sellerName = o.getString("sellerName"),
                            unit = o.optString("unit", "pcs"),
                            unitPrice = o.getDouble("unitPrice"),
                            sellerSharePerUnit = o.getDouble("sellerSharePerUnit"),
                            adminProfitPerUnit = o.getDouble("adminProfitPerUnit"),
                            quantity = o.getInt("quantity"),
                            subtotal = o.getDouble("subtotal"),
                            totalSellerShare = o.getDouble("totalSellerShare"),
                            totalAdminProfit = o.getDouble("totalAdminProfit")
                        )
                    )
                }
                dao.insertTransactionItems(itemList)
            }

            // Insert Payments
            if (root.has("sellerPayments")) {
                val payArr = root.getJSONArray("sellerPayments")
                for (i in 0 until payArr.length()) {
                    val o = payArr.getJSONObject(i)
                    dao.insertPayment(
                        SellerPayment(
                            id = o.getLong("id"),
                            sellerId = o.getLong("sellerId"),
                            paymentDate = o.getLong("paymentDate"),
                            amount = o.getDouble("amount"),
                            notes = o.optString("notes", ""),
                            paymentMethod = o.optString("paymentMethod", "Tunai")
                        )
                    )
                }
            }

            // Insert Operating Expenses
            if (root.has("operatingExpenses")) {
                val expArr = root.getJSONArray("operatingExpenses")
                for (i in 0 until expArr.length()) {
                    val o = expArr.getJSONObject(i)
                    dao.insertExpense(
                        OperatingExpense(
                            id = o.getLong("id"),
                            title = o.getString("title"),
                            amount = o.getDouble("amount"),
                            expenseDate = o.optLong("expenseDate", System.currentTimeMillis()),
                            notes = o.optString("notes", "")
                        )
                    )
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Cloud Financial Sync Snapshot & Cross-Device Merge
    suspend fun getFinancialSyncSnapshot(): LocalFinancialSyncSnapshot = withContext(Dispatchers.IO) {
        val sellers = dao.getAllSellersDirect()
        val transactions = dao.getAllTransactionsDirect().sortedBy { it.timestamp }
        val itemsByTrx = dao.getAllTransactionItemsDirect().groupBy { it.transactionId }
        val expenses = dao.getAllOperatingExpensesDirect().sortedBy { it.expenseDate }
        val payments = dao.getAllSellerPaymentsDirect().sortedBy { it.paymentDate }
        LocalFinancialSyncSnapshot(
            sellers = sellers,
            transactions = transactions,
            itemsByTransactionId = itemsByTrx,
            expenses = expenses,
            payments = payments
        )
    }

    private val cloudMergeMutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Real-time 2-HP merge for sellers, products, and live consignment stock from `users/{uid}/vendors`.
     * Skips self-echo writes from `currentDeviceId` when the local device already holds the same state,
     * and synchronizes sellers, product metadata, and target stock quantities cleanly onto the companion phone.
     */
    suspend fun mergeCloudVendorsCatalog(
        vendors: List<com.example.data.firebase.CloudVendorCatalogRecord>,
        currentDeviceId: String,
        ignoreSourceDeviceCheck: Boolean = false
    ): Int = withContext(Dispatchers.IO) {
        cloudMergeMutex.lock()
        try {
            var updatedCount = 0
            val existingSellers = dao.getAllSellersDirect().toMutableList()
            val sellerByCode = existingSellers
                .filter { it.vendorCode.isNotBlank() }
                .associateBy { it.vendorCode.trim().uppercase() }
                .toMutableMap()
            val sellerByName = existingSellers
                .associateBy { it.name.trim().lowercase() }
                .toMutableMap()

            for (cv in vendors) {
                val cleanCode = cv.vendorCode.trim().uppercase()
                val parsedStatus = runCatching { SellerStatus.valueOf(cv.sellerStatus.uppercase()) }
                    .getOrDefault(SellerStatus.AKTIF)

                val matchedSeller = sellerByCode[cleanCode] ?: sellerByName[cv.sellerName.trim().lowercase()]
                // Only skip self-echo when this vendor already exists locally; always restore missing vendors
                // (e.g. after app update, legacy cache cleanup, or re-login on the same phone).
                if (!ignoreSourceDeviceCheck && matchedSeller != null &&
                    cv.sourceDeviceId.isNotBlank() && cv.sourceDeviceId == currentDeviceId
                ) {
                    continue
                }
                val targetSeller = if (matchedSeller == null) {
                    val created = Seller(
                        name = cv.sellerName,
                        whatsappNumber = cv.whatsappNumber,
                        address = cv.address,
                        notes = cv.notes,
                        status = parsedStatus,
                        vendorCode = cleanCode,
                        writeToken = cv.writeToken.takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                            ?: VendorCodeGenerator.generateWriteToken(),
                        customAdminFeeType = cv.customAdminFeeType,
                        customAdminFeeValue = cv.customAdminFeeValue
                    )
                    val newId = dao.insertSeller(created)
                    val saved = created.copy(id = newId)
                    sellerByCode[cleanCode] = saved
                    sellerByName[saved.name.trim().lowercase()] = saved
                    updatedCount++
                    saved
                } else {
                    val needsUpdate = matchedSeller.name != cv.sellerName ||
                        matchedSeller.whatsappNumber != cv.whatsappNumber ||
                        matchedSeller.address != cv.address ||
                        matchedSeller.notes != cv.notes ||
                        matchedSeller.status != parsedStatus ||
                        matchedSeller.vendorCode != cleanCode ||
                        (cv.writeToken.isNotBlank() && matchedSeller.writeToken != cv.writeToken) ||
                        matchedSeller.customAdminFeeType != cv.customAdminFeeType ||
                        matchedSeller.customAdminFeeValue != cv.customAdminFeeValue
                    if (needsUpdate) {
                        val updated = matchedSeller.copy(
                            name = cv.sellerName,
                            whatsappNumber = cv.whatsappNumber,
                            address = cv.address,
                            notes = cv.notes,
                            status = parsedStatus,
                            vendorCode = cleanCode,
                            writeToken = cv.writeToken.takeIf { VendorCodeGenerator.isValidWriteToken(it) }
                                ?: matchedSeller.writeToken,
                            customAdminFeeType = cv.customAdminFeeType,
                            customAdminFeeValue = cv.customAdminFeeValue
                        )
                        dao.updateSeller(updated)
                        sellerByCode[cleanCode] = updated
                        sellerByName[updated.name.trim().lowercase()] = updated
                        updatedCount++
                        updated
                    } else {
                        matchedSeller
                    }
                }

                // Sync products & live stock levels for this seller
                val existingProducts = dao.getProductsBySellerDirect(targetSeller.id)
                    .associateBy { it.name.trim().lowercase() }
                    .toMutableMap()

                for (cp in cv.products) {
                    val normName = cp.name.trim().lowercase()
                    val existingProd = existingProducts[normName]
                    val targetProd = if (existingProd == null) {
                        val newProd = Product(
                            sellerId = targetSeller.id,
                            name = cp.name,
                            category = cp.category,
                            unit = cp.unit,
                            sellingPrice = cp.sellingPrice,
                            sellerShare = cp.sellerShare,
                            adminProfitPerUnit = cp.adminProfitPerUnit,
                            isActive = cp.isActive
                        )
                        val newProdId = dao.insertProduct(newProd)
                        val savedProd = newProd.copy(id = newProdId)
                        existingProducts[normName] = savedProd
                        updatedCount++
                        savedProd
                    } else {
                        val prodChanged = existingProd.category != cp.category ||
                            existingProd.unit != cp.unit ||
                            existingProd.sellingPrice != cp.sellingPrice ||
                            existingProd.sellerShare != cp.sellerShare ||
                            existingProd.adminProfitPerUnit != cp.adminProfitPerUnit ||
                            existingProd.isActive != cp.isActive
                        if (prodChanged) {
                            val updatedProd = existingProd.copy(
                                category = cp.category,
                                unit = cp.unit,
                                sellingPrice = cp.sellingPrice,
                                sellerShare = cp.sellerShare,
                                adminProfitPerUnit = cp.adminProfitPerUnit,
                                isActive = cp.isActive
                            )
                            dao.updateProduct(updatedProd)
                            existingProducts[normName] = updatedProd
                            updatedCount++
                            updatedProd
                        } else {
                            existingProd
                        }
                    }

                    // Reconcile live stock quantity to match cloud snapshot
                    val currentLocalStock = dao.getProductTotalStock(targetProd.id)
                    val targetStock = cp.currentStock.coerceAtLeast(0)
                    if (currentLocalStock != targetStock) {
                        val activeLots = dao.getActiveStockLotsForProduct(targetProd.id)
                        if (targetStock == 0) {
                            for (lot in activeLots) {
                                dao.updateStockLot(lot.copy(currentQty = 0))
                            }
                        } else if (targetStock > currentLocalStock) {
                            val diff = targetStock - currentLocalStock
                            dao.insertStockLot(
                                StockLot(
                                    productId = targetProd.id,
                                    sellerId = targetSeller.id,
                                    initialQty = diff,
                                    currentQty = diff,
                                    unit = targetProd.unit,
                                    sellingPrice = targetProd.sellingPrice,
                                    sellerShare = targetProd.sellerShare,
                                    notes = "Sinkronisasi real-time antar HP"
                                )
                            )
                        } else {
                            // Reduce FIFO across active lots to match sales/adjustments on companion HP
                            var toDeduct = currentLocalStock - targetStock
                            for (lot in activeLots) {
                                if (toDeduct <= 0) break
                                val take = minOf(lot.currentQty, toDeduct)
                                dao.updateStockLot(lot.copy(currentQty = (lot.currentQty - take).coerceAtLeast(0)))
                                toDeduct -= take
                            }
                        }
                        updatedCount++
                    }
                }
            }
            updatedCount
        } finally {
            cloudMergeMutex.unlock()
        }
    }

    suspend fun mergeCloudTransactions(
        transactions: List<com.example.data.firebase.CloudTransactionRecord>
    ): Int = mergeCloudFinancialData(
        com.example.data.firebase.CloudFinancialBundle(
            transactions = transactions,
            expenses = emptyList(),
            settlements = emptyList()
        )
    )

    suspend fun mergeCloudExpenses(
        expenses: List<com.example.data.firebase.CloudExpenseRecord>
    ): Int = mergeCloudFinancialData(
        com.example.data.firebase.CloudFinancialBundle(
            transactions = emptyList(),
            expenses = expenses,
            settlements = emptyList()
        )
    )

    suspend fun mergeCloudSettlements(
        settlements: List<com.example.data.firebase.CloudSettlementRecord>
    ): Int = mergeCloudFinancialData(
        com.example.data.firebase.CloudFinancialBundle(
            transactions = emptyList(),
            expenses = emptyList(),
            settlements = settlements
        )
    )

    /**
     * Merges financial records pulled from Firebase Firestore into local Room DB without duplicating
     * existing records. Ensures opening the app on a brand-new device or companion phone restores
     * and live-updates chronological sales transactions, operating expenses, and seller settlements.
     */
    suspend fun mergeCloudFinancialData(
        bundle: com.example.data.firebase.CloudFinancialBundle
    ): Int = withContext(Dispatchers.IO) {
        cloudMergeMutex.lock()
        try {
            var importedCount = 0

            val sellers = dao.getAllSellersDirect().toMutableList()
            val sellerByCode = sellers.filter { it.vendorCode.isNotBlank() }
                .associateBy { it.vendorCode.trim().uppercase() }
                .toMutableMap()
            val sellerByName = sellers.associateBy { it.name.trim().lowercase() }.toMutableMap()

            suspend fun resolveOrCreateSellerId(sellerName: String, vendorCode: String = ""): Long {
                val cleanCode = vendorCode.trim().uppercase()
                if (cleanCode.isNotBlank()) {
                    sellerByCode[cleanCode]?.let { return it.id }
                }
                val cleanName = sellerName.trim().ifEmpty { "Penyetor" }
                val normName = cleanName.lowercase()
                sellerByName[normName]?.let { return it.id }

                val newSeller = Seller(
                    name = cleanName,
                    whatsappNumber = "",
                    status = SellerStatus.AKTIF,
                    vendorCode = cleanCode.takeIf { VendorCodeGenerator.isValidVendorCode(it) }
                        ?: VendorCodeGenerator.generateVendorCode(),
                    writeToken = VendorCodeGenerator.generateWriteToken()
                )
                val newId = dao.insertSeller(newSeller)
                val saved = newSeller.copy(id = newId)
                sellers.add(saved)
                sellerByCode[saved.vendorCode.uppercase()] = saved
                sellerByName[normName] = saved
                return newId
            }

            // 1. Merge Transactions chronologically
            val existingTrxByNumber = dao.getAllTransactionsDirect()
                .associateBy { it.transactionNumber.trim() }
                .toMutableMap()

            for (cloudTrx in bundle.transactions.sortedBy { it.timestampMillis }) {
                val existing = existingTrxByNumber[cloudTrx.transactionNumber.trim()]
                if (existing == null) {
                    val insertedTrxId = dao.insertTransaction(
                        LapakTransaction(
                            transactionNumber = cloudTrx.transactionNumber,
                            timestamp = cloudTrx.timestampMillis,
                            totalAmount = cloudTrx.totalAmount,
                            totalItemsCount = cloudTrx.totalItemsCount,
                            cashReceived = cloudTrx.cashReceived,
                            changeGiven = cloudTrx.changeGiven,
                            paymentMethod = cloudTrx.paymentMethod,
                            totalAdminProfit = cloudTrx.totalAdminProfit,
                            totalSellerShare = cloudTrx.totalSellerShare,
                            isCancelled = cloudTrx.cancelled,
                            cancellationReason = cloudTrx.cancellationReason.ifBlank { null }
                        )
                    )
                    if (cloudTrx.items.isNotEmpty()) {
                        val localItems = cloudTrx.items.map { ci ->
                            val resolvedSellerId = resolveOrCreateSellerId(ci.sellerName)
                            TransactionItem(
                                transactionId = insertedTrxId,
                                productId = 0L,
                                sellerId = resolvedSellerId,
                                stockLotId = 0L,
                                productName = ci.productName,
                                sellerName = ci.sellerName,
                                unit = ci.unit,
                                unitPrice = ci.unitPrice,
                                sellerSharePerUnit = ci.sellerSharePerUnit,
                                adminProfitPerUnit = ci.adminProfitPerUnit,
                                quantity = ci.quantity,
                                subtotal = ci.subtotal,
                                totalSellerShare = ci.totalSellerShare,
                                totalAdminProfit = ci.totalAdminProfit
                            )
                        }
                        dao.insertTransactionItems(localItems)
                    }
                    importedCount++
                } else if (!existing.isCancelled && cloudTrx.cancelled) {
                    dao.updateTransaction(
                        existing.copy(
                            isCancelled = true,
                            cancellationReason = cloudTrx.cancellationReason.ifBlank { existing.cancellationReason }
                        )
                    )
                    importedCount++
                }
            }

            // 2. Merge Operating Expenses chronologically
            val existingExpenseKeys = dao.getAllOperatingExpensesDirect()
                .map { "${it.expenseDate}|${it.title.trim().lowercase()}|${it.amount.toLong()}" }
                .toMutableSet()

            for (cloudExp in bundle.expenses.sortedBy { it.expenseDateMillis }) {
                val key = "${cloudExp.expenseDateMillis}|${cloudExp.title.trim().lowercase()}|${cloudExp.amount.toLong()}"
                if (existingExpenseKeys.add(key)) {
                    dao.insertExpense(
                        OperatingExpense(
                            title = cloudExp.title,
                            amount = cloudExp.amount,
                            expenseDate = cloudExp.expenseDateMillis,
                            notes = cloudExp.notes
                        )
                    )
                    importedCount++
                }
            }

            // 3. Merge Seller Payout Settlements chronologically
            val existingPaymentKeys = dao.getAllSellerPaymentsDirect()
                .map { "${it.paymentDate}|${it.sellerId}|${it.amount.toLong()}" }
                .toMutableSet()

            for (cloudPay in bundle.settlements.sortedBy { it.paymentDateMillis }) {
                val resolvedSellerId = resolveOrCreateSellerId(cloudPay.sellerName, cloudPay.vendorCode)
                val key = "${cloudPay.paymentDateMillis}|$resolvedSellerId|${cloudPay.amount.toLong()}"
                if (existingPaymentKeys.add(key)) {
                    dao.insertPayment(
                        SellerPayment(
                            sellerId = resolvedSellerId,
                            paymentDate = cloudPay.paymentDateMillis,
                            amount = cloudPay.amount,
                            notes = cloudPay.notes,
                            paymentMethod = cloudPay.paymentMethod
                        )
                    )
                    importedCount++
                }
            }

            importedCount
        } finally {
            cloudMergeMutex.unlock()
        }
    }
}
