package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

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

@Entity(tableName = "shop_profiles")
data class ShopProfile(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "Lapak Kita",
    val ownerName: String = "Rafline Dev",
    val whatsappNumber: String = "0812-3456-7890",
    val address: String = "Jl. Pasar Segar No. 18, Sentra Kuliner Lapak Kita",
    val logoUri: String? = null,
    val footerMessage: String = "Terima kasih atas kunjungan dan kepercayaan Anda!"
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
    val createdAt: Long = System.currentTimeMillis()
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

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val themeMode: String = "LIGHT", // LIGHT, DARK
    val language: String = "id", // id, en
    val isGoogleDriveConnected: Boolean = false,
    val lastBackupDate: Long? = null,
    val driveAccountEmail: String? = null
)

data class SoldItemSummary(
    val productName: String,
    val unit: String = "pcs",
    val quantity: Int,
    val sellerSharePerUnit: Double,
    val totalSellerShare: Double
)
