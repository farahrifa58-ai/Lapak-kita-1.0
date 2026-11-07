package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.LapakDao
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ShopProfile::class,
        Seller::class,
        Product::class,
        StockLot::class,
        StockMovement::class,
        LapakTransaction::class,
        TransactionItem::class,
        SellerPayment::class,
        OperatingExpense::class,
        SellerStatusHistory::class,
        AppSettings::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LapakDatabase : RoomDatabase() {

    abstract fun lapakDao(): LapakDao

    companion object {
        @Volatile
        private var INSTANCE: LapakDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): LapakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LapakDatabase::class.java,
                    "lapak_kita_database"
                )
                    .addCallback(LapakDatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class LapakDatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.lapakDao())
                    }
                }
            }
        }

        suspend fun populateInitialData(dao: LapakDao) {
            // 1. Shop Profile
            dao.insertOrUpdateShopProfile(
                ShopProfile(
                    id = 1,
                    shopName = "Lapak Kita",
                    ownerName = "Rafline Dev",
                    whatsappNumber = "0812-3456-7890",
                    address = "Jl. Pasar Anyar No. 24, Sentra Kuliner Lapak Kita",
                    footerMessage = "Terima kasih atas kunjungan & kepercayaan Anda berbelanja di Lapak Kita!"
                )
            )

            // 2. Settings
            dao.insertOrUpdateAppSettings(
                AppSettings(
                    id = 1,
                    themeMode = "LIGHT",
                    language = "id"
                )
            )

            // 3. Penyetor A (Aktif)
            val sellerAId = dao.insertSeller(
                Seller(
                    name = "Dapur Mama Farida",
                    whatsappNumber = "081298765432",
                    address = "Komp. Flamboyan Blok C-3",
                    notes = "Spesialis jajan pasar & burger homemade",
                    status = SellerStatus.AKTIF
                )
            )

            // 4. Penyetor B (Aktif)
            val sellerBId = dao.insertSeller(
                Seller(
                    name = "Snack Berkah Bu Dewi",
                    whatsappNumber = "081311223344",
                    address = "Jl. Melati No. 15",
                    notes = "Spesialis risoles & gorengan premium",
                    status = SellerStatus.AKTIF
                )
            )

            // 5. Penyetor C (Libur)
            val now = System.currentTimeMillis()
            val threeDaysLater = now + (3 * 24 * 60 * 60 * 1000L)
            val sellerCId = dao.insertSeller(
                Seller(
                    name = "Kue Tradisional Bu Siti",
                    whatsappNumber = "081555667788",
                    address = "Jl. Cendana No. 9",
                    notes = "Spesialis kue basah tradisional",
                    status = SellerStatus.LIBUR,
                    leaveStartDate = now - (24 * 60 * 60 * 1000L),
                    leaveEndDate = threeDaysLater,
                    leaveReason = "Ada acara keluarga di luar kota"
                )
            )

            // Log status history for Seller C
            dao.insertStatusHistory(
                SellerStatusHistory(
                    sellerId = sellerCId,
                    previousStatus = SellerStatus.AKTIF,
                    newStatus = SellerStatus.LIBUR,
                    reason = "Ada acara keluarga di luar kota",
                    timestamp = now - (24 * 60 * 60 * 1000L)
                )
            )

            // Products for Seller A
            // Notice: Seller A sells "Burger Sapi Keju"
            val prodA1Id = dao.insertProduct(
                Product(
                    sellerId = sellerAId,
                    name = "Burger Sapi Keju",
                    category = "Makanan",
                    unit = "pcs",
                    sellingPrice = 15000.0,
                    sellerShare = 12000.0,
                    adminProfitPerUnit = 3000.0
                )
            )
            val prodA2Id = dao.insertProduct(
                Product(
                    sellerId = sellerAId,
                    name = "Sandwich Telur Mayo",
                    category = "Makanan",
                    unit = "pcs",
                    sellingPrice = 12000.0,
                    sellerShare = 9500.0,
                    adminProfitPerUnit = 2500.0
                )
            )
            val prodA3Id = dao.insertProduct(
                Product(
                    sellerId = sellerAId,
                    name = "Donat Gula Halus",
                    category = "Kue",
                    unit = "pcs",
                    sellingPrice = 5000.0,
                    sellerShare = 3800.0,
                    adminProfitPerUnit = 1200.0
                )
            )

            // Products for Seller B
            // Notice: Seller B ALSO sells "Burger Sapi Keju" (demonstrating separate stock per seller!)
            val prodB1Id = dao.insertProduct(
                Product(
                    sellerId = sellerBId,
                    name = "Burger Sapi Keju",
                    category = "Makanan",
                    unit = "pcs",
                    sellingPrice = 14000.0,
                    sellerShare = 11000.0,
                    adminProfitPerUnit = 3000.0
                )
            )
            val prodB2Id = dao.insertProduct(
                Product(
                    sellerId = sellerBId,
                    name = "Risoles Mayo Pedas",
                    category = "Gorengan",
                    unit = "pcs",
                    sellingPrice = 4000.0,
                    sellerShare = 3000.0,
                    adminProfitPerUnit = 1000.0
                )
            )
            val prodB3Id = dao.insertProduct(
                Product(
                    sellerId = sellerBId,
                    name = "Brownies Kukus Coklat",
                    category = "Kue",
                    unit = "potong",
                    sellingPrice = 8000.0,
                    sellerShare = 6000.0,
                    adminProfitPerUnit = 2000.0
                )
            )

            // Products for Seller C (Libur, but existing stock still available for sale)
            val prodC1Id = dao.insertProduct(
                Product(
                    sellerId = sellerCId,
                    name = "Lemper Ayam Bakar",
                    category = "Kue Basah",
                    unit = "pcs",
                    sellingPrice = 4000.0,
                    sellerShare = 3000.0,
                    adminProfitPerUnit = 1000.0
                )
            )
            val prodC2Id = dao.insertProduct(
                Product(
                    sellerId = sellerCId,
                    name = "Pastel Ayam Telur",
                    category = "Gorengan",
                    unit = "pcs",
                    sellingPrice = 4500.0,
                    sellerShare = 3500.0,
                    adminProfitPerUnit = 1000.0
                )
            )

            // Stock Lots & Initial Consignments
            // Seller A: Burger 15 pcs, Sandwich 10 pcs, Donat 20 pcs
            val lotA1Id = dao.insertStockLot(
                StockLot(
                    productId = prodA1Id,
                    sellerId = sellerAId,
                    initialQty = 15,
                    currentQty = 15,
                    unit = "pcs",
                    sellingPrice = 15000.0,
                    sellerShare = 12000.0,
                    notes = "Setoran pagi jam 06.00"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodA1Id,
                    sellerId = sellerAId,
                    stockLotId = lotA1Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 15,
                    finalStock = 15,
                    reasonOrNote = "Setoran awal 15 pcs"
                )
            )

            val lotA2Id = dao.insertStockLot(
                StockLot(
                    productId = prodA2Id,
                    sellerId = sellerAId,
                    initialQty = 10,
                    currentQty = 10,
                    unit = "pcs",
                    sellingPrice = 12000.0,
                    sellerShare = 9500.0,
                    notes = "Setoran pagi"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodA2Id,
                    sellerId = sellerAId,
                    stockLotId = lotA2Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 10,
                    finalStock = 10,
                    reasonOrNote = "Setoran awal 10 pcs"
                )
            )

            val lotA3Id = dao.insertStockLot(
                StockLot(
                    productId = prodA3Id,
                    sellerId = sellerAId,
                    initialQty = 20,
                    currentQty = 20,
                    unit = "pcs",
                    sellingPrice = 5000.0,
                    sellerShare = 3800.0,
                    notes = "Setoran pagi"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodA3Id,
                    sellerId = sellerAId,
                    stockLotId = lotA3Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 20,
                    finalStock = 20,
                    reasonOrNote = "Setoran awal 20 pcs"
                )
            )

            // Seller B: Burger 12 pcs, Risoles 25 pcs, Brownies 10 potong
            val lotB1Id = dao.insertStockLot(
                StockLot(
                    productId = prodB1Id,
                    sellerId = sellerBId,
                    initialQty = 12,
                    currentQty = 12,
                    unit = "pcs",
                    sellingPrice = 14000.0,
                    sellerShare = 11000.0,
                    notes = "Setoran pagi"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodB1Id,
                    sellerId = sellerBId,
                    stockLotId = lotB1Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 12,
                    finalStock = 12,
                    reasonOrNote = "Setoran awal 12 pcs"
                )
            )

            val lotB2Id = dao.insertStockLot(
                StockLot(
                    productId = prodB2Id,
                    sellerId = sellerBId,
                    initialQty = 25,
                    currentQty = 25,
                    unit = "pcs",
                    sellingPrice = 4000.0,
                    sellerShare = 3000.0,
                    notes = "Setoran pagi"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodB2Id,
                    sellerId = sellerBId,
                    stockLotId = lotB2Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 25,
                    finalStock = 25,
                    reasonOrNote = "Setoran awal 25 pcs"
                )
            )

            val lotB3Id = dao.insertStockLot(
                StockLot(
                    productId = prodB3Id,
                    sellerId = sellerBId,
                    initialQty = 10,
                    currentQty = 10,
                    unit = "potong",
                    sellingPrice = 8000.0,
                    sellerShare = 6000.0,
                    notes = "Setoran pagi"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodB3Id,
                    sellerId = sellerBId,
                    stockLotId = lotB3Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 10,
                    finalStock = 10,
                    reasonOrNote = "Setoran awal 10 potong"
                )
            )

            // Seller C: Lemper 15 pcs, Pastel 12 pcs
            val lotC1Id = dao.insertStockLot(
                StockLot(
                    productId = prodC1Id,
                    sellerId = sellerCId,
                    initialQty = 15,
                    currentQty = 15,
                    unit = "pcs",
                    sellingPrice = 4000.0,
                    sellerShare = 3000.0,
                    notes = "Stok kemarin sebelum libur"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodC1Id,
                    sellerId = sellerCId,
                    stockLotId = lotC1Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 15,
                    finalStock = 15,
                    reasonOrNote = "Setoran awal 15 pcs"
                )
            )

            val lotC2Id = dao.insertStockLot(
                StockLot(
                    productId = prodC2Id,
                    sellerId = sellerCId,
                    initialQty = 12,
                    currentQty = 12,
                    unit = "pcs",
                    sellingPrice = 4500.0,
                    sellerShare = 3500.0,
                    notes = "Stok kemarin sebelum libur"
                )
            )
            dao.insertMovement(
                StockMovement(
                    productId = prodC2Id,
                    sellerId = sellerCId,
                    stockLotId = lotC2Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 12,
                    finalStock = 12,
                    reasonOrNote = "Setoran awal 12 pcs"
                )
            )

            // Operating Expense example
            dao.insertExpense(
                OperatingExpense(
                    title = "Kantong Kresek & Plastik Pembungkus",
                    amount = 15000.0,
                    notes = "Pembelian 2 pak kantong belanja lapak"
                )
            )
        }
    }
}
