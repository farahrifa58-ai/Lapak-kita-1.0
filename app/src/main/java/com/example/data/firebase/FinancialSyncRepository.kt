package com.example.data.firebase

import android.util.Log
import com.example.data.model.AppSettings
import com.example.data.model.AutoClosingLeftoverMode
import com.example.data.model.DailyClosingArchive
import com.example.data.model.DailyClosingItemRecord
import com.example.data.model.DailyClosingTriggerType
import com.example.data.model.LapakTransaction
import com.example.data.model.OperatingExpense
import com.example.data.model.Seller
import com.example.data.model.SellerPayment
import com.example.data.model.TransactionItem
import com.example.data.model.VendorCodeGenerator
import com.example.data.repository.ProductDetailItem
import com.example.data.repository.SellerDetailItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudVendorProductRecord(
    val name: String,
    val category: String,
    val unit: String,
    val sellingPrice: Double,
    val sellerShare: Double,
    val adminProfitPerUnit: Double,
    val currentStock: Int,
    val isActive: Boolean
)

data class CloudVendorCatalogRecord(
    val vendorCode: String,
    val sellerName: String,
    val whatsappNumber: String,
    val address: String,
    val notes: String,
    val sellerStatus: String,
    val writeToken: String,
    val customAdminFeeType: String?,
    val customAdminFeeValue: Double?,
    val sourceDeviceId: String,
    val products: List<CloudVendorProductRecord>,
    val updatedAtMillis: Long
)

data class CloudTransactionItemRecord(
    val productName: String,
    val sellerName: String,
    val unit: String,
    val unitPrice: Double,
    val sellerSharePerUnit: Double,
    val adminProfitPerUnit: Double,
    val quantity: Int,
    val subtotal: Double,
    val totalSellerShare: Double,
    val totalAdminProfit: Double
)

data class CloudTransactionRecord(
    val docId: String,
    val transactionNumber: String,
    val timestampMillis: Long,
    val totalAmount: Double,
    val totalItemsCount: Int,
    val cashReceived: Double,
    val changeGiven: Double,
    val paymentMethod: String,
    val totalAdminProfit: Double,
    val totalSellerShare: Double,
    val cancelled: Boolean,
    val cancellationReason: String,
    val items: List<CloudTransactionItemRecord>
)

data class CloudExpenseRecord(
    val docId: String,
    val localExpenseId: Long,
    val title: String,
    val amount: Double,
    val expenseDateMillis: Long,
    val notes: String
)

data class CloudSettlementRecord(
    val docId: String,
    val localPaymentId: Long,
    val localSellerId: Long,
    val sellerName: String,
    val vendorCode: String,
    val amount: Double,
    val paymentDateMillis: Long,
    val paymentMethod: String,
    val notes: String,
    val status: String
)

data class CloudFinancialBundle(
    val transactions: List<CloudTransactionRecord>,
    val expenses: List<CloudExpenseRecord>,
    val settlements: List<CloudSettlementRecord>
)

data class CloudSnapshotBatch<T>(
    val items: List<T>,
    val isFromCache: Boolean
)

data class CloudUserSettingsDoc(
    val autoClosingEnabled: Boolean?,
    val autoClosingHour: Int?,
    val autoClosingMinute: Int?,
    val autoClosingLeftoverMode: String?,
    val lastClosedDate: String?,
    val cashierShowOmzet: Boolean?,
    val cashierShowProfit: Boolean?,
    val cashierShowExpenses: Boolean?,
    val isFromCache: Boolean = false
)

data class CloudDailyClosingRecord(
    val closingDate: String,
    val closedAtMillis: Long,
    val triggerType: String,
    val leftoverMode: String,
    val totalTransactions: Int,
    val totalItemsIn: Int,
    val totalItemsSold: Int,
    val totalItemsLeftover: Int,
    val totalOmzet: Double,
    val totalSellerShare: Double,
    val totalAdminProfit: Double,
    val totalOperatingExpenses: Double,
    val netProfit: Double,
    val itemsBreakdown: List<DailyClosingItemRecord>
)

/**
 * Syncs all Lapak Kita operational & financial data in real time under user-scoped subcollections
 * so up to 2 authorized devices (HP Kasir + HP Owner) stay live-synced via `onSnapshot`
 * with `MetadataChanges.INCLUDE`:
 * - `users/{uid}/vendors` (sellers, products & live consignment stock)
 * - `users/{uid}/transactions`
 * - `users/{uid}/expenses`
 * - `users/{uid}/settlements`
 */
class FinancialSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun vendorsCatalogCollection(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("vendors")

    private fun transactionsCollection(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("transactions")

    private fun expensesCollection(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("expenses")

    private fun settlementsCollection(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("settlements")

    private fun dailyClosingsCollection(uid: String): CollectionReference =
        db.collection("users").document(uid).collection("daily_closings")

    private fun requireMatchingUserId(expectedUid: String? = null): String {
        val activeUid = auth.currentUser?.uid
            ?: throw IllegalStateException("Admin harus masuk terlebih dahulu.")
        if (!expectedUid.isNullOrBlank() && activeUid != expectedUid) {
            throw IllegalStateException("Sesi akun telah berubah. Operasi sinkronisasi dibatalkan untuk mencegah penimpaan lintas akun.")
        }
        return activeUid
    }

    private fun sanitizeDocId(raw: String, prefix: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100)
        return "${prefix}_${clean.ifBlank { System.currentTimeMillis().toString() }}".take(120)
    }

    fun buildTransactionDocId(transactionNumber: String): String =
        sanitizeDocId(transactionNumber, "trx")

    fun buildExpenseDocId(expense: OperatingExpense): String =
        sanitizeDocId("${expense.expenseDate}_${expense.id}", "exp")

    fun buildSettlementDocId(payment: SellerPayment): String =
        sanitizeDocId("${payment.paymentDate}_${payment.id}", "pay")

    suspend fun syncVendorCatalogToUserCloud(
        sellerDetail: SellerDetailItem,
        sellerProducts: List<ProductDetailItem>,
        sourceDeviceId: String,
        expectedUid: String? = null
    ): Result<Unit> {
        val seller = sellerDetail.seller
        val vendorCode = seller.vendorCode.trim().uppercase()
        val writeToken = seller.writeToken.trim()
        if (!VendorCodeGenerator.isValidVendorCode(vendorCode) || !VendorCodeGenerator.isValidWriteToken(writeToken)) {
            return Result.failure(IllegalArgumentException("Kode penyetor atau token tidak valid"))
        }

        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val safeStatus = seller.status.name.takeIf { it in setOf("AKTIF", "LIBUR", "NONAKTIF") } ?: "AKTIF"
        val customFeeType = seller.customAdminFeeType?.takeIf { it in setOf("FIXED", "PERCENTAGE") }.orEmpty()
        val customFeeVal = (seller.customAdminFeeValue ?: 0.0).coerceIn(0.0, 10_000_000.0)

        val productMaps = sellerProducts.take(100).map { detail ->
            val p = detail.product
            val safePrice = p.sellingPrice.coerceAtLeast(0.0)
            val safeShare = p.sellerShare.coerceIn(0.0, safePrice)
            val safeProfit = (safePrice - safeShare).coerceAtLeast(0.0)
            val safeStock = detail.currentStock.coerceAtLeast(0)
            mapOf(
                "id" to p.id,
                "localProductId" to p.id,
                "name" to p.name.trim().ifEmpty { "Produk" }.take(100),
                "category" to p.category.trim().ifEmpty { "Makanan" }.take(50),
                "unit" to p.unit.trim().ifEmpty { "pcs" }.take(20),
                "sellingPrice" to safePrice,
                "basePrice" to safeShare,
                "sellerShare" to safeShare,
                "commission" to safeProfit,
                "adminProfitPerUnit" to safeProfit,
                "stock" to safeStock,
                "currentStock" to safeStock,
                "isActive" to p.isActive
            )
        }

        val path = "users/$uid/vendors/$vendorCode"
        val docRef = vendorsCatalogCollection(uid).document(vendorCode)

        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "ownerUid" to uid,
                    "vendorCode" to vendorCode,
                    "sellerName" to seller.name.trim().ifEmpty { "Penyetor" }.take(100),
                    "whatsappNumber" to seller.whatsappNumber.trim().take(40),
                    "address" to seller.address.trim().take(250),
                    "notes" to seller.notes.trim().take(300),
                    "sellerStatus" to safeStatus,
                    "writeToken" to writeToken,
                    "customAdminFeeType" to customFeeType,
                    "customAdminFeeValue" to customFeeVal,
                    "sourceDeviceId" to sourceDeviceId.trim().take(128),
                    "products" to productMaps,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "sellerName" to seller.name.trim().ifEmpty { "Penyetor" }.take(100),
                    "whatsappNumber" to seller.whatsappNumber.trim().take(40),
                    "address" to seller.address.trim().take(250),
                    "notes" to seller.notes.trim().take(300),
                    "sellerStatus" to safeStatus,
                    "writeToken" to writeToken,
                    "customAdminFeeType" to customFeeType,
                    "customAdminFeeValue" to customFeeVal,
                    "sourceDeviceId" to sourceDeviceId.trim().take(128),
                    "products" to productMaps,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updatePayload, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.WRITE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("FinancialSyncRepo", "Failed syncing $path", e)
            Result.failure(e)
        }
    }

    suspend fun deleteVendorCatalogFromUserCloud(
        vendorCode: String,
        expectedUid: String? = null
    ): Result<Unit> {
        val cleanCode = vendorCode.trim().uppercase()
        if (!VendorCodeGenerator.isValidVendorCode(cleanCode)) return Result.success(Unit)
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (_: Exception) {
            return Result.success(Unit)
        }
        val path = "users/$uid/vendors/$cleanCode"
        return try {
            vendorsCatalogCollection(uid).document(cleanCode).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FinancialSyncRepo", "Delete $path ignored/failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncTransactionToFirestore(
        transaction: LapakTransaction,
        items: List<TransactionItem>,
        expectedUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val docId = buildTransactionDocId(transaction.transactionNumber)
        val path = "users/$uid/transactions/$docId"
        val docRef = transactionsCollection(uid).document(docId)

        val itemMaps = items.take(100).map { it ->
            mapOf(
                "productName" to it.productName.trim().ifEmpty { "Produk" }.take(100),
                "sellerName" to it.sellerName.trim().ifEmpty { "Penyetor" }.take(100),
                "unit" to it.unit.trim().ifEmpty { "pcs" }.take(20),
                "unitPrice" to it.unitPrice.coerceAtLeast(0.0),
                "sellerSharePerUnit" to it.sellerSharePerUnit.coerceAtLeast(0.0),
                "adminProfitPerUnit" to it.adminProfitPerUnit.coerceAtLeast(0.0),
                "quantity" to it.quantity.coerceAtLeast(0),
                "subtotal" to it.subtotal.coerceAtLeast(0.0),
                "totalSellerShare" to it.totalSellerShare.coerceAtLeast(0.0),
                "totalAdminProfit" to it.totalAdminProfit.coerceAtLeast(0.0)
            )
        }

        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "ownerUid" to uid,
                    "transactionNumber" to transaction.transactionNumber.trim().ifEmpty { docId }.take(64),
                    "timestampMillis" to transaction.timestamp.coerceAtLeast(0L),
                    "totalAmount" to transaction.totalAmount.coerceAtLeast(0.0),
                    "totalItemsCount" to transaction.totalItemsCount.coerceAtLeast(0),
                    "cashReceived" to transaction.cashReceived.coerceAtLeast(0.0),
                    "changeGiven" to transaction.changeGiven.coerceAtLeast(0.0),
                    "paymentMethod" to transaction.paymentMethod.trim().ifEmpty { "Tunai" }.take(40),
                    "totalAdminProfit" to transaction.totalAdminProfit.coerceAtLeast(0.0),
                    "totalSellerShare" to transaction.totalSellerShare.coerceAtLeast(0.0),
                    "cancelled" to transaction.isCancelled,
                    "cancellationReason" to (transaction.cancellationReason ?: "").trim().take(200),
                    "items" to itemMaps,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "cancelled" to transaction.isCancelled,
                    "cancellationReason" to (transaction.cancellationReason ?: "").trim().take(200),
                    "totalAmount" to transaction.totalAmount.coerceAtLeast(0.0),
                    "totalItemsCount" to transaction.totalItemsCount.coerceAtLeast(0),
                    "cashReceived" to transaction.cashReceived.coerceAtLeast(0.0),
                    "changeGiven" to transaction.changeGiven.coerceAtLeast(0.0),
                    "paymentMethod" to transaction.paymentMethod.trim().ifEmpty { "Tunai" }.take(40),
                    "totalAdminProfit" to transaction.totalAdminProfit.coerceAtLeast(0.0),
                    "totalSellerShare" to transaction.totalSellerShare.coerceAtLeast(0.0),
                    "items" to itemMaps,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updatePayload, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.WRITE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("FinancialSyncRepo", "Failed syncing $path", e)
            Result.failure(e)
        }
    }

    suspend fun syncExpenseToFirestore(
        expense: OperatingExpense,
        expectedUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val docId = buildExpenseDocId(expense)
        val path = "users/$uid/expenses/$docId"
        val docRef = expensesCollection(uid).document(docId)

        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "ownerUid" to uid,
                    "localExpenseId" to expense.id.coerceAtLeast(0L),
                    "title" to expense.title.trim().ifEmpty { "Biaya Operasional" }.take(120),
                    "amount" to expense.amount.coerceAtLeast(0.0),
                    "expenseDateMillis" to expense.expenseDate.coerceAtLeast(0L),
                    "notes" to expense.notes.trim().take(300),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                docRef.set(
                    mapOf(
                        "title" to expense.title.trim().ifEmpty { "Biaya Operasional" }.take(120),
                        "amount" to expense.amount.coerceAtLeast(0.0),
                        "expenseDateMillis" to expense.expenseDate.coerceAtLeast(0L),
                        "notes" to expense.notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.WRITE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("FinancialSyncRepo", "Failed syncing $path", e)
            Result.failure(e)
        }
    }

    suspend fun deleteExpenseFromFirestore(
        expense: OperatingExpense,
        expectedUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (_: Exception) {
            return Result.success(Unit)
        }
        val docId = buildExpenseDocId(expense)
        val path = "users/$uid/expenses/$docId"
        return try {
            expensesCollection(uid).document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FinancialSyncRepo", "Delete $path ignored/failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun syncSettlementToFirestore(
        payment: SellerPayment,
        seller: Seller?,
        status: String,
        expectedUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val safeStatus = when (status.uppercase()) {
            "LUNAS" -> "LUNAS"
            "SEBAGIAN", "DIBAYAR SEBAGIAN" -> "SEBAGIAN"
            else -> "BELUM_DIBAYAR"
        }

        val docId = buildSettlementDocId(payment)
        val path = "users/$uid/settlements/$docId"
        val docRef = settlementsCollection(uid).document(docId)

        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "ownerUid" to uid,
                    "localPaymentId" to payment.id.coerceAtLeast(0L),
                    "localSellerId" to payment.sellerId.coerceAtLeast(0L),
                    "sellerName" to (seller?.name?.trim()?.ifEmpty { "Penyetor" } ?: "Penyetor").take(100),
                    "vendorCode" to (seller?.vendorCode?.trim() ?: "").take(20),
                    "amount" to payment.amount.coerceAtLeast(0.0),
                    "paymentDateMillis" to payment.paymentDate.coerceAtLeast(0L),
                    "paymentMethod" to payment.paymentMethod.trim().ifEmpty { "Tunai" }.take(40),
                    "notes" to payment.notes.trim().take(300),
                    "status" to safeStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                docRef.set(
                    mapOf(
                        "sellerName" to (seller?.name?.trim()?.ifEmpty { "Penyetor" } ?: "Penyetor").take(100),
                        "vendorCode" to (seller?.vendorCode?.trim() ?: "").take(20),
                        "amount" to payment.amount.coerceAtLeast(0.0),
                        "paymentDateMillis" to payment.paymentDate.coerceAtLeast(0L),
                        "paymentMethod" to payment.paymentMethod.trim().ifEmpty { "Tunai" }.take(40),
                        "notes" to payment.notes.trim().take(300),
                        "status" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.WRITE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("FinancialSyncRepo", "Failed syncing $path", e)
            Result.failure(e)
        }
    }

    suspend fun syncUserSettingsToFirestore(
        settings: AppSettings,
        expectedUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val safeMode = when (settings.autoClosingLeftoverMode.uppercase()) {
            AutoClosingLeftoverMode.CARRY_OVER -> AutoClosingLeftoverMode.CARRY_OVER
            else -> AutoClosingLeftoverMode.RESET_ZERO
        }
        val safeLastClosed = settings.lastClosedDate.trim().takeIf {
            it.isEmpty() || it.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))
        }.orEmpty()

        val path = "users/$uid"
        val docRef = db.collection("users").document(uid)
        return try {
            val payload = mapOf(
                "autoClosingEnabled" to settings.autoClosingEnabled,
                "autoClosingHour" to settings.autoClosingHour.coerceIn(0, 23),
                "autoClosingMinute" to settings.autoClosingMinute.coerceIn(0, 59),
                "autoClosingLeftoverMode" to safeMode,
                "lastClosedDate" to safeLastClosed,
                "cashierShowOmzet" to settings.cashierShowOmzet,
                "cashierShowProfit" to settings.cashierShowProfit,
                "cashierShowExpenses" to settings.cashierShowExpenses,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.UPDATE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("FinancialSyncRepo", "Failed syncing settings to $path", e)
            Result.failure(e)
        }
    }

    suspend fun syncDailyClosingToFirestore(
        archive: DailyClosingArchive,
        items: List<DailyClosingItemRecord>,
        expectedUid: String? = null
    ): Result<Unit> {
        val uid = try {
            requireMatchingUserId(expectedUid)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val closingDate = archive.closingDate.trim()
        if (!closingDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) {
            return Result.failure(IllegalArgumentException("Format tanggal tutup buku tidak valid: $closingDate"))
        }

        val safeTrigger = when (archive.triggerType.uppercase()) {
            DailyClosingTriggerType.MANUAL -> DailyClosingTriggerType.MANUAL
            else -> DailyClosingTriggerType.AUTO
        }
        val safeMode = when (archive.leftoverMode.uppercase()) {
            AutoClosingLeftoverMode.CARRY_OVER -> AutoClosingLeftoverMode.CARRY_OVER
            else -> AutoClosingLeftoverMode.RESET_ZERO
        }

        val itemMaps = items.take(300).map { rec ->
            mapOf(
                "productId" to rec.productId,
                "productName" to rec.productName.trim().ifEmpty { "Produk" }.take(100),
                "sellerName" to rec.sellerName.trim().ifEmpty { "Penyetor" }.take(100),
                "unit" to rec.unit.trim().ifEmpty { "pcs" }.take(20),
                "qtyIn" to rec.qtyIn.coerceAtLeast(0),
                "qtySold" to rec.qtySold.coerceAtLeast(0),
                "qtyLeftover" to rec.qtyLeftover.coerceAtLeast(0),
                "omzet" to rec.omzet.coerceAtLeast(0.0),
                "sellerShare" to rec.sellerShare.coerceAtLeast(0.0),
                "adminProfit" to rec.adminProfit.coerceAtLeast(0.0)
            )
        }

        val path = "users/$uid/daily_closings/$closingDate"
        val docRef = dailyClosingsCollection(uid).document(closingDate)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val createPayload = mapOf(
                    "ownerUid" to uid,
                    "closingDate" to closingDate,
                    "closedAtMillis" to archive.closedAtMillis.coerceAtLeast(0L),
                    "triggerType" to safeTrigger,
                    "leftoverMode" to safeMode,
                    "totalTransactions" to archive.totalTransactions.coerceAtLeast(0),
                    "totalItemsIn" to archive.totalItemsIn.coerceAtLeast(0),
                    "totalItemsSold" to archive.totalItemsSold.coerceAtLeast(0),
                    "totalItemsLeftover" to archive.totalItemsLeftover.coerceAtLeast(0),
                    "totalOmzet" to archive.totalOmzet.coerceAtLeast(0.0),
                    "totalSellerShare" to archive.totalSellerShare.coerceAtLeast(0.0),
                    "totalAdminProfit" to archive.totalAdminProfit.coerceAtLeast(0.0),
                    "totalOperatingExpenses" to archive.totalOperatingExpenses.coerceAtLeast(0.0),
                    "netProfit" to archive.netProfit,
                    "itemsBreakdown" to itemMaps,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(createPayload).await()
            } else {
                val updatePayload = mapOf(
                    "closedAtMillis" to archive.closedAtMillis.coerceAtLeast(0L),
                    "triggerType" to safeTrigger,
                    "leftoverMode" to safeMode,
                    "totalTransactions" to archive.totalTransactions.coerceAtLeast(0),
                    "totalItemsIn" to archive.totalItemsIn.coerceAtLeast(0),
                    "totalItemsSold" to archive.totalItemsSold.coerceAtLeast(0),
                    "totalItemsLeftover" to archive.totalItemsLeftover.coerceAtLeast(0),
                    "totalOmzet" to archive.totalOmzet.coerceAtLeast(0.0),
                    "totalSellerShare" to archive.totalSellerShare.coerceAtLeast(0.0),
                    "totalAdminProfit" to archive.totalAdminProfit.coerceAtLeast(0.0),
                    "totalOperatingExpenses" to archive.totalOperatingExpenses.coerceAtLeast(0.0),
                    "netProfit" to archive.netProfit,
                    "itemsBreakdown" to itemMaps,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(updatePayload, SetOptions.merge()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.WRITE, path)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("FinancialSyncRepo", "Failed syncing $path", e)
            Result.failure(e)
        }
    }

    suspend fun fetchCloudVendorsCatalog(ownerUid: String): Result<List<CloudVendorCatalogRecord>> {
        if (ownerUid.isBlank()) return Result.failure(IllegalStateException("UID tidak valid"))
        val activeUid = auth.currentUser?.uid
        if (activeUid != ownerUid) {
            return Result.failure(IllegalStateException("Sesi akun tidak cocok untuk memuat katalog cloud."))
        }
        return try {
            val snap = vendorsCatalogCollection(ownerUid).get().await()
            val vendors = snap.documents.mapNotNull { parseVendorCatalog(it) }
            Result.success(vendors)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.LIST, "users/$ownerUid/vendors")
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAllCloudFinancialData(ownerUid: String): Result<CloudFinancialBundle> {
        if (ownerUid.isBlank()) return Result.failure(IllegalStateException("UID tidak valid"))
        val activeUid = auth.currentUser?.uid
        if (activeUid != ownerUid) {
            return Result.failure(IllegalStateException("Sesi akun tidak cocok untuk memuat data keuangan cloud."))
        }
        return try {
            val trxSnap = transactionsCollection(ownerUid).get().await()
            val expSnap = expensesCollection(ownerUid).get().await()
            val setSnap = settlementsCollection(ownerUid).get().await()

            val transactions = trxSnap.documents.mapNotNull { parseTransaction(it) }
                .sortedBy { it.timestampMillis }
            val expenses = expSnap.documents.mapNotNull { parseExpense(it) }
                .sortedBy { it.expenseDateMillis }
            val settlements = setSnap.documents.mapNotNull { parseSettlement(it) }
                .sortedBy { it.paymentDateMillis }

            Result.success(CloudFinancialBundle(transactions, expenses, settlements))
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.LIST, "users/$ownerUid/transactions")
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchAllCloudDailyClosings(ownerUid: String): Result<List<CloudDailyClosingRecord>> {
        if (ownerUid.isBlank()) return Result.failure(IllegalStateException("UID tidak valid"))
        val activeUid = auth.currentUser?.uid
        if (activeUid != ownerUid) {
            return Result.failure(IllegalStateException("Sesi akun tidak cocok untuk memuat arsip tutup buku cloud."))
        }
        return try {
            val snap = dailyClosingsCollection(ownerUid).get().await()
            val closings = snap.documents.mapNotNull { parseDailyClosing(it) }
                .sortedByDescending { it.closingDate }
            Result.success(closings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.LIST, "users/$ownerUid/daily_closings")
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- REAL-TIME SNAPSHOT LISTENERS (MetadataChanges.INCLUDE) FOR 2-HP SYNC ---

    fun observeVendorsCatalogWithMetadata(ownerUid: String): Flow<CloudSnapshotBatch<CloudVendorCatalogRecord>> {
        if (ownerUid.isBlank()) return emptyFlow()
        val path = "users/$ownerUid/vendors"
        return vendorsCatalogCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                CloudSnapshotBatch(
                    items = snapshot.documents.mapNotNull { parseVendorCatalog(it) },
                    isFromCache = snapshot.metadata.isFromCache
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeTransactionsWithMetadata(ownerUid: String): Flow<CloudSnapshotBatch<CloudTransactionRecord>> {
        if (ownerUid.isBlank()) return emptyFlow()
        val path = "users/$ownerUid/transactions"
        return transactionsCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                CloudSnapshotBatch(
                    items = snapshot.documents.mapNotNull { parseTransaction(it) }
                        .sortedBy { it.timestampMillis },
                    isFromCache = snapshot.metadata.isFromCache
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeExpensesWithMetadata(ownerUid: String): Flow<CloudSnapshotBatch<CloudExpenseRecord>> {
        if (ownerUid.isBlank()) return emptyFlow()
        val path = "users/$ownerUid/expenses"
        return expensesCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                CloudSnapshotBatch(
                    items = snapshot.documents.mapNotNull { parseExpense(it) }
                        .sortedBy { it.expenseDateMillis },
                    isFromCache = snapshot.metadata.isFromCache
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeSettlementsWithMetadata(ownerUid: String): Flow<CloudSnapshotBatch<CloudSettlementRecord>> {
        if (ownerUid.isBlank()) return emptyFlow()
        val path = "users/$ownerUid/settlements"
        return settlementsCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                CloudSnapshotBatch(
                    items = snapshot.documents.mapNotNull { parseSettlement(it) }
                        .sortedBy { it.paymentDateMillis },
                    isFromCache = snapshot.metadata.isFromCache
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeDailyClosingsWithMetadata(ownerUid: String): Flow<CloudSnapshotBatch<CloudDailyClosingRecord>> {
        if (ownerUid.isBlank()) return emptyFlow()
        val path = "users/$ownerUid/daily_closings"
        return dailyClosingsCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                CloudSnapshotBatch(
                    items = snapshot.documents.mapNotNull { parseDailyClosing(it) }
                        .sortedByDescending { it.closingDate },
                    isFromCache = snapshot.metadata.isFromCache
                )
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeUserSettingsWithMetadata(ownerUid: String): Flow<CloudUserSettingsDoc?> {
        if (ownerUid.isBlank()) return emptyFlow()
        val path = "users/$ownerUid"
        return db.collection("users").document(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { doc ->
                if (!doc.exists()) {
                    null
                } else {
                    CloudUserSettingsDoc(
                        autoClosingEnabled = doc.getBoolean("autoClosingEnabled"),
                        autoClosingHour = (doc.get("autoClosingHour") as? Number)?.toInt(),
                        autoClosingMinute = (doc.get("autoClosingMinute") as? Number)?.toInt(),
                        autoClosingLeftoverMode = doc.getString("autoClosingLeftoverMode"),
                        lastClosedDate = doc.getString("lastClosedDate"),
                        cashierShowOmzet = doc.getBoolean("cashierShowOmzet"),
                        cashierShowProfit = doc.getBoolean("cashierShowProfit"),
                        cashierShowExpenses = doc.getBoolean("cashierShowExpenses"),
                        isFromCache = doc.metadata.isFromCache
                    )
                }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.GET, path)
            }
    }

    fun observeVendorsCatalogRealtime(ownerUid: String): Flow<List<CloudVendorCatalogRecord>> =
        observeVendorsCatalogWithMetadata(ownerUid).map { it.items }

    fun observeTransactionsChronological(ownerUid: String): Flow<List<CloudTransactionRecord>> =
        observeTransactionsWithMetadata(ownerUid).map { it.items }

    fun observeExpensesRealtime(ownerUid: String): Flow<List<CloudExpenseRecord>> =
        observeExpensesWithMetadata(ownerUid).map { it.items }

    fun observeSettlementsRealtime(ownerUid: String): Flow<List<CloudSettlementRecord>> =
        observeSettlementsWithMetadata(ownerUid).map { it.items }

    private fun parseVendorCatalog(doc: DocumentSnapshot): CloudVendorCatalogRecord? {
        val vendorCode = doc.getString("vendorCode")?.trim()?.uppercase().orEmpty()
        val sellerName = doc.getString("sellerName")?.trim().orEmpty()
        if (vendorCode.isBlank() || sellerName.isBlank()) return null

        val rawProducts = doc.get("products") as? List<*> ?: emptyList<Any>()
        val products = rawProducts.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            val name = (m["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val price = (m["sellingPrice"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
            val share = ((m["basePrice"] as? Number) ?: (m["sellerShare"] as? Number))
                ?.toDouble()?.coerceIn(0.0, price) ?: 0.0
            val profit = ((m["commission"] as? Number) ?: (m["adminProfitPerUnit"] as? Number))
                ?.toDouble()?.coerceIn(0.0, price)
                ?: (price - share).coerceAtLeast(0.0)
            val stock = ((m["stock"] as? Number) ?: (m["currentStock"] as? Number))
                ?.toInt()?.coerceAtLeast(0) ?: 0
            CloudVendorProductRecord(
                name = name,
                category = (m["category"] as? String)?.trim().orEmpty().ifEmpty { "Makanan" },
                unit = (m["unit"] as? String)?.trim().orEmpty().ifEmpty { "pcs" },
                sellingPrice = price,
                sellerShare = share,
                adminProfitPerUnit = profit,
                currentStock = stock,
                isActive = (m["isActive"] as? Boolean) ?: true
            )
        }

        val rawFeeType = doc.getString("customAdminFeeType")?.trim().orEmpty()
        val rawFeeVal = (doc.get("customAdminFeeValue") as? Number)?.toDouble()

        return CloudVendorCatalogRecord(
            vendorCode = vendorCode,
            sellerName = sellerName,
            whatsappNumber = doc.getString("whatsappNumber").orEmpty(),
            address = doc.getString("address").orEmpty(),
            notes = doc.getString("notes").orEmpty(),
            sellerStatus = doc.getString("sellerStatus").orEmpty().ifEmpty { "AKTIF" },
            writeToken = doc.getString("writeToken").orEmpty(),
            customAdminFeeType = rawFeeType.takeIf { it in setOf("FIXED", "PERCENTAGE") },
            customAdminFeeValue = if (rawFeeType in setOf("FIXED", "PERCENTAGE")) rawFeeVal else null,
            sourceDeviceId = doc.getString("sourceDeviceId").orEmpty(),
            products = products,
            updatedAtMillis = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?.toDate()?.time ?: System.currentTimeMillis()
        )
    }

    private fun parseTransaction(doc: DocumentSnapshot): CloudTransactionRecord? {
        val trxNumber = doc.getString("transactionNumber")?.trim().orEmpty()
        if (trxNumber.isEmpty()) return null
        val tsMillis = (doc.get("timestampMillis") as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: return null

        val rawItems = doc.get("items") as? List<*> ?: emptyList<Any>()
        val items = rawItems.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            CloudTransactionItemRecord(
                productName = (m["productName"] as? String)?.trim().orEmpty().ifEmpty { "Produk" },
                sellerName = (m["sellerName"] as? String)?.trim().orEmpty().ifEmpty { "Penyetor" },
                unit = (m["unit"] as? String)?.trim().orEmpty().ifEmpty { "pcs" },
                unitPrice = (m["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                sellerSharePerUnit = (m["sellerSharePerUnit"] as? Number)?.toDouble() ?: 0.0,
                adminProfitPerUnit = (m["adminProfitPerUnit"] as? Number)?.toDouble() ?: 0.0,
                quantity = (m["quantity"] as? Number)?.toInt() ?: 0,
                subtotal = (m["subtotal"] as? Number)?.toDouble() ?: 0.0,
                totalSellerShare = (m["totalSellerShare"] as? Number)?.toDouble() ?: 0.0,
                totalAdminProfit = (m["totalAdminProfit"] as? Number)?.toDouble() ?: 0.0
            )
        }

        return CloudTransactionRecord(
            docId = doc.id,
            transactionNumber = trxNumber,
            timestampMillis = tsMillis,
            totalAmount = (doc.get("totalAmount") as? Number)?.toDouble() ?: 0.0,
            totalItemsCount = (doc.get("totalItemsCount") as? Number)?.toInt() ?: items.sumOf { it.quantity },
            cashReceived = (doc.get("cashReceived") as? Number)?.toDouble() ?: 0.0,
            changeGiven = (doc.get("changeGiven") as? Number)?.toDouble() ?: 0.0,
            paymentMethod = doc.getString("paymentMethod").orEmpty().ifEmpty { "Tunai" },
            totalAdminProfit = (doc.get("totalAdminProfit") as? Number)?.toDouble() ?: 0.0,
            totalSellerShare = (doc.get("totalSellerShare") as? Number)?.toDouble() ?: 0.0,
            cancelled = doc.getBoolean("cancelled") ?: false,
            cancellationReason = doc.getString("cancellationReason").orEmpty(),
            items = items
        )
    }

    private fun parseExpense(doc: DocumentSnapshot): CloudExpenseRecord? {
        val title = doc.getString("title")?.trim().orEmpty()
        if (title.isEmpty()) return null
        val ts = (doc.get("expenseDateMillis") as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: return null
        return CloudExpenseRecord(
            docId = doc.id,
            localExpenseId = (doc.get("localExpenseId") as? Number)?.toLong() ?: 0L,
            title = title,
            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
            expenseDateMillis = ts,
            notes = doc.getString("notes").orEmpty()
        )
    }

    private fun parseSettlement(doc: DocumentSnapshot): CloudSettlementRecord? {
        val sellerName = doc.getString("sellerName")?.trim().orEmpty()
        if (sellerName.isEmpty()) return null
        val ts = (doc.get("paymentDateMillis") as? Number)?.toLong()
            ?: doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: return null
        return CloudSettlementRecord(
            docId = doc.id,
            localPaymentId = (doc.get("localPaymentId") as? Number)?.toLong() ?: 0L,
            localSellerId = (doc.get("localSellerId") as? Number)?.toLong() ?: 0L,
            sellerName = sellerName,
            vendorCode = doc.getString("vendorCode").orEmpty(),
            amount = (doc.get("amount") as? Number)?.toDouble() ?: 0.0,
            paymentDateMillis = ts,
            paymentMethod = doc.getString("paymentMethod").orEmpty().ifEmpty { "Tunai" },
            notes = doc.getString("notes").orEmpty(),
            status = doc.getString("status").orEmpty().ifEmpty { "SEBAGIAN" }
        )
    }

    private fun parseDailyClosing(doc: DocumentSnapshot): CloudDailyClosingRecord? {
        val closingDate = doc.getString("closingDate")?.trim().orEmpty()
        if (!closingDate.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return null

        val rawItems = doc.get("itemsBreakdown") as? List<*> ?: emptyList<Any>()
        val breakdown = rawItems.mapNotNull { entry ->
            val m = entry as? Map<*, *> ?: return@mapNotNull null
            DailyClosingItemRecord(
                productId = (m["productId"] as? Number)?.toLong() ?: 0L,
                productName = (m["productName"] as? String)?.trim().orEmpty().ifEmpty { "Produk" },
                sellerName = (m["sellerName"] as? String)?.trim().orEmpty().ifEmpty { "Penyetor" },
                unit = (m["unit"] as? String)?.trim().orEmpty().ifEmpty { "pcs" },
                qtyIn = (m["qtyIn"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                qtySold = (m["qtySold"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                qtyLeftover = (m["qtyLeftover"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
                omzet = (m["omzet"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0,
                sellerShare = (m["sellerShare"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0,
                adminProfit = (m["adminProfit"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
            )
        }

        return CloudDailyClosingRecord(
            closingDate = closingDate,
            closedAtMillis = (doc.get("closedAtMillis") as? Number)?.toLong() ?: System.currentTimeMillis(),
            triggerType = doc.getString("triggerType").orEmpty().ifEmpty { DailyClosingTriggerType.AUTO },
            leftoverMode = doc.getString("leftoverMode").orEmpty().ifEmpty { AutoClosingLeftoverMode.RESET_ZERO },
            totalTransactions = (doc.get("totalTransactions") as? Number)?.toInt() ?: 0,
            totalItemsIn = (doc.get("totalItemsIn") as? Number)?.toInt() ?: 0,
            totalItemsSold = (doc.get("totalItemsSold") as? Number)?.toInt() ?: 0,
            totalItemsLeftover = (doc.get("totalItemsLeftover") as? Number)?.toInt() ?: 0,
            totalOmzet = (doc.get("totalOmzet") as? Number)?.toDouble() ?: 0.0,
            totalSellerShare = (doc.get("totalSellerShare") as? Number)?.toDouble() ?: 0.0,
            totalAdminProfit = (doc.get("totalAdminProfit") as? Number)?.toDouble() ?: 0.0,
            totalOperatingExpenses = (doc.get("totalOperatingExpenses") as? Number)?.toDouble() ?: 0.0,
            netProfit = (doc.get("netProfit") as? Number)?.toDouble() ?: 0.0,
            itemsBreakdown = breakdown
        )
    }
}
