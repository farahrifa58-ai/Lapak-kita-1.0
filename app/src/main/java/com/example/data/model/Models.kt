package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.SecureRandom
import kotlin.math.roundToLong

enum class SellerStatus {
    AKTIF,
    LIBUR,
    NONAKTIF
}

enum class StockMovementType {
    SETORAN_AWAL,
    SETORAN_TAMBAHAN,
    PENJUALAN,
    PENGEMBALIAN,
    KOREKSI
}

object AdminFeeType {
    const val FIXED = "FIXED"
    const val PERCENTAGE = "PERCENTAGE"
}

object ThemeMode {
    const val SYSTEM = "SYSTEM"
    const val LIGHT = "LIGHT"
    const val DARK = "DARK"
}

const val DEFAULT_WEB_PORTAL_BASE_URL = "https://lapakkita1.vercel.app"

fun sanitizeWebPortalBaseUrl(rawUrl: String?): String {
    val trimmed = rawUrl?.trim()?.trimEnd('/').orEmpty()
    if (trimmed.isEmpty()) return DEFAULT_WEB_PORTAL_BASE_URL
    val lower = trimmed.lowercase()
    if (lower.contains("ais-pre-") || lower.contains("ais-dev-") || lower.contains(".run.app")) {
        return DEFAULT_WEB_PORTAL_BASE_URL
    }
    return trimmed
}

object PosterTemplate {
    const val SOFT_EMERALD = "SOFT_EMERALD"
    const val WARM_CREAM = "WARM_CREAM"
    const val DARK_GOLD = "DARK_GOLD"
    const val FRESH_TERRACOTTA = "FRESH_TERRACOTTA"
    const val CUSTOM_IMAGE = "CUSTOM_IMAGE"
}

object ShopIconKey {
    const val STORE = "STORE"
    const val BAKERY = "BAKERY"
    const val CAFE = "CAFE"
    const val BASKET = "BASKET"
    const val RESTAURANT = "RESTAURANT"
    const val CUSTOM_PHOTO = "CUSTOM_PHOTO"
}

object HeaderBgColorKey {
    const val SOFT_EMERALD = "SOFT_EMERALD"
    const val OCEAN_TEAL = "OCEAN_TEAL"
    const val WARM_TERRACOTTA = "WARM_TERRACOTTA"
    const val ROYAL_SLATE = "ROYAL_SLATE"
    const val BERRY_PLUM = "BERRY_PLUM"
}

data class AdminFeeBreakdown(
    val sellingPrice: Double,
    val adminFee: Double,
    val sellerShare: Double
)

object AdminFeeCalculator {
    fun resolveEffectiveFee(seller: Seller?, settings: AppSettings): Pair<String, Double> {
        val type = seller?.customAdminFeeType?.takeIf {
            it == AdminFeeType.FIXED || it == AdminFeeType.PERCENTAGE
        } ?: settings.adminFeeType.takeIf {
            it == AdminFeeType.FIXED || it == AdminFeeType.PERCENTAGE
        } ?: AdminFeeType.FIXED

        val rawValue = if (seller?.customAdminFeeType != null && seller.customAdminFeeValue != null) {
            seller.customAdminFeeValue
        } else {
            settings.adminFeeValue
        }

        val clampedValue = when (type) {
            AdminFeeType.PERCENTAGE -> rawValue.coerceIn(0.0, 100.0)
            else -> rawValue.coerceIn(0.0, 10_000_000.0)
        }
        return type to clampedValue
    }

    fun calculate(sellingPrice: Double, feeType: String, feeValue: Double): AdminFeeBreakdown {
        val safePrice = sellingPrice.coerceAtLeast(0.0)
        val fee = when (feeType) {
            AdminFeeType.PERCENTAGE -> {
                val pct = feeValue.coerceIn(0.0, 100.0)
                ((safePrice * pct) / 100.0).roundToLong().toDouble()
            }
            else -> feeValue.coerceAtLeast(0.0)
        }.coerceAtMost(safePrice)

        val sellerShare = (safePrice - fee).coerceAtLeast(0.0)
        return AdminFeeBreakdown(
            sellingPrice = safePrice,
            adminFee = fee,
            sellerShare = sellerShare
        )
    }

