package com.example.data.model

import java.text.SimpleDateFormat
import java.util.*

enum class FinancialReportPeriod(val label: String) {
    TODAY("Hari Ini"),
    LAST_7_DAYS("7 Hari Terakhir"),
    THIS_MONTH("Bulan Ini"),
    CUSTOM_MONTH("Pilih Bulan"),
    CUSTOM_RANGE("Rentang Tanggal"),
    ALL_TIME("Semua Waktu")
}

data class FinancialPeriodFilter(
    val period: FinancialReportPeriod = FinancialReportPeriod.THIS_MONTH,
    val selectedYear: Int = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta")).get(Calendar.MONTH), // 0-based
    val customStartMillis: Long? = null,
    val customEndMillis: Long? = null
)

enum class FinancialLedgerEntryType {
    SALE_ITEM,
    OPERATING_EXPENSE
}

data class FinancialLedgerRow(
    val number: Int,
    val timestampMillis: Long,
    val type: FinancialLedgerEntryType,
    val description: String,
    val sellerName: String,
    val qty: Int,
    val omzetIn: Double,
    val sellerShare: Double,
    val adminCommission: Double,
    val operatingExpense: Double,
    val referenceCode: String = ""
)

data class SellerRecapRow(
    val number: Int,
    val sellerId: Long,
    val sellerName: String,
    val totalItemsSold: Int,
    val totalSellerShare: Double,
    val totalPaid: Double,
    val remainingUnpaid: Double,
    val status: String // "Lunas", "Dibayar Sebagian", "Belum Dibayar"
)

data class FinancialReportSnapshot(
    val periodLabel: String,
    val startMillis: Long,
    val endMillis: Long,
    val generatedAtMillis: Long,
    val totalGrossOmzet: Double,
    val totalSellerShare: Double,
    val totalAdminCommission: Double,
    val totalOperatingExpenses: Double,
    val netLapakProfit: Double,
    val totalItemsSold: Int,
    val totalTransactionsCount: Int,
    val ledgerRows: List<FinancialLedgerRow>,
    val sellerRecapRows: List<SellerRecapRow>,
    val profitMarginPercent: Double = 0.0,
    val totalSellerPaid: Double = 0.0,
    val totalSellerUnpaid: Double = 0.0,
    val totalItemsIn: Int = 0,
    val totalItemsLeftover: Int = 0,
    val sellThroughRatePercent: Double = 0.0,
    val topSellingProductName: String? = null,
    val topSellingProductQty: Int = 0,
    val topSellingSellerName: String? = null,
    val topSellingSellerQty: Int = 0,
    val topSellingSellerOmzet: Double = 0.0
)

object FinancialReportBuilder {

    private val wibTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val indonesianLocale = Locale("id", "ID")

