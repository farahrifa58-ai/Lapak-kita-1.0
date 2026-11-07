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

    // Sellers
    val allSellers: Flow<List<Seller>> = dao.getAllSellersFlow()

    suspend fun addSeller(seller: Seller): Long = withContext(Dispatchers.IO) {
        dao.insertSeller(seller)
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
        dao.updateProduct(product)
    }

    suspend fun deleteProduct(product: Product) = withContext(Dispatchers.IO) {
        dao.deleteProduct(product)
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

    fun getItemsForTransaction(transactionId: Long): Flow<List<TransactionItem>> =
        dao.getItemsForTransactionFlow(transactionId)

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
            sellersArr.put(o)
        }
        root.put("sellers", sellersArr)

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
                        leaveReason = if (o.isNull("leaveReason")) null else o.optString("leaveReason")
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
}