    fun formatTariffLabel(feeType: String, feeValue: Double): String {
        return if (feeType == AdminFeeType.PERCENTAGE) {
            val formatted = if (feeValue % 1.0 == 0.0) feeValue.toInt().toString() else "%.1f".format(feeValue)
            "$formatted% dari harga jual"
        } else {
            "Rp ${"%,d".format(feeValue.toLong()).replace(',', '.')} / item"
        }
    }
}

object VendorCodeGenerator {
    private val random = SecureRandom()
    private val codeChars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val tokenChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    private val validCodeRegex = Regex("^LPK-[A-Z0-9]{6}$")
    private val validTokenRegex = Regex("^[a-zA-Z0-9_\\-]{12,64}$")

    fun generateVendorCode(): String {
        val suffix = buildString {
            repeat(6) {
                append(codeChars[random.nextInt(codeChars.length)])
            }
        }
        return "LPK-$suffix"
    }

    fun generateWriteToken(): String {
        return buildString {
            repeat(24) {
                append(tokenChars[random.nextInt(tokenChars.length)])
            }
        }
    }

    fun isValidVendorCode(code: String): Boolean = validCodeRegex.matches(code)
    fun isValidWriteToken(token: String): Boolean = validTokenRegex.matches(token)
}

@Entity(tableName = "shop_profiles")
data class ShopProfile(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "Lapak Kita",
    val ownerName: String = "",
    val whatsappNumber: String = "",
    val address: String = "",
    val logoUri: String? = null,
    val footerMessage: String = "Terima kasih atas kunjungan dan kepercayaan Anda!",
    val shopIconKey: String = ShopIconKey.STORE,
    val headerBgColorKey: String = HeaderBgColorKey.SOFT_EMERALD
)

@Entity(tableName = "sellers")
data class Seller(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val whatsappNumber: String,
    val address: String = "",
    val notes: String = "",
    val status: SellerStatus = SellerStatus.AKTIF,
    val leaveStartDate: Long? = null,
    val leaveEndDate: Long? = null,
    val leaveReason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val vendorCode: String = "",
    val writeToken: String = "",
    val customAdminFeeType: String? = null, // null = follow global setting, else FIXED / PERCENTAGE
    val customAdminFeeValue: Double? = null
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = Seller::class,
            parentColumns = ["id"],
            childColumns = ["sellerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sellerId")]
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sellerId: Long,
    val name: String,
    val category: String = "Makanan",
    val unit: String = "pcs",
    val sellingPrice: Double,
    val sellerShare: Double,
    val adminProfitPerUnit: Double = sellingPrice - sellerShare,
    val photoUri: String? = null,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_lots",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Seller::class,
            parentColumns = ["id"],
            childColumns = ["sellerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("sellerId")]
)
data class StockLot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val sellerId: Long,
    val initialQty: Int,
    val currentQty: Int,
    val unit: String = "pcs",
    val sellingPrice: Double,
    val sellerShare: Double,
    val notes: String = "",
    val depositDate: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Seller::class,
            parentColumns = ["id"],
            childColumns = ["sellerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId"), Index("sellerId")]
)
data class StockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val sellerId: Long,
    val stockLotId: Long? = null,
    val type: StockMovementType,
    val quantityChange: Int,
    val finalStock: Int,
    val reasonOrNote: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class LapakTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val totalAmount: Double,
    val totalItemsCount: Int,
    val cashReceived: Double,
    val changeGiven: Double,
    val paymentMethod: String = "Tunai",
    val totalAdminProfit: Double,
    val totalSellerShare: Double,
    val isCancelled: Boolean = false,
    val cancellationReason: String? = null,
    val cancelledAt: Long? = null
)

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = LapakTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("transactionId")]
)
data class TransactionItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val sellerId: Long,
    val stockLotId: Long? = null,
    val productName: String,
    val sellerName: String, // FOR ADMIN INTERNAL AUDIT ONLY - NEVER EXPOSED ON CUSTOMER RECEIPT
    val unit: String = "pcs",
    val unitPrice: Double,
    val sellerSharePerUnit: Double,
    val adminProfitPerUnit: Double,
    val quantity: Int,
    val subtotal: Double,
    val totalSellerShare: Double,
    val totalAdminProfit: Double
)