    fun resolveDateBounds(filter: FinancialPeriodFilter, nowMillis: Long = System.currentTimeMillis()): Triple<Long, Long, String> {
        val dateFmt = SimpleDateFormat("dd MMM yyyy", indonesianLocale).apply { timeZone = wibTimeZone }
        val monthFmt = SimpleDateFormat("MMMM yyyy", indonesianLocale).apply { timeZone = wibTimeZone }

        return when (filter.period) {
            FinancialReportPeriod.TODAY -> {
                val startCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = startCal.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Triple(startCal.timeInMillis, endCal.timeInMillis, "Hari Ini (${dateFmt.format(startCal.time)})")
            }

            FinancialReportPeriod.LAST_7_DAYS -> {
                val startCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_MONTH, -6)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = nowMillis
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Triple(
                    startCal.timeInMillis,
                    endCal.timeInMillis,
                    "7 Hari Terakhir (${dateFmt.format(startCal.time)} – ${dateFmt.format(endCal.time)})"
                )
            }

            FinancialReportPeriod.THIS_MONTH -> {
                val startCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = nowMillis
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = startCal.timeInMillis
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Triple(startCal.timeInMillis, endCal.timeInMillis, "Bulan ${monthFmt.format(startCal.time)}")
            }

            FinancialReportPeriod.CUSTOM_MONTH -> {
                val startCal = Calendar.getInstance(wibTimeZone).apply {
                    set(Calendar.YEAR, filter.selectedYear)
                    set(Calendar.MONTH, filter.selectedMonth.coerceIn(0, 11))
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = startCal.timeInMillis
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Triple(startCal.timeInMillis, endCal.timeInMillis, "Bulan ${monthFmt.format(startCal.time)}")
            }

            FinancialReportPeriod.CUSTOM_RANGE -> {
                val rawStart = filter.customStartMillis ?: (nowMillis - 6L * 86_400_000L)
                val rawEnd = filter.customEndMillis ?: nowMillis
                val safeStart = minOf(rawStart, rawEnd)
                val safeEnd = maxOf(rawStart, rawEnd)
                val startCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = safeStart
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val endCal = Calendar.getInstance(wibTimeZone).apply {
                    timeInMillis = safeEnd
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
                Triple(
                    startCal.timeInMillis,
                    endCal.timeInMillis,
                    "${dateFmt.format(startCal.time)} – ${dateFmt.format(endCal.time)}"
                )
            }

            FinancialReportPeriod.ALL_TIME -> {
                Triple(0L, Long.MAX_VALUE, "Semua Waktu")
            }
        }
    }

    /**
     * Builds a complete FinancialReportSnapshot with strictly ASCENDING chronological order
     * (oldest date & time at row 1 -> newest date & time at bottom row).
     */
    fun buildReport(
        filter: FinancialPeriodFilter,
        sellers: List<Seller>,
        transactions: List<LapakTransaction>,
        transactionItems: List<TransactionItem>,
        expenses: List<OperatingExpense>,
        payments: List<SellerPayment>,
        stockLots: List<StockLot> = emptyList(),
        returnMovements: List<StockMovement> = emptyList(),
        dailyClosingArchives: List<DailyClosingArchive> = emptyList(),
        nowMillis: Long = System.currentTimeMillis()
    ): FinancialReportSnapshot {
        val (startMillis, endMillis, periodLabel) = resolveDateBounds(filter, nowMillis)

        // Filter non-cancelled transactions within period, sorted strictly ASCENDING
        val periodTransactions = transactions
            .filter { !it.isCancelled && it.timestamp in startMillis..endMillis }
            .sortedWith(compareBy<LapakTransaction> { it.timestamp }.thenBy { it.id })

        val trxMap = periodTransactions.associateBy { it.id }
        val itemsByTrxId = transactionItems
            .filter { it.transactionId in trxMap }
            .groupBy { it.transactionId }

        // Filter operating expenses within period, sorted strictly ASCENDING
        val periodExpenses = expenses
            .filter { it.expenseDate in startMillis..endMillis }
            .sortedWith(compareBy<OperatingExpense> { it.expenseDate }.thenBy { it.id })

        // Filter seller payments within period
        val periodPayments = payments
            .filter { it.paymentDate in startMillis..endMillis }

        // Build chronological ledger entries (oldest -> newest)
        val rawLedger = mutableListOf<Pair<Long, FinancialLedgerRow>>()

        for (trx in periodTransactions) {
            val items = itemsByTrxId[trx.id].orEmpty().sortedBy { it.id }
            if (items.isEmpty()) {
                rawLedger.add(
                    trx.timestamp to FinancialLedgerRow(
                        number = 0,
                        timestampMillis = trx.timestamp,
                        type = FinancialLedgerEntryType.SALE_ITEM,
                        description = "Penjualan (${trx.transactionNumber})",
                        sellerName = "-",
                        qty = trx.totalItemsCount,
                        omzetIn = trx.totalAmount,
                        sellerShare = trx.totalSellerShare,
                        adminCommission = trx.totalAdminProfit,
                        operatingExpense = 0.0,
                        referenceCode = trx.transactionNumber
                    )
                )
            } else {
                for (item in items) {
                    rawLedger.add(
                        trx.timestamp to FinancialLedgerRow(
                            number = 0,
                            timestampMillis = trx.timestamp,
                            type = FinancialLedgerEntryType.SALE_ITEM,
                            description = "${item.productName} (${trx.transactionNumber})",
                            sellerName = item.sellerName,
                            qty = item.quantity,
                            omzetIn = item.subtotal,
                            sellerShare = item.totalSellerShare,
                            adminCommission = item.totalAdminProfit,
                            operatingExpense = 0.0,
                            referenceCode = trx.transactionNumber
                        )
                    )
                }
            }
        }

        for (exp in periodExpenses) {
            val desc = buildString {
                append("[Biaya] ${exp.title}")
                if (exp.notes.isNotBlank()) append(" — ${exp.notes}")
            }
            rawLedger.add(
                exp.expenseDate to FinancialLedgerRow(
                    number = 0,
                    timestampMillis = exp.expenseDate,
                    type = FinancialLedgerEntryType.OPERATING_EXPENSE,
                    description = desc,
                    sellerName = "-",
                    qty = 0,
                    omzetIn = 0.0,
                    sellerShare = 0.0,
                    adminCommission = 0.0,
                    operatingExpense = exp.amount,
                    referenceCode = "EXP-${exp.id}"
                )
            )
        }

        // Strict chronological ASCENDING sort (oldest top -> newest bottom)
        val sortedLedger = rawLedger
            .sortedWith(
                compareBy<Pair<Long, FinancialLedgerRow>> { it.first }
                    .thenBy { if (it.second.type == FinancialLedgerEntryType.SALE_ITEM) 0 else 1 }
            )
            .mapIndexed { idx, (_, row) -> row.copy(number = idx + 1) }

        val totalGrossOmzet = periodTransactions.sumOf { it.totalAmount }
        val totalSellerShare = periodTransactions.sumOf { it.totalSellerShare }
        val totalAdminCommission = periodTransactions.sumOf { it.totalAdminProfit }
        val totalOperatingExpenses = periodExpenses.sumOf { it.amount }
        val netLapakProfit = totalAdminCommission - totalOperatingExpenses
        val totalItemsSold = periodTransactions.sumOf { it.totalItemsCount }

        // Build Seller Payout Recap for the selected period (merging by sellerId or sellerName)
        val sellerById = sellers.associateBy { it.id }
        val allPeriodItems = periodTransactions.flatMap { trx -> itemsByTrxId[trx.id].orEmpty() }

        val sellerIdsWithActivity = (
            allPeriodItems.map { it.sellerId } +
                periodPayments.map { it.sellerId } +
                sellers.filter { it.status == SellerStatus.AKTIF }.map { it.id }
            ).distinct()

        val recapList = sellerIdsWithActivity.mapNotNull { sellerId ->
            val sItems = allPeriodItems.filter { it.sellerId == sellerId }
            val sPayments = periodPayments.filter { it.sellerId == sellerId }
            val sellerObj = sellerById[sellerId]
            val sellerName = sellerObj?.name
                ?: sItems.firstOrNull()?.sellerName
                ?: return@mapNotNull null

            val soldQty = sItems.sumOf { it.quantity }
            val totalRights = sItems.sumOf { it.totalSellerShare }
            val totalPaid = sPayments.sumOf { it.amount }

            // Skip completely inactive zero-row sellers if they have no sales and no payments in this period
            if (soldQty == 0 && totalRights <= 0.0 && totalPaid <= 0.0) return@mapNotNull null

            val remaining = (totalRights - totalPaid).coerceAtLeast(0.0)
            val status = when {
                totalRights <= 0.0 -> "Lunas"
                remaining <= 0.0 -> "Lunas"
                totalPaid > 0.0 -> "Dibayar Sebagian"
                else -> "Belum Dibayar"
            }
            SellerRecapRow(
                number = 0,
                sellerId = sellerId,
                sellerName = sellerName,
                totalItemsSold = soldQty,
                totalSellerShare = totalRights,
                totalPaid = totalPaid,
                remainingUnpaid = remaining,
                status = status
            )
        }
            .sortedBy { it.sellerName.lowercase() }
            .mapIndexed { idx, row -> row.copy(number = idx + 1) }

        // Profit margin (%) relative to gross omzet
        val profitMarginPercent = if (totalGrossOmzet > 0.0) {
            (netLapakProfit / totalGrossOmzet) * 100.0
        } else {
            0.0
        }

        // Payout settlement totals across the period
        val totalSellerPaid = periodPayments.sumOf { it.amount }
        val totalSellerUnpaid = recapList.sumOf { it.remainingUnpaid }

        // Stock In, Leftover/Return, and Sell-through Ratio (%)
        val todayDateStr = DailyClosingHelper.todayDateString(nowMillis)
        val (todayStart, _) = DailyClosingHelper.dayBoundsWib(todayDateStr)
        val periodIncludesToday = endMillis >= todayStart
        val isTodayClosed = dailyClosingArchives.any { it.closingDate == todayDateStr }

        val periodArchives = dailyClosingArchives.filter { archive ->
            val (aStart, _) = DailyClosingHelper.dayBoundsWib(archive.closingDate)
            aStart in startMillis..endMillis
        }
        val archivedIn = periodArchives.sumOf { it.totalItemsIn.coerceAtLeast(0) }
        val archivedLeftover = periodArchives.sumOf { it.totalItemsLeftover.coerceAtLeast(0) }

        val periodLots = stockLots.filter { it.depositDate in startMillis..endMillis }
        val lotsIn = periodLots.sumOf { it.initialQty.coerceAtLeast(0) }
        val movementReturned = returnMovements
            .filter { it.type == StockMovementType.PENGEMBALIAN && it.timestamp in startMillis..endMillis }
            .sumOf { kotlin.math.abs(it.quantityChange) }

        val closedLeftover = maxOf(archivedLeftover, movementReturned)
        val unclosedCurrentStock = when {
            periodIncludesToday && !isTodayClosed -> stockLots.sumOf { it.currentQty.coerceAtLeast(0) }
            !periodIncludesToday && closedLeftover == 0 -> periodLots.sumOf { it.currentQty.coerceAtLeast(0) }
            else -> 0
        }
        val totalItemsLeftover = closedLeftover + unclosedCurrentStock
        val totalItemsIn = maxOf(lotsIn, archivedIn, totalItemsSold + totalItemsLeftover)
        val sellThroughRatePercent = when {
            totalItemsIn > 0 -> ((totalItemsSold.toDouble() / totalItemsIn.toDouble()) * 100.0).coerceIn(0.0, 100.0)
            totalItemsSold > 0 -> 100.0
            else -> 0.0
        }

        // Top-selling menu & top-selling vendor in this period
        val topProductEntry = allPeriodItems
            .groupBy { it.productName.trim().ifBlank { "Produk" } }
            .mapValues { (_, list) -> list.sumOf { it.quantity.coerceAtLeast(0) } to list.sumOf { it.subtotal } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Pair<Int, Double>>> { it.value.first }.thenByDescending { it.value.second })
            .firstOrNull()

        val topSellerEntry = allPeriodItems
            .groupBy { item ->
                sellerById[item.sellerId]?.name?.takeIf { it.isNotBlank() }
                    ?: item.sellerName.trim().ifBlank { "Penyetor" }
            }
            .mapValues { (_, list) -> list.sumOf { it.quantity.coerceAtLeast(0) } to list.sumOf { it.subtotal } }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Pair<Int, Double>>> { it.value.first }.thenByDescending { it.value.second })
            .firstOrNull()

        return FinancialReportSnapshot(
            periodLabel = periodLabel,
            startMillis = startMillis,
            endMillis = endMillis,
            generatedAtMillis = nowMillis,
            totalGrossOmzet = totalGrossOmzet,
            totalSellerShare = totalSellerShare,
            totalAdminCommission = totalAdminCommission,
            totalOperatingExpenses = totalOperatingExpenses,
            netLapakProfit = netLapakProfit,
            totalItemsSold = totalItemsSold,
            totalTransactionsCount = periodTransactions.size,
            ledgerRows = sortedLedger,
            sellerRecapRows = recapList,
            profitMarginPercent = profitMarginPercent,
            totalSellerPaid = totalSellerPaid,
            totalSellerUnpaid = totalSellerUnpaid,
            totalItemsIn = totalItemsIn,
            totalItemsLeftover = totalItemsLeftover,
            sellThroughRatePercent = sellThroughRatePercent,
            topSellingProductName = topProductEntry?.key,
            topSellingProductQty = topProductEntry?.value?.first ?: 0,
            topSellingSellerName = topSellerEntry?.key,
            topSellingSellerQty = topSellerEntry?.value?.first ?: 0,
            topSellingSellerOmzet = topSellerEntry?.value?.second ?: 0.0
        )
    }
}
