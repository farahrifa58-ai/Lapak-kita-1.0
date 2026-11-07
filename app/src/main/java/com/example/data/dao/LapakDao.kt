package com.example.data.dao

import androidx.room.*
import com.example.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LapakDao {

    // Shop Profile
    @Query("SELECT * FROM shop_profiles WHERE id = 1")
    fun getShopProfileListFlow(): Flow<List<ShopProfile>>

    @Query("SELECT * FROM shop_profiles WHERE id = 1 LIMIT 1")
    suspend fun getShopProfile(): ShopProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateShopProfile(profile: ShopProfile)

    // Sellers
    @Query("SELECT * FROM sellers ORDER BY name ASC")
    fun getAllSellersFlow(): Flow<List<Seller>>

    @Query("SELECT * FROM sellers WHERE id = :id LIMIT 1")
    suspend fun getSellerById(id: Long): Seller?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeller(seller: Seller): Long

    @Update
    suspend fun updateSeller(seller: Seller)

    @Delete
    suspend fun deleteSeller(seller: Seller)

    // Products
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsFlow(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): Product?

    @Query("SELECT * FROM products WHERE sellerId = :sellerId ORDER BY name ASC")
    fun getProductsBySellerFlow(sellerId: Long): Flow<List<Product>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    // Stock Lots
    @Query("SELECT * FROM stock_lots WHERE productId = :productId AND currentQty > 0 ORDER BY depositDate ASC")
    suspend fun getActiveStockLotsForProduct(productId: Long): List<StockLot>

    @Query("SELECT * FROM stock_lots WHERE productId = :productId ORDER BY depositDate DESC")
    fun getAllStockLotsForProductFlow(productId: Long): Flow<List<StockLot>>

    @Query("SELECT * FROM stock_lots ORDER BY depositDate DESC")
    fun getAllStockLotsFlow(): Flow<List<StockLot>>

    @Query("SELECT IFNULL(SUM(currentQty), 0) FROM stock_lots WHERE productId = :productId")
    fun getProductTotalStockFlow(productId: Long): Flow<Int>

    @Query("SELECT IFNULL(SUM(currentQty), 0) FROM stock_lots WHERE productId = :productId")
    suspend fun getProductTotalStock(productId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockLot(stockLot: StockLot): Long

    @Update
    suspend fun updateStockLot(stockLot: StockLot)

    // Stock Movements
    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 100")
    fun getAllMovementsFlow(): Flow<List<StockMovement>>

    @Query("SELECT * FROM stock_movements WHERE sellerId = :sellerId ORDER BY timestamp DESC")
    fun getMovementsForSellerFlow(sellerId: Long): Flow<List<StockMovement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: StockMovement): Long

    // Transactions
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<LapakTransaction>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): LapakTransaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: LapakTransaction): Long

    @Update
    suspend fun updateTransaction(transaction: LapakTransaction)

    // Transaction Items
    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    fun getItemsForTransactionFlow(transactionId: Long): Flow<List<TransactionItem>>

    @Query("SELECT * FROM transaction_items WHERE transactionId = :transactionId")
    suspend fun getItemsForTransaction(transactionId: Long): List<TransactionItem>

    @Query("SELECT * FROM transaction_items ORDER BY id DESC")
    fun getAllTransactionItemsFlow(): Flow<List<TransactionItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactionItems(items: List<TransactionItem>)

    @Query("""
        SELECT ti.* FROM transaction_items ti 
        INNER JOIN transactions t ON ti.transactionId = t.id 
        WHERE ti.sellerId = :sellerId AND t.isCancelled = 0 
        ORDER BY t.timestamp DESC
    """)
    fun getSoldItemsForSellerFlow(sellerId: Long): Flow<List<TransactionItem>>

    @Query("""
        SELECT ti.* FROM transaction_items ti 
        INNER JOIN transactions t ON ti.transactionId = t.id 
        WHERE ti.sellerId = :sellerId AND t.isCancelled = 0 
        ORDER BY t.timestamp DESC
    """)
    suspend fun getSoldItemsForSeller(sellerId: Long): List<TransactionItem>

    // Seller Payments
    @Query("SELECT * FROM seller_payments ORDER BY paymentDate DESC")
    fun getAllPaymentsFlow(): Flow<List<SellerPayment>>

    @Query("SELECT * FROM seller_payments WHERE sellerId = :sellerId ORDER BY paymentDate DESC")
    fun getPaymentsForSellerFlow(sellerId: Long): Flow<List<SellerPayment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: SellerPayment): Long

    @Delete
    suspend fun deletePayment(payment: SellerPayment)

    // Operating Expenses
    @Query("SELECT * FROM operating_expenses ORDER BY expenseDate DESC")
    fun getAllExpensesFlow(): Flow<List<OperatingExpense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: OperatingExpense): Long

    @Delete
    suspend fun deleteExpense(expense: OperatingExpense)

    // Seller Status History
    @Query("SELECT * FROM seller_status_history WHERE sellerId = :sellerId ORDER BY timestamp DESC")
    fun getStatusHistoryForSellerFlow(sellerId: Long): Flow<List<SellerStatusHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusHistory(history: SellerStatusHistory): Long

    // App Settings
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getAppSettingsListFlow(): Flow<List<AppSettings>>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getAppSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAppSettings(settings: AppSettings)

    // Backup & Restore
    @Query("SELECT * FROM shop_profiles")
    suspend fun getAllShopProfiles(): List<ShopProfile>

    @Query("SELECT * FROM sellers")
    suspend fun getAllSellersDirect(): List<Seller>

    @Query("SELECT * FROM products")
    suspend fun getAllProductsDirect(): List<Product>

    @Query("SELECT * FROM stock_lots")
    suspend fun getAllStockLotsDirect(): List<StockLot>

    @Query("SELECT * FROM stock_movements")
    suspend fun getAllStockMovementsDirect(): List<StockMovement>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsDirect(): List<LapakTransaction>

    @Query("SELECT * FROM transaction_items")
    suspend fun getAllTransactionItemsDirect(): List<TransactionItem>

    @Query("SELECT * FROM seller_payments")
    suspend fun getAllSellerPaymentsDirect(): List<SellerPayment>

    @Query("SELECT * FROM operating_expenses")
    suspend fun getAllOperatingExpensesDirect(): List<OperatingExpense>

    @Query("SELECT * FROM seller_status_history")
    suspend fun getAllSellerStatusHistoryDirect(): List<SellerStatusHistory>

    @Query("DELETE FROM transaction_items")
    suspend fun clearTransactionItems()

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM stock_movements")
    suspend fun clearStockMovements()

    @Query("DELETE FROM stock_lots")
    suspend fun clearStockLots()

    @Query("DELETE FROM products")
    suspend fun clearProducts()

    @Query("DELETE FROM seller_payments")
    suspend fun clearSellerPayments()

    @Query("DELETE FROM seller_status_history")
    suspend fun clearSellerStatusHistory()

    @Query("DELETE FROM operating_expenses")
    suspend fun clearOperatingExpenses()

    @Query("DELETE FROM sellers")
    suspend fun clearSellers()
}
