package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
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
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LapakDatabase : RoomDatabase() {

    abstract fun lapakDao(): LapakDao

    companion object {
        @Volatile
        private var INSTANCE: LapakDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sellers ADD COLUMN vendorCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sellers ADD COLUMN writeToken TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sellers ADD COLUMN customAdminFeeType TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sellers ADD COLUMN customAdminFeeValue REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN adminFeeType TEXT NOT NULL DEFAULT 'FIXED'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN adminFeeValue REAL NOT NULL DEFAULT 2000.0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN webPortalBaseUrl TEXT NOT NULL DEFAULT '$DEFAULT_WEB_PORTAL_BASE_URL'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE shop_profiles ADD COLUMN shopIconKey TEXT NOT NULL DEFAULT 'STORE'")
                db.execSQL("ALTER TABLE shop_profiles ADD COLUMN headerBgColorKey TEXT NOT NULL DEFAULT 'SOFT_EMERALD'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterTemplate TEXT NOT NULL DEFAULT 'SOFT_EMERALD'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterIntroText TEXT NOT NULL DEFAULT 'Menu Fresh & Ready Stok Hari Ini!'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterOutroText TEXT NOT NULL DEFAULT 'Yuk pesan sekarang sebelum kehabisan!'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterShowLogo INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterShowShopName INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterShowContact INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN posterCustomBgUri TEXT DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): LapakDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LapakDatabase::class.java,
                    "lapak_kita_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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
                    themeMode = ThemeMode.SYSTEM,
                    language = "id",
                    adminFeeType = AdminFeeType.FIXED,
                    adminFeeValue = 2000.0,
                    webPortalBaseUrl = DEFAULT_WEB_PORTAL_BASE_URL
                )
            )

            // 3. Penyetor 1 (Dapur Mama Farida)
            val sellerAId = dao.insertSeller(
                Seller(
                    name = "Dapur Mama Farida",
                    whatsappNumber = "081298765432",
                    address = "Komp. Flamboyan Blok C-3",
                    notes = "Spesialis burger & jajan pasar",
                    status = SellerStatus.AKTIF,
                    vendorCode = VendorCodeGenerator.generateVendorCode(),
                    writeToken = VendorCodeGenerator.generateWriteToken()
                )
            )

            // 4. Penyetor 2 (Snack Berkah Bu Dewi)
            val sellerBId = dao.insertSeller(
                Seller(
                    name = "Snack Berkah Bu Dewi",
                    whatsappNumber = "081311223344",
                    address = "Jl. Melati No. 15",
                    notes = "Spesialis brownies kukus & kue lezat",
                    status = SellerStatus.AKTIF,
                    vendorCode = VendorCodeGenerator.generateVendorCode(),
                    writeToken = VendorCodeGenerator.generateWriteToken()
                )
            )

            // Produk 1: Burger Sapi Keju (Penyetor 1)
            val prodA1Id = dao.insertProduct(
                Product(
                    sellerId = sellerAId,
                    name = "Burger Sapi Keju",
                    category = "Makanan",
                    unit = "pcs",
                    sellingPrice = 15000.0,
                    sellerShare = 13000.0,
                    adminProfitPerUnit = 2000.0
                )
            )

            // Produk 2: Brownies Kukus Coklat (Penyetor 2)
            val prodB1Id = dao.insertProduct(
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

            // Stok Awal Produk 1: Burger 15 pcs
            val lotA1Id = dao.insertStockLot(
                StockLot(
                    productId = prodA1Id,
                    sellerId = sellerAId,
                    initialQty = 15,
                    currentQty = 15,
                    unit = "pcs",
                    sellingPrice = 15000.0,
                    sellerShare = 13000.0,
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

            // Stok Awal Produk 2: Brownies 10 potong
            val lotB1Id = dao.insertStockLot(
                StockLot(
                    productId = prodB1Id,
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
                    productId = prodB1Id,
                    sellerId = sellerBId,
                    stockLotId = lotB1Id,
                    type = StockMovementType.SETORAN_AWAL,
                    quantityChange = 10,
                    finalStock = 10,
                    reasonOrNote = "Setoran awal 10 potong"
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