@Entity(
    tableName = "seller_payments",
    foreignKeys = [
        ForeignKey(
            entity = Seller::class,
            parentColumns = ["id"],
            childColumns = ["sellerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sellerId")]
)
data class SellerPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sellerId: Long,
    val paymentDate: Long = System.currentTimeMillis(),
    val amount: Double,
    val notes: String = "",
    val paymentMethod: String = "Tunai"
)

@Entity(tableName = "operating_expenses")
data class OperatingExpense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val expenseDate: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "seller_status_history",
    foreignKeys = [
        ForeignKey(
            entity = Seller::class,
            parentColumns = ["id"],
            childColumns = ["sellerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sellerId")]
)
data class SellerStatusHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sellerId: Long,
    val previousStatus: SellerStatus,
    val newStatus: SellerStatus,
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object OperationalCycleHelper {
    private val wibTimeZone: java.util.TimeZone = java.util.TimeZone.getTimeZone("Asia/Jakarta")

    /**
     * Determines the operational sales cycle ID (yyyy-MM-dd) in WIB (Asia/Jakarta).
     * - If local WIB hour >= resetHour (default 17:00): belongs to the NEW cycle (for tomorrow's sales).
     * - If local WIB hour < resetHour (00:00..16:59): stays in the SAME cycle started at yesterday's 17:00,
     *   so evening deliveries are never wiped when passing 00:00 midnight.
     */
    fun getOperationalCycleDate(
        resetHour: Int = 17,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val safeHour = resetHour.coerceIn(0, 23)
        val cal = java.util.Calendar.getInstance(wibTimeZone).apply {
            timeInMillis = nowMillis
        }
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        if (safeHour > 0 && currentHour >= safeHour) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        }
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = wibTimeZone
        }
        return sdf.format(cal.time)
    }

    fun formatResetHour(hour: Int): String {
        return String.format(java.util.Locale.US, "%02d:00", hour.coerceIn(0, 23))
    }

    fun formatCutoffMinuteBefore(hour: Int): String {
        val prevHour = (hour.coerceIn(0, 23) + 23) % 24
        return String.format(java.util.Locale.US, "%02d:59", prevHour)
    }
}

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = ThemeMode.SYSTEM, // SYSTEM, LIGHT, DARK
    val language: String = "id", // id, en
    val isGoogleDriveConnected: Boolean = false,
    val lastBackupDate: Long? = null,
    val driveAccountEmail: String? = null,
    val adminFeeType: String = AdminFeeType.FIXED, // FIXED, PERCENTAGE
    val adminFeeValue: Double = 2000.0,
    val webPortalBaseUrl: String = DEFAULT_WEB_PORTAL_BASE_URL,
    val posterTemplate: String = PosterTemplate.SOFT_EMERALD,
    val posterIntroText: String = "Menu Fresh & Ready Stok Hari Ini!",
    val posterOutroText: String = "Yuk pesan sekarang sebelum kehabisan!",
    val posterShowLogo: Boolean = true,
    val posterShowShopName: Boolean = true,
    val posterShowContact: Boolean = true,
    val posterCustomBgUri: String? = null,
    val autoResetStockEnabled: Boolean = true,
    val autoResetHour: Int = 17,
    val lastStockResetCycleId: String = ""
)

data class SoldItemSummary(
    val productName: String,
    val unit: String = "pcs",
    val quantity: Int,
    val sellerSharePerUnit: Double,
    val totalSellerShare: Double
)
