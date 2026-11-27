package com.example.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.model.LapakTransaction
import com.example.data.model.PrinterPaperSize
import com.example.data.model.Seller
import com.example.data.model.SellerPayment
import com.example.data.model.ShopProfile
import com.example.data.model.SoldItemSummary
import com.example.data.model.TransactionItem
import com.example.ui.viewmodel.LapakViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

/**
 * Real Android Bluetooth ESC/POS thermal printer utility supporting paired 58mm and 80mm
 * Bluetooth thermal printers via standard RFCOMM Serial Port Profile (SPP).
 */
object BluetoothThermalPrinter {

    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasBluetoothConnectPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter
    }

    fun isBluetoothSupported(context: Context): Boolean {
        return getBluetoothAdapter(context) != null
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val adapter = getBluetoothAdapter(context) ?: return false
        return runCatching { adapter.isEnabled }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun getPairedDevices(context: Context): Result<List<BluetoothPrinterDevice>> {
        if (!hasBluetoothConnectPermission(context)) {
            return Result.failure(SecurityException("Izin Bluetooth (BLUETOOTH_CONNECT) belum diberikan."))
        }
        val adapter = getBluetoothAdapter(context)
            ?: return Result.failure(IllegalStateException("Perangkat ini tidak mendukung Bluetooth."))
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth belum aktif. Nyalakan Bluetooth di HP terlebih dahulu."))
        }

        return try {
            val devices = adapter.bondedDevices
                .orEmpty()
                .map { device ->
                    BluetoothPrinterDevice(
                        name = device.name?.trim().takeIf { !it.isNullOrBlank() } ?: "Perangkat Bluetooth",
                        address = device.address.orEmpty()
                    )
                }
                .filter { it.address.isNotBlank() }
                .sortedBy { it.name.lowercase(Locale.getDefault()) }
            Result.success(devices)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Izin akses Bluetooth ditolak oleh sistem."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printEscPosText(
        context: Context,
        macAddress: String,
        formattedText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (macAddress.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Alamat MAC printer belum dipilih."))
        }
        if (!hasBluetoothConnectPermission(context)) {
            return@withContext Result.failure(SecurityException("Izin Bluetooth (BLUETOOTH_CONNECT) diperlukan untuk mencetak."))
        }
        val adapter = getBluetoothAdapter(context)
            ?: return@withContext Result.failure(IllegalStateException("Perangkat tidak mendukung Bluetooth."))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth sedang mati. Aktifkan Bluetooth terlebih dahulu."))
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(macAddress)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(IllegalArgumentException("Alamat printer Bluetooth tidak valid: $macAddress"))
        }

        var socket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("Gagal menyiapkan koneksi printer Bluetooth: ${e.message}"))
        }

        try {
            runCatching { adapter.cancelDiscovery() }
            try {
                socket.connect()
            } catch (primaryErr: IOException) {
                runCatching { socket.close() }
                // Fallback insecure RFCOMM socket for older generic thermal printers
                socket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
            }

            val payload = buildEscPosBytes(formattedText)
            val out = socket.outputStream
            out.write(payload)
            out.flush()
            // Brief settle pause before closing RFCOMM stream so printer drains buffer
            Thread.sleep(250L)
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("Izin Bluetooth ditolak saat menghubungkan ke printer."))
        } catch (e: Exception) {
            Result.failure(IOException("Gagal terhubung ke printer (${device.name ?: macAddress}). Pastikan printer menyala & berada dalam jangkauan."))
        } finally {
            runCatching { socket.close() }
        }
    }

    fun buildEscPosBytes(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        // ESC @ (Initialize printer)
        out.write(byteArrayOf(0x1B, 0x40))
        // ESC a 0 (Left align)
        out.write(byteArrayOf(0x1B, 0x61, 0x00))
        val cleanAscii = sanitizeForThermal(text)
        out.write(cleanAscii.toByteArray(Charsets.UTF_8))
        // Feed 3 lines
        out.write("\n\n\n".toByteArray(Charsets.UTF_8))
        // GS V A 16 (Partial cut if supported; ignored safely by tear-bar printers)
        out.write(byteArrayOf(0x1D, 0x56, 0x41, 0x10))
        return out.toByteArray()
    }

    private fun sanitizeForThermal(input: String): String {
        return input
            .replace("•", "-")
            .replace("—", "-")
            .replace("–", "-")
            .replace("Rp ", "Rp")
            .replace("\r\n", "\n")
    }

    private fun centerText(text: String, width: Int): String {
        val trimmed = text.trim()
        if (trimmed.length >= width) return wrapLine(trimmed, width)
        val pad = ((width - trimmed.length) / 2).coerceAtLeast(0)
        return " ".repeat(pad) + trimmed
    }

    private fun twoColumns(left: String, right: String, width: Int): String {
        val cleanLeft = left.trim()
        val cleanRight = right.trim()
        val minSpaces = 1
        if (cleanLeft.length + cleanRight.length + minSpaces <= width) {
            val gap = width - cleanLeft.length - cleanRight.length
            return cleanLeft + " ".repeat(gap) + cleanRight
        }
        val maxLeft = (width - cleanRight.length - 1).coerceAtLeast(8)
        return cleanLeft.take(maxLeft) + " " + cleanRight
    }

    private fun wrapLine(text: String, width: Int): String {
        val words = text.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return ""
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.isEmpty()) {
                if (word.length <= width) {
                    current.append(word)
                } else {
                    var idx = 0
                    while (idx < word.length) {
                        lines.add(word.substring(idx, (idx + width).coerceAtMost(word.length)))
                        idx += width
                    }
                }
            } else if (current.length + 1 + word.length <= width) {
                current.append(' ').append(word)
            } else {
                lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines.joinToString("\n")
    }

    fun formatCustomerReceiptEscPos(
        transaction: LapakTransaction,
        items: List<TransactionItem>,
        shopProfile: ShopProfile,
        paperSize: String = PrinterPaperSize.MM_58
    ): String {
        val w = PrinterPaperSize.columnsFor(paperSize)
        val dash = "-".repeat(w)
        val eq = "=".repeat(w)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(transaction.timestamp))

        return buildString {
            appendLine(centerText(shopProfile.shopName.uppercase(Locale.getDefault()), w))
            if (shopProfile.address.isNotBlank()) {
                appendLine(centerText(shopProfile.address, w))
            }
            if (shopProfile.whatsappNumber.isNotBlank()) {
                appendLine(centerText("WA: ${shopProfile.whatsappNumber}", w))
            }
            appendLine(eq)
            appendLine(twoColumns("No. TRX", transaction.transactionNumber, w))
            appendLine(twoColumns("Waktu", dateStr, w))
            appendLine(twoColumns("Metode", transaction.paymentMethod, w))
            appendLine(dash)
            items.forEach { item ->
                appendLine(wrapLine(item.productName, w))
                val qtyPrice = "${item.quantity}x ${LapakViewModel.formatRupiah(item.unitPrice)}"
                val sub = LapakViewModel.formatRupiah(item.subtotal)
                appendLine(twoColumns("  $qtyPrice", sub, w))
            }
            appendLine(dash)
            appendLine(twoColumns("TOTAL", LapakViewModel.formatRupiah(transaction.totalAmount), w))
            appendLine(twoColumns("Bayar", LapakViewModel.formatRupiah(transaction.cashReceived), w))
            appendLine(twoColumns("Kembali", LapakViewModel.formatRupiah(transaction.changeGiven), w))
            appendLine(eq)
            if (shopProfile.footerMessage.isNotBlank()) {
                appendLine(centerText(shopProfile.footerMessage, w))
            }
        }
    }

    fun formatSellerPayoutReceiptEscPos(
        payment: SellerPayment,
        seller: Seller,
        shopProfile: ShopProfile,
        remainingBalance: Double,
        soldItems: List<SoldItemSummary> = emptyList(),
        paperSize: String = PrinterPaperSize.MM_58
    ): String {
        val w = PrinterPaperSize.columnsFor(paperSize)
        val dash = "-".repeat(w)
        val eq = "=".repeat(w)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(payment.paymentDate))

        return buildString {
            appendLine(centerText(shopProfile.shopName.uppercase(Locale.getDefault()), w))
            appendLine(centerText("BUKTI BAYAR PENYETOR", w))
            appendLine(eq)
            appendLine(twoColumns("No", "PAY-${payment.id}", w))
            appendLine(twoColumns("Waktu", dateStr, w))
            appendLine(twoColumns("Penyetor", seller.name, w))
            appendLine(twoColumns("Metode", payment.paymentMethod, w))
            if (payment.notes.isNotBlank()) {
                appendLine(wrapLine("Catatan: ${payment.notes}", w))
            }
            if (soldItems.isNotEmpty()) {
                appendLine(dash)
                appendLine("RINCIAN TERJUAL:")
                soldItems.forEach { item ->
                    appendLine(wrapLine(item.productName, w))
                    appendLine(
                        twoColumns(
                            "  ${item.quantity} ${item.unit} x ${LapakViewModel.formatRupiah(item.sellerSharePerUnit)}",
                            LapakViewModel.formatRupiah(item.totalSellerShare),
                            w
                        )
                    )
                }
            }
            appendLine(dash)
            appendLine(twoColumns("DIBAYAR", LapakViewModel.formatRupiah(payment.amount), w))
            appendLine(twoColumns("SISA HAK", LapakViewModel.formatRupiah(remainingBalance), w))
            appendLine(eq)
            appendLine(centerText("Terima kasih atas kerja samanya", w))
        }
    }

    fun formatTestPrintEscPos(
        shopProfile: ShopProfile,
        printerName: String,
        paperSize: String = PrinterPaperSize.MM_58
    ): String {
        val w = PrinterPaperSize.columnsFor(paperSize)
        val dash = "-".repeat(w)
        val eq = "=".repeat(w)
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        return buildString {
            appendLine(centerText(shopProfile.shopName.uppercase(Locale.getDefault()), w))
            appendLine(centerText("TES CETAK PRINTER", w))
            appendLine(eq)
            appendLine(twoColumns("Printer", printerName.ifBlank { "Thermal BT" }, w))
            appendLine(twoColumns("Kertas", PrinterPaperSize.label(paperSize), w))
            appendLine(twoColumns("Lebar", "$w karakter", w))
            appendLine(twoColumns("Waktu", sdf.format(Date()), w))
            appendLine(dash)
            appendLine(twoColumns("Risoles Mayo 2x", "Rp10.000", w))
            appendLine(twoColumns("Lemper Ayam 1x", "Rp5.000", w))
            appendLine(dash)
            appendLine(twoColumns("TOTAL", "Rp15.000", w))
            appendLine(eq)
            appendLine(centerText("Printer siap digunakan!", w))
        }
    }
}
