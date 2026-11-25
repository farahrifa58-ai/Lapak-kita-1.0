package com.example.data.firebase

import android.util.Log
import com.example.data.model.AdminFeeCalculator
import com.example.data.model.AppSettings
import com.example.data.model.ShopProfile
import com.example.data.model.VendorCodeGenerator
import com.example.data.repository.ProductDetailItem
import com.example.data.repository.SellerDetailItem
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class WebNewProductPayload(
    val name: String,
    val category: String,
    val unit: String,
    val sellingPrice: Double,
    val initialStock: Int
)

data class WebEditedProductPayload(
    val localProductId: Long,
    val name: String,
    val category: String,
    val unit: String,
    val sellingPrice: Double
)

data class WebStockAdditionPayload(
    val localProductId: Long,
    val addedQty: Int
)

data class VendorSubmissionRemote(
    val submissionId: String,
    val ownerUid: String,
    val vendorCode: String,
    val status: String,
    val newProducts: List<WebNewProductPayload>,
    val editedProducts: List<WebEditedProductPayload>,
    val stockAdditions: List<WebStockAdditionPayload>,
    val note: String,
    val createdAtMillis: Long
)

class VendorSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val vendorsCollection = db.collection("lapak_vendors")
    private val submissionsCollection = db.collection("lapak_vendor_submissions")

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("Admin harus masuk dengan akun Google terlebih dahulu.")

    suspend fun syncVendorToFirestore(
        sellerDetail: SellerDetailItem,
        sellerProducts: List<ProductDetailItem>,
        shopProfile: ShopProfile,
        appSettings: AppSettings,
        lastProcessedSubmissionId: String = ""
    ): Result<Unit> {
        val seller = sellerDetail.seller
        val vendorCode = seller.vendorCode.trim().uppercase()
        val writeToken = seller.writeToken.trim()
        if (!VendorCodeGenerator.isValidVendorCode(vendorCode) || !VendorCodeGenerator.isValidWriteToken(writeToken)) {
            return Result.failure(IllegalArgumentException("Kode penyetor atau token tulis tidak valid"))
        }

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val (feeType, feeValue) = AdminFeeCalculator.resolveEffectiveFee(seller, appSettings)
        val safeShopName = shopProfile.shopName.trim().ifEmpty { "Lapak Kita" }.take(100)
        val safeSellerName = seller.name.trim().ifEmpty { "Penyetor" }.take(100)
        val safeStatus = seller.status.name.takeIf { it in setOf("AKTIF", "LIBUR", "NONAKTIF") } ?: "AKTIF"

        val productMaps = sellerProducts.take(100).map { detail ->
            val p = detail.product
            val safePrice = p.sellingPrice.coerceAtLeast(0.0)
            val safeShare = p.sellerShare.coerceIn(0.0, safePrice)
            val safeAdminProfit = (safePrice - safeShare).coerceAtLeast(0.0)
            mapOf(
                "localProductId" to p.id,
                "name" to p.name.trim().ifEmpty { "Produk" }.take(100),
                "category" to p.category.trim().ifEmpty { "Makanan" }.take(50),
                "unit" to p.unit.trim().ifEmpty { "pcs" }.take(20),
                "sellingPrice" to safePrice,
                "sellerShare" to safeShare,
                "adminProfitPerUnit" to safeAdminProfit,
                "currentStock" to detail.currentStock.coerceAtLeast(0),
                "isActive" to p.isActive
            )
        }

        val vendorPath = "lapak_vendors/$vendorCode"
        val vendorRef = vendorsCollection.document(vendorCode)
        val authPath = "lapak_vendors/$vendorCode/private/auth"
        val authRef = vendorRef.collection("private").document("auth")

        return try {
            val existingVendor = vendorRef.get().await()
            if (!existingVendor.exists()) {
                val createData = mapOf(
                    "ownerUid" to uid,
                    "vendorCode" to vendorCode,
                    "localSellerId" to seller.id.coerceAtLeast(0L),
                    "shopName" to safeShopName,
                    "sellerName" to safeSellerName,
                    "sellerStatus" to safeStatus,
                    "adminFeeType" to feeType,
                    "adminFeeValue" to feeValue,
                    "summaryTotalStock" to sellerDetail.totalStock.coerceAtLeast(0),
                    "summaryTotalSold" to sellerDetail.totalSoldQty.coerceAtLeast(0),
                    "summaryUnpaidBalance" to sellerDetail.remainingBalance.coerceAtLeast(0.0),
                    "products" to productMaps,
                    "lastProcessedSubmissionId" to lastProcessedSubmissionId.take(128),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                vendorRef.set(createData).await()
            } else {
                val existingOwner = existingVendor.getString("ownerUid")
                if (existingOwner != null && existingOwner != uid) {
                    return Result.failure(IllegalStateException("Kode penyetor $vendorCode dimiliki akun lain"))
                }
                val preservedSubmissionId = lastProcessedSubmissionId.ifBlank {
                    existingVendor.getString("lastProcessedSubmissionId").orEmpty()
                }.take(128)
                val updateData = mapOf(
                    "localSellerId" to seller.id.coerceAtLeast(0L),
                    "shopName" to safeShopName,
                    "sellerName" to safeSellerName,
                    "sellerStatus" to safeStatus,
                    "adminFeeType" to feeType,
                    "adminFeeValue" to feeValue,
                    "summaryTotalStock" to sellerDetail.totalStock.coerceAtLeast(0),
                    "summaryTotalSold" to sellerDetail.totalSoldQty.coerceAtLeast(0),
                    "summaryUnpaidBalance" to sellerDetail.remainingBalance.coerceAtLeast(0.0),
                    "products" to productMaps,
                    "lastProcessedSubmissionId" to preservedSubmissionId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                vendorRef.update(updateData).await()
            }

            // Guarantee private writeToken document exists & is 100% identical to seller.writeToken
            val existingAuth = authRef.get().await()
            if (!existingAuth.exists()) {
                authRef.set(
                    mapOf(
                        "ownerUid" to uid,
                        "writeToken" to writeToken,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existingAuthOwner = existingAuth.getString("ownerUid")
                if (existingAuthOwner != null && existingAuthOwner != uid) {
                    return Result.failure(IllegalStateException("Token akses penyetor $vendorCode dimiliki akun lain"))
                }
                if (existingAuth.getString("writeToken") != writeToken) {
                    authRef.update(
                        mapOf(
                            "writeToken" to writeToken,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.WRITE, vendorPath)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Log.e("VendorSyncRepository", "Failed syncing $vendorPath / $authPath", e)
            Result.failure(e)
        }
    }

    suspend fun deleteVendorFromFirestore(vendorCode: String): Result<Unit> {
        if (!VendorCodeGenerator.isValidVendorCode(vendorCode)) return Result.success(Unit)
        if (auth.currentUser == null) return Result.success(Unit)

        val vendorPath = "lapak_vendors/$vendorCode"
        return try {
            val vendorRef = vendorsCollection.document(vendorCode)
            runCatching { vendorRef.collection("private").document("auth").delete().await() }
            runCatching { vendorRef.delete().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            try {
                handleFirestoreError(e, OperationType.DELETE, vendorPath)
            } catch (wrapped: IllegalStateException) {
                Result.failure(wrapped)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observePendingSubmissions(ownerUid: String): Flow<List<VendorSubmissionRemote>> {
        val path = "lapak_vendor_submissions"
        return submissionsCollection
            .whereEqualTo("ownerUid", ownerUid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { doc ->
                    val status = doc.getString("status") ?: return@mapNotNull null
                    if (status != "PENDING") return@mapNotNull null
                    parseSubmission(doc)
                }.sortedBy { it.createdAtMillis }
            }
            .catch { cause ->
                if (cause is CancellationException && cause.cause == null) {
                    throw cause
                }
                val rootError = (cause as? CancellationException)?.cause ?: cause
                handleFirestoreError(rootError, OperationType.LIST, path)
            }
    }

    suspend fun markSubmissionStatus(
        submissionId: String,
        status: String
    ): Result<Unit> {
        val path = "lapak_vendor_submissions/$submissionId"
        return try {
            requireUserId()
            submissionsCollection.document(submissionId).update(
                mapOf(
                    "status" to status,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
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
            Result.failure(e)
        }
    }

    private fun parseSubmission(doc: DocumentSnapshot): VendorSubmissionRemote? {
        val ownerUid = doc.getString("ownerUid") ?: return null
        val vendorCode = doc.getString("vendorCode") ?: return null
        val status = doc.getString("status") ?: return null
        val note = doc.getString("note").orEmpty()
        val createdAtMillis = doc.getTimestamp(
            "createdAt",
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )?.toDate()?.time ?: System.currentTimeMillis()

        val rawNew = doc.get("newProducts") as? List<*> ?: emptyList<Any>()
        val newProducts = rawNew.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val name = (map["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val category = (map["category"] as? String)?.trim().orEmpty().ifEmpty { "Makanan" }
            val unit = (map["unit"] as? String)?.trim().orEmpty().ifEmpty { "pcs" }
            val sellingPrice = (map["sellingPrice"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
            val initialStock = (map["initialStock"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            WebNewProductPayload(name, category, unit, sellingPrice, initialStock)
        }

        val rawEdited = doc.get("editedProducts") as? List<*> ?: emptyList<Any>()
        val editedProducts = rawEdited.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val localId = (map["localProductId"] as? Number)?.toLong() ?: return@mapNotNull null
            val name = (map["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            val category = (map["category"] as? String)?.trim().orEmpty().ifEmpty { "Makanan" }
            val unit = (map["unit"] as? String)?.trim().orEmpty().ifEmpty { "pcs" }
            val sellingPrice = (map["sellingPrice"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: 0.0
            WebEditedProductPayload(localId, name, category, unit, sellingPrice)
        }

        val rawStock = doc.get("stockAdditions") as? List<*> ?: emptyList<Any>()
        val stockAdditions = rawStock.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val localId = (map["localProductId"] as? Number)?.toLong() ?: return@mapNotNull null
            val addedQty = (map["addedQty"] as? Number)?.toInt() ?: 0
            if (addedQty <= 0) return@mapNotNull null
            WebStockAdditionPayload(localId, addedQty)
        }

        return VendorSubmissionRemote(
            submissionId = doc.id,
            ownerUid = ownerUid,
            vendorCode = vendorCode,
            status = status,
            newProducts = newProducts,
            editedProducts = editedProducts,
            stockAdditions = stockAdditions,
            note = note,
            createdAtMillis = createdAtMillis
        )
    }
}
