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
        AppSettings::class,
        DailyClosingArchive::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LapakDatabase : RoomDatabase() {

    abstract fun lapakDao(): LapakDao

    companion object {
        const val LEGACY_GLOBAL_DB_NAME = "lapak_kita_database"

        private val userInstances = mutableMapOf<String, LapakDatabase>()

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoResetStockEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoResetHour INTEGER NOT NULL DEFAULT 17")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN lastStockResetCycleId TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoClosingEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoClosingHour INTEGER NOT NULL DEFAULT 22")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoClosingMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN autoClosingLeftoverMode TEXT NOT NULL DEFAULT 'RESET_ZERO'")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN lastClosedDate TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN cashierShowOmzet INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN cashierShowProfit INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN cashierShowExpenses INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN printerName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN printerAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN printerPaperSize TEXT NOT NULL DEFAULT '58MM'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_closing_archives` (
                        `closingDate` TEXT NOT NULL,
                        `closedAtMillis` INTEGER NOT NULL,
                        `triggerType` TEXT NOT NULL,
                        `leftoverMode` TEXT NOT NULL,
                        `totalTransactions` INTEGER NOT NULL,
                        `totalItemsIn` INTEGER NOT NULL,
                        `totalItemsSold` INTEGER NOT NULL,
                        `totalItemsLeftover` INTEGER NOT NULL,
                        `totalOmzet` REAL NOT NULL,
                        `totalSellerShare` REAL NOT NULL,
                        `totalAdminProfit` REAL NOT NULL,
                        `totalOperatingExpenses` REAL NOT NULL,
                        `netProfit` REAL NOT NULL,
                        `itemsBreakdownJson` TEXT NOT NULL,
                        PRIMARY KEY(`closingDate`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun sanitizeUidForStorage(uid: String): String {
            val cleaned = uid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
            require(cleaned.isNotBlank()) { "UID akun wajib diisi untuk penyimpanan lokal terisolasi." }
            return cleaned
        }

        fun databaseNameForUser(uid: String): String {
            val safeUid = sanitizeUidForStorage(uid)
            return "lapakkita_${safeUid}_db"
        }

        /**
         * Returns a strictly UID-isolated Room SQLite database (`lapakkita_{uid}_db`) so
         * operational data (sellers, products, stocks, transactions, expenses, settlements, settings)
         * can NEVER leak across different accounts logged in on the same device.
         */
        fun getDatabaseForUser(context: Context, uid: String): LapakDatabase {
            val safeUid = sanitizeUidForStorage(uid)
            return synchronized(this) {
                val existing = userInstances[safeUid]
                if (existing != null && existing.isOpen) {
                    existing
                } else {
                    val dbName = "lapakkita_${safeUid}_db"
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        LapakDatabase::class.java,
                        dbName
                    )
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                        .fallbackToDestructiveMigration()
                        .build()
                    userInstances[safeUid] = instance
                    instance
                }
            }
        }

        /**
         * Closes and evicts the Room database handle for [uid] during logout / account switch.
         */
        fun closeUserDatabase(uid: String) {
            val safeUid = uid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
            if (safeUid.isBlank()) return
            synchronized(this) {
                userInstances.remove(safeUid)?.let { db ->
                    runCatching { db.close() }
                }
            }
        }

        fun closeAllDatabases() {
            synchronized(this) {
                userInstances.values.forEach { db ->
                    runCatching { db.close() }
                }
                userInstances.clear()
            }
        }

        /**
         * Permanently deletes the old global un-prefixed SQLite database (`lapak_kita_database`)
         * along with its WAL/SHM/journal files so legacy test data can never bleed into user accounts.
         */
        fun deleteLegacyGlobalDatabase(context: Context) {
            val appContext = context.applicationContext
            runCatching { appContext.deleteDatabase(LEGACY_GLOBAL_DB_NAME) }
            val dbFile = appContext.getDatabasePath(LEGACY_GLOBAL_DB_NAME)
            listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
                runCatching {
                    val f = java.io.File(dbFile.path + suffix)
                    if (f.exists()) f.delete()
                }
            }
        }
    }
}

