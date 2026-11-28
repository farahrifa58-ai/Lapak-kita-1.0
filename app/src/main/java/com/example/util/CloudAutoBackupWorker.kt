package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.config.LicenseStorage
import com.example.data.database.LapakDatabase
import com.example.data.firebase.FinancialSyncRepository
import com.example.data.firebase.VendorSyncRepository
import com.example.data.model.SellerPeriodReportBuilder
import com.example.data.repository.LapakRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Periodic Android WorkManager worker scheduled every 1 hour (when NetworkType.CONNECTED)
 * to automatically back up all local UID-scoped Room data (Sellers, Products, Transactions,
 * Operating Expenses, Seller Settlements, and Daily Closing Archives) to the user's Firestore account.
 */
class CloudAutoBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val currentUser = Firebase.auth.currentUser ?: return@withContext Result.success()
        val uid = currentUser.uid
        if (uid.isBlank()) return@withContext Result.success()

        val licenseStorage = LicenseStorage(applicationContext)
        if (!licenseStorage.isLocallyLicensedFor(uid)) {
            return@withContext Result.success()
        }

        val deviceId = licenseStorage.getDeviceId()
        return@withContext try {
            val primaryDbId = applicationContext.getString(R.string.firestore_database_id).trim()
            val firestoreDb = FirebaseFirestore.getInstance(primaryDbId)
            val vendorSyncRepo = VendorSyncRepository(firestoreDb)
            val financialSyncRepo = FinancialSyncRepository(firestoreDb)

            val roomDb = LapakDatabase.getDatabaseForUser(applicationContext, uid)
            val repository = LapakRepository(roomDb.lapakDao())
            repository.ensureAllSellersHaveVendorCodes()

            var hadFailure = false

            // 1. Sync Sellers & Products (User catalog + Public Web Portal)
            val sellers = repository.allSellers.first()
            for (seller in sellers) {
                val snapshot = repository.getSellerSyncSnapshotDirect(seller.id) ?: continue
                val pubRes = vendorSyncRepo.syncVendorToFirestore(
                    sellerDetail = snapshot.sellerDetail,
                    sellerProducts = snapshot.sellerProducts,
                    shopProfile = snapshot.shopProfile,
                    appSettings = snapshot.appSettings,
                    expectedUid = uid
                )
                val userRes = financialSyncRepo.syncVendorCatalogToUserCloud(
                    sellerDetail = snapshot.sellerDetail,
                    sellerProducts = snapshot.sellerProducts,
                    sourceDeviceId = deviceId,
                    expectedUid = uid
                )
                if (pubRes.isFailure || userRes.isFailure) hadFailure = true
            }

            // 2. Sync Transactions, Operating Expenses, and Seller Settlements
            val finSnapshot = repository.getFinancialSyncSnapshot()
            val sellersById = finSnapshot.sellers.associateBy { it.id }
            val sellerStatusById = repository.sellersWithStats.first()
                .associate { it.seller.id to it.paymentStatus }

            for (trx in finSnapshot.transactions) {
                val items = finSnapshot.itemsByTransactionId[trx.id].orEmpty()
                if (financialSyncRepo.syncTransactionToFirestore(trx, items, expectedUid = uid).isFailure) {
                    hadFailure = true
                }
            }
            for (exp in finSnapshot.expenses) {
                if (financialSyncRepo.syncExpenseToFirestore(exp, expectedUid = uid).isFailure) {
                    hadFailure = true
                }
            }
            for (pay in finSnapshot.payments) {
                val seller = sellersById[pay.sellerId]
                val status = sellerStatusById[pay.sellerId] ?: "SEBAGIAN"
                if (financialSyncRepo.syncSettlementToFirestore(pay, seller, status, expectedUid = uid).isFailure) {
                    hadFailure = true
                }
            }

            // 3. Sync Daily Closing Archives
            val archives = repository.allDailyClosingArchives.first()
            for (archive in archives) {
                val parsedItems = LapakRepository.decodeClosingItemsJson(archive.itemsBreakdownJson)
                if (financialSyncRepo.syncDailyClosingToFirestore(archive, parsedItems, expectedUid = uid).isFailure) {
                    hadFailure = true
                }
            }

            // 4. Sync User Settings
            val settings = repository.appSettings.first()
            financialSyncRepo.syncUserSettingsToFirestore(settings, expectedUid = uid)

            if (hadFailure) {
                licenseStorage.setPendingCloudSync(uid, true)
                Result.retry()
            } else {
                licenseStorage.setPendingCloudSync(uid, false)
                licenseStorage.saveLastCloudSyncAt(uid, System.currentTimeMillis())
                Result.success()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Hourly cloud auto-backup retry scheduled: ${e.message}")
            licenseStorage.setPendingCloudSync(uid, true)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CloudAutoBackupWorker"

        private fun uniqueWorkName(uid: String): String {
            val safeUid = LicenseStorage.sanitizeUidForStorageKey(uid)
            return "lapak_hourly_cloud_backup_$safeUid"
        }

        fun scheduleHourlyAutoBackup(context: Context, uid: String) {
            if (uid.isBlank()) return
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = PeriodicWorkRequestBuilder<CloudAutoBackupWorker>(
                    1L,
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .addTag(TAG)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    uniqueWorkName(uid),
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            }.onFailure { err ->
                Log.w(TAG, "Failed to schedule hourly backup worker: ${err.message}")
            }
        }

        fun cancelHourlyAutoBackup(context: Context, uid: String) {
            if (uid.isBlank()) return
            runCatching {
                WorkManager.getInstance(context.applicationContext)
                    .cancelUniqueWork(uniqueWorkName(uid))
            }
        }
    }
}
