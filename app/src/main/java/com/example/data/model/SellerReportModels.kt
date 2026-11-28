package com.example.data.model

import org.json.JSONArray
import kotlin.math.abs

data class SellerProductReportRow(
    val productId: Long,
    val productName: String,
    val unit: String,
    val qtyIn: Int,
    val qtySold: Int,
    val qtyLeftover: Int,
    val sellingPrice: Double,
    val totalCommission: Double,
    val totalSellerShare: Double,
    val totalOmzet: Double
)

data class SellerPeriodSection(
    val sellerId: Long,
    val sellerName: String,
    val vendorCode: String,
    val whatsappNumber: String,
    val sellerStatus: SellerStatus,
    val tariffLabel: String,
    val productRows: List<SellerProductReportRow>,
    val totalQtyIn: Int,
    val totalQtySold: Int,
    val totalQtyLeftover: Int,
    val totalOmzet: Double,
    val totalCommission: Double,
    val totalSellerShare: Double,
    val totalPaid: Double,
    val remainingBalance: Double,
    val paymentStatus: String
)

data class SellerPeriodReportSnapshot(
    val periodLabel: String,
    val startMillis: Long,
    val endMillis: Long,
    val generatedAtMillis: Long,
    val scopeLabel: String,
    val isSingleSeller: Boolean,
    val sections: List<SellerPeriodSection>,
    val grandQtyIn: Int,
    val grandQtySold: Int,
    val grandQtyLeftover: Int,
    val grandOmzet: Double,
    val grandCommission: Double,
    val grandSellerShare: Double,
    val grandPaid: Double,
    val grandRemaining: Double
) {
    fun filterBySeller(sellerId: Long?): SellerPeriodReportSnapshot {
        if (sellerId == null) return this
        val filtered = sections.filter { it.sellerId == sellerId }
        val targetName = filtered.firstOrNull()?.sellerName ?: "Penyetor"
        return copy(
            scopeLabel = targetName,
            isSingleSeller = true,
            sections = filtered,
            grandQtyIn = filtered.sumOf { it.totalQtyIn },
            grandQtySold = filtered.sumOf { it.totalQtySold },
            grandQtyLeftover = filtered.sumOf { it.totalQtyLeftover },
            grandOmzet = filtered.sumOf { it.totalOmzet },
            grandCommission = filtered.sumOf { it.totalCommission },
            grandSellerShare = filtered.sumOf { it.totalSellerShare },
            grandPaid = filtered.sumOf { it.totalPaid },
            grandRemaining = filtered.sumOf { it.remainingBalance }
        )
    }
}

object SellerPeriodReportBuilder {

