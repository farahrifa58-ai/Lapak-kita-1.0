package com.example.data.firebase

import android.util.Log
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
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Admin harus masuk terlebih dahulu.")

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
        sourceDeviceId: String
    ): Result<Unit> {
        val seller = sellerDetail.seller
        val vendorCode = seller.vendorCode.trim().uppercase()
        val writeToken = seller.writeToken.trim()
        if (!VendorCodeGenerator.isValidVendorCode(vendorCode) || !VendorCodeGenerator.isValidWriteToken(writeToken)) {
            return Result.failure(IllegalArgumentException("Kode penyetor atau token tidak valid"))
        }

        val uid = try {
            requireUserId()
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
            mapOf(
                "name" to p.name.trim().ifEmpty { "Produk" }.take(100),
                "category" to p.category.trim().ifEmpty { "Makanan" }.take(50),
                "unit" to p.unit.trim().ifEmpty { "pcs" }.take(20),
                "sellingPrice" to safePrice,
                "sellerShare" to safeShare,
                "adminProfitPerUnit" to safeProfit,
                "currentStock" to detail.currentStock.coerceAtLeast(0),
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
                docRef.update(updatePayload).await()
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

    suspend fun deleteVendorCatalogFromUserCloud(vendorCode: String): Result<Unit> {
        val cleanCode = vendorCode.trim().uppercase()
        if (!VendorCodeGenerator.isValidVendorCode(cleanCode)) return Result.success(Unit)
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
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
        items: List<TransactionItem>
    ): Result<Unit> {
        val uid = try {
            requireUserId()
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
                docRef.update(updatePayload).await()
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

    suspend fun syncExpenseToFirestore(expense: OperatingExpense): Result<Unit> {
        val uid = try {
            requireUserId()
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
                docRef.update(
                    mapOf(
                        "title" to expense.title.trim().ifEmpty { "Biaya Operasional" }.take(120),
                        "amount" to expense.amount.coerceAtLeast(0.0),
                        "expenseDateMillis" to expense.expenseDate.coerceAtLeast(0L),
                        "notes" to expense.notes.trim().take(300),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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

    suspend fun deleteExpenseFromFirestore(expense: OperatingExpense): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
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
        status: String
    ): Result<Unit> {
        val uid = try {
            requireUserId()
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
                docRef.update(
                    mapOf(
                        "sellerName" to (seller?.name?.trim()?.ifEmpty { "Penyetor" } ?: "Penyetor").take(100),
                        "vendorCode" to (seller?.vendorCode?.trim() ?: "").take(20),
                        "amount" to payment.amount.coerceAtLeast(0.0),
                        "paymentDateMillis" to payment.paymentDate.coerceAtLeast(0L),
                        "paymentMethod" to payment.paymentMethod.trim().ifEmpty { "Tunai" }.take(40),
                        "notes" to payment.notes.trim().take(300),
                        "status" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
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

    suspend fun fetchAllCloudFinancialData(ownerUid: String): Result<CloudFinancialBundle> {
        if (ownerUid.isBlank()) return Result.failure(IllegalStateException("UID tidak valid"))
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

    // --- REAL-TIME SNAPSHOT LISTENERS (MetadataChanges.INCLUDE) FOR 2-HP SYNC ---

    fun observeVendorsCatalogRealtime(ownerUid: String): Flow<List<CloudVendorCatalogRecord>> {
        val path = "users/$ownerUid/vendors"
        return vendorsCatalogCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseVendorCatalog(it) }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeTransactionsChronological(ownerUid: String): Flow<List<CloudTransactionRecord>> {
        val path = "users/$ownerUid/transactions"
        return transactionsCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseTransaction(it) }
                    .sortedBy { it.timestampMillis }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeExpensesRealtime(ownerUid: String): Flow<List<CloudExpenseRecord>> {
        val path = "users/$ownerUid/expenses"
        return expensesCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseExpense(it) }
                    .sortedBy { it.expenseDateMillis }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    fun observeSettlementsRealtime(ownerUid: String): Flow<List<CloudSettlementRecord>> {
        val path = "users/$ownerUid/settlements"
        return settlementsCollection(ownerUid)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                snapshot.documents.mapNotNull { parseSettlement(it) }
                    .sortedBy { it.paymentDateMillis }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) throw cause
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

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
            val share = (m["sellerShare"] as? Number)?.toDouble()?.coerceIn(0.0, price) ?: 0.0
            val profit = (m["adminProfitPerUnit"] as? Number)?.toDouble()?.coerceAtLeast(0.0)
                ?: (price - share).coerceAtLeast(0.0)
            CloudVendorProductRecord(
                name = name,
                category = (m["category"] as? String)?.trim().orEmpty().ifEmpty { "Makanan" },
                unit = (m["unit"] as? String)?.trim().orEmpty().ifEmpty { "pcs" },
                sellingPrice = price,
                sellerShare = share,
                adminProfitPerUnit = profit,
                currentStock = (m["currentStock"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0,
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
}
