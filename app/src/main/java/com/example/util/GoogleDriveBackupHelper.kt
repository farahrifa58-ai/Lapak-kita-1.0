package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Play-Policy-compliant Google Drive backup & restore helper using:
 * 1) Android Storage Access Framework (CreateDocument / OpenDocument) which natively integrates
 *    with Google Drive without requiring any dangerous storage permissions.
 * 2) Direct 1-tap share to the Google Drive app (`com.google.android.apps.docs`) via FileProvider.
 */
object GoogleDriveBackupHelper {

    private const val GOOGLE_DRIVE_PACKAGE = "com.google.android.apps.docs"

    fun defaultBackupFileName(shopName: String): String {
        val slug = shopName.trim()
            .replace(Regex("[^a-zA-Z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { "LapakKita" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        return "LapakKita_Backup_${slug}_${stamp}.json"
    }

    suspend fun writeJsonToUri(
        context: Context,
        uri: Uri,
        json: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Tidak dapat membuka file tujuan di Google Drive / penyimpanan.")
            out.use { stream ->
                stream.write(json.toByteArray(Charsets.UTF_8))
                stream.flush()
            }
        }
    }

    suspend fun readJsonFromUri(
        context: Context,
        uri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Tidak dapat membaca file cadangan dari Google Drive.")
            val content = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (content.isBlank()) {
                throw IllegalArgumentException("File cadangan kosong atau tidak valid.")
            }
            content
        }
    }

    suspend fun shareBackupFileToGoogleDrive(
        context: Context,
        json: String,
        shopName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
            // Keep cache tidy by pruning older temporary backup files
            backupDir.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(3)
                ?.forEach { runCatching { it.delete() } }

            val fileName = defaultBackupFileName(shopName)
            val file = File(backupDir, fileName)
            file.writeText(json, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val driveIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_TITLE, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(GOOGLE_DRIVE_PACKAGE)
            }

            withContext(Dispatchers.Main) {
                val pm = context.packageManager
                if (driveIntent.resolveActivity(pm) != null) {
                    context.startActivity(driveIntent)
                } else {
                    val fallback = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, fileName)
                        putExtra(Intent.EXTRA_TITLE, fileName)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(fallback, "Simpan Cadangan ke Google Drive")
                    )
                }
            }
            fileName
        }
    }
}