    fun parseArchiveItems(jsonStr: String): List<DailyClosingItemRecord> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        return runCatching {
            val arr = JSONArray(jsonStr)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        DailyClosingItemRecord(
                            sellerId = obj.optLong("sellerId", 0L),
                            sellerName = obj.optString("sellerName", ""),
                            vendorCode = obj.optString("vendorCode", ""),
                            productId = obj.optLong("productId", 0L),
                            productName = obj.optString("productName", ""),
                            unit = obj.optString("unit", "pcs"),
                            qtyIn = obj.optInt("qtyIn", 0),
                            qtySold = obj.optInt("qtySold", 0),
                            qtyLeftover = obj.optInt("qtyLeftover", 0),
                            sellingPrice = obj.optDouble("sellingPrice", 0.0),
                            omzet = obj.optDouble("omzet", 0.0),
                            sellerShare = obj.optDouble("sellerShare", 0.0),
                            adminProfit = obj.optDouble("adminProfit", 0.0)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun build(
        filter: FinancialPeriodFilter,
        sellers: List<Seller>,
        products: List<Product>,
        stockLots: List<StockLot>,
        transactions: List<LapakTransaction>,
        transactionItems: List<TransactionItem>,
        payments: List<SellerPayment>,
        returnMovements: List<StockMovement>,
        dailyClosingArchives: List<DailyClosingArchive>,
        appSettings: AppSettings,
        sellerIdFilter: Long? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): SellerPeriodReportSnapshot {
        val (startMillis, endMillis, periodLabel) = FinancialReportBuilder.resolveDateBounds(filter, nowMillis)

        val todayDateStr = DailyClosingHelper.todayDateString(nowMillis)
        val (todayStart, _) = DailyClosingHelper.dayBoundsWib(todayDateStr)
        val periodIncludesToday = endMillis >= todayStart
        val isTodayClosed = dailyClosingArchives.any { it.closingDate == todayDateStr }

        val validTrxIds = transactions
            .filter { !it.isCancelled && it.timestamp in startMillis..endMillis }
            .map { it.id }
            .toSet()

        val periodSaleItems = transactionItems.filter { it.transactionId in validTrxIds }
        val periodPayments = payments.filter { it.paymentDate in startMillis..endMillis }
        val periodReturnMovements = returnMovements.filter {
            it.type == StockMovementType.PENGEMBALIAN && it.timestamp in startMillis..endMillis
        }

        val periodArchiveRecords = dailyClosingArchives
            .filter { archive ->
                val (aStart, _) = DailyClosingHelper.dayBoundsWib(archive.closingDate)
                aStart in startMillis..endMillis
            }
            .flatMap { parseArchiveItems(it.itemsBreakdownJson) }

        val targetSellers = if (sellerIdFilter != null) {
            sellers.filter { it.id == sellerIdFilter }
        } else {
            sellers.sortedBy { it.name.lowercase() }
        }

        val productsBySeller = products.groupBy { it.sellerId }
        val lotsByProduct = stockLots.groupBy { it.productId }

        val sections = targetSellers.map { seller ->
            val (feeType, feeValue) = AdminFeeCalculator.resolveEffectiveFee(seller, appSettings)
            val tariffLabel = AdminFeeCalculator.formatTariffLabel(feeType, feeValue)

            val sellerProducts = productsBySeller[seller.id].orEmpty().sortedBy { it.name.lowercase() }
            val normSellerName = seller.name.trim().lowercase()

            val sellerSaleItems = periodSaleItems.filter { item ->
                item.sellerId == seller.id ||
                    (item.sellerId == 0L && item.sellerName.trim().lowercase() == normSellerName)
            }
            val sellerArchiveItems = periodArchiveRecords.filter { rec ->
                (rec.sellerId > 0L && rec.sellerId == seller.id) ||
                    rec.sellerName.trim().lowercase() == normSellerName
            }
            val sellerReturnMovs = periodReturnMovements.filter { it.sellerId == seller.id }
            val sellerPayments = periodPayments.filter { it.sellerId == seller.id }

            val matchedSaleItemIds = mutableSetOf<Long>()
            val matchedArchiveKeys = mutableSetOf<String>()

            val productRows = mutableListOf<SellerProductReportRow>()

            for (product in sellerProducts) {
                val normProdName = product.name.trim().lowercase()
                val prodSales = sellerSaleItems.filter { item ->
                    (item.productId > 0L && item.productId == product.id) ||
                        (item.productId == 0L && item.productName.trim().lowercase() == normProdName)
                }
                prodSales.forEach { matchedSaleItemIds.add(it.id) }

                val prodArchives = sellerArchiveItems.filter { rec ->
                    (rec.productId > 0L && rec.productId == product.id) ||
                        rec.productName.trim().lowercase() == normProdName
                }
                if (prodArchives.isNotEmpty()) {
                    matchedArchiveKeys.add(normProdName)
                }

                val prodLots = lotsByProduct[product.id].orEmpty()
                val periodProdLots = prodLots.filter { it.depositDate in startMillis..endMillis }
                val lotsIn = periodProdLots.sumOf { it.initialQty.coerceAtLeast(0) }
                val archivedIn = prodArchives.sumOf { it.qtyIn.coerceAtLeast(0) }
                val archivedLeftover = prodArchives.sumOf { it.qtyLeftover.coerceAtLeast(0) }
                val movReturned = sellerReturnMovs
                    .filter { it.productId == product.id }
                    .sumOf { abs(it.quantityChange) }

                val closedLeftover = maxOf(archivedLeftover, movReturned)
                val unclosedCurrent = when {
                    periodIncludesToday && !isTodayClosed -> prodLots.sumOf { it.currentQty.coerceAtLeast(0) }
                    !periodIncludesToday && closedLeftover == 0 -> periodProdLots.sumOf { it.currentQty.coerceAtLeast(0) }
                    else -> 0
                }
                val qtySold = prodSales.sumOf { it.quantity.coerceAtLeast(0) }
                val qtyLeftover = closedLeftover + unclosedCurrent
                val qtyIn = maxOf(lotsIn, archivedIn, qtySold + qtyLeftover)

                val totalOmzet = prodSales.sumOf { it.subtotal }
                val totalCommission = prodSales.sumOf { it.totalAdminProfit }
                val totalSellerShare = prodSales.sumOf { it.totalSellerShare }
                val effectivePrice = if (qtySold > 0) {
                    prodSales.lastOrNull()?.unitPrice ?: product.sellingPrice
                } else {
                    product.sellingPrice
                }

                productRows.add(
                    SellerProductReportRow(
                        productId = product.id,
                        productName = product.name,
                        unit = product.unit,
                        qtyIn = qtyIn,
                        qtySold = qtySold,
                        qtyLeftover = qtyLeftover,
                        sellingPrice = effectivePrice,
                        totalCommission = totalCommission,
                        totalSellerShare = totalSellerShare,
                        totalOmzet = totalOmzet
                    )
                )
            }

            // Include historical / deleted products that had sales or archive entries in this period
            val unmatchedSales = sellerSaleItems.filter { it.id !in matchedSaleItemIds }
            val unmatchedSalesByName = unmatchedSales.groupBy { it.productName.trim().lowercase() }
            val unmatchedArchiveByName = sellerArchiveItems
                .filter { it.productName.trim().lowercase() !in matchedArchiveKeys }
                .groupBy { it.productName.trim().lowercase() }

            val extraProductKeys = (unmatchedSalesByName.keys + unmatchedArchiveByName.keys).distinct()
            for (normKey in extraProductKeys) {
                val salesGroup = unmatchedSalesByName[normKey].orEmpty()
                val archiveGroup = unmatchedArchiveByName[normKey].orEmpty()
                val displayName = salesGroup.firstOrNull()?.productName
                    ?: archiveGroup.firstOrNull()?.productName
                    ?: "Menu"
                val unit = salesGroup.firstOrNull()?.unit
                    ?: archiveGroup.firstOrNull()?.unit
                    ?: "pcs"
                val qtySold = salesGroup.sumOf { it.quantity.coerceAtLeast(0) }
                val qtyLeftover = archiveGroup.sumOf { it.qtyLeftover.coerceAtLeast(0) }
                val archivedIn = archiveGroup.sumOf { it.qtyIn.coerceAtLeast(0) }
                val qtyIn = maxOf(archivedIn, qtySold + qtyLeftover)
                val price = salesGroup.lastOrNull()?.unitPrice
                    ?: archiveGroup.lastOrNull()?.sellingPrice
                    ?: 0.0
                productRows.add(
                    SellerProductReportRow(
                        productId = salesGroup.firstOrNull()?.productId ?: 0L,
                        productName = displayName,
                        unit = unit,
                        qtyIn = qtyIn,
                        qtySold = qtySold,
                        qtyLeftover = qtyLeftover,
                        sellingPrice = price,
                        totalCommission = salesGroup.sumOf { it.totalAdminProfit },
                        totalSellerShare = salesGroup.sumOf { it.totalSellerShare },
                        totalOmzet = salesGroup.sumOf { it.subtotal }
                    )
                )
            }

            val sortedRows = productRows.sortedBy { it.productName.lowercase() }
            val totalQtyIn = sortedRows.sumOf { it.qtyIn }
            val totalQtySold = sortedRows.sumOf { it.qtySold }
            val totalQtyLeftover = sortedRows.sumOf { it.qtyLeftover }
            val totalOmzet = sortedRows.sumOf { it.totalOmzet }
            val totalCommission = sortedRows.sumOf { it.totalCommission }
            val totalSellerShare = sortedRows.sumOf { it.totalSellerShare }
            val totalPaid = sellerPayments.sumOf { it.amount }
            val remaining = (totalSellerShare - totalPaid).coerceAtLeast(0.0)
            val status = when {
                totalSellerShare <= 0.0 -> "Lunas"
                remaining <= 0.0 -> "Lunas"
                totalPaid > 0.0 -> "Dibayar Sebagian"
                else -> "Belum Dibayar"
            }

            SellerPeriodSection(
                sellerId = seller.id,
                sellerName = seller.name,
                vendorCode = seller.vendorCode,
                whatsappNumber = seller.whatsappNumber,
                sellerStatus = seller.status,
                tariffLabel = tariffLabel,
                productRows = sortedRows,
                totalQtyIn = totalQtyIn,
                totalQtySold = totalQtySold,
                totalQtyLeftover = totalQtyLeftover,
                totalOmzet = totalOmzet,
                totalCommission = totalCommission,
                totalSellerShare = totalSellerShare,
                totalPaid = totalPaid,
                remainingBalance = remaining,
                paymentStatus = status
            )
        }

        val scopeLabel = if (sellerIdFilter != null) {
            sections.firstOrNull()?.sellerName ?: "Penyetor"
        } else {
            "Semua Penyetor (${sections.size} Dapur)"
        }

        return SellerPeriodReportSnapshot(
            periodLabel = periodLabel,
            startMillis = startMillis,
            endMillis = endMillis,
            generatedAtMillis = nowMillis,
            scopeLabel = scopeLabel,
            isSingleSeller = sellerIdFilter != null,
            sections = sections,
            grandQtyIn = sections.sumOf { it.totalQtyIn },
            grandQtySold = sections.sumOf { it.totalQtySold },
            grandQtyLeftover = sections.sumOf { it.totalQtyLeftover },
            grandOmzet = sections.sumOf { it.totalOmzet },
            grandCommission = sections.sumOf { it.totalCommission },
            grandSellerShare = sections.sumOf { it.totalSellerShare },
            grandPaid = sections.sumOf { it.totalPaid },
            grandRemaining = sections.sumOf { it.remainingBalance }
        )
    }
}
