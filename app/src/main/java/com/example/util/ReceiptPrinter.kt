package com.example.util

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.model.LapakTransaction
import com.example.data.model.Seller
import com.example.data.model.SellerPayment
import com.example.data.model.ShopProfile
import com.example.data.model.TransactionItem
import com.example.ui.viewmodel.LapakViewModel
import java.text.SimpleDateFormat
import java.util.*

object ReceiptPrinter {

    /**
     * Prints an HTML receipt to standard printer or thermal printer via Android PrintManager.
     */
    fun printHtml(context: Context, htmlContent: String, jobName: String) {
        val activity = context as? Activity
        val runner = {
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager?.print(jobName, printAdapter, attributes)
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
        }

        if (activity != null) {
            activity.runOnUiThread(runner)
        } else {
            runner()
        }
    }

    /**
     * Generates thermal-styled HTML for customer purchase receipt.
     * Note: NO seller name is included (strict customer privacy).
     */
    fun buildCustomerReceiptHtml(
        transaction: LapakTransaction,
        items: List<TransactionItem>,
        shopProfile: ShopProfile
    ): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = sdf.format(Date(transaction.timestamp))

        val itemsHtml = StringBuilder()
        for (item in items) {
            itemsHtml.append("""
                <tr>
                    <td style="padding: 4px 0; font-size: 13px;">
                        <b>${item.productName}</b><br>
                        <span style="font-size: 11px; color: #555;">${item.quantity} x ${LapakViewModel.formatRupiah(item.unitPrice)}</span>
                    </td>
                    <td style="padding: 4px 0; text-align: right; vertical-align: top; font-size: 13px;">
                        ${LapakViewModel.formatRupiah(item.subtotal)}
                    </td>
                </tr>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Courier New', Courier, monospace;
                        font-size: 12px;
                        color: #000;
                        margin: 0;
                        padding: 16px;
                        max-width: 380px;
                    }
                    .center { text-align: center; }
                    .bold { font-weight: bold; }
                    .divider { border-top: 1px dashed #444; margin: 8px 0; }
                    .double-divider { border-top: 2px solid #000; margin: 8px 0; }
                    table { width: 100%; border-collapse: collapse; }
                    .row { display: flex; justify-content: space-between; margin: 2px 0; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="center">
                    <h2 style="margin: 0 0 4px 0; font-size: 18px;">${shopProfile.shopName.uppercase()}</h2>
                    <p style="margin: 0; font-size: 11px;">${shopProfile.address}</p>
                    ${if (shopProfile.whatsappNumber.isNotEmpty()) "<p style='margin: 0; font-size: 11px;'>WA: ${shopProfile.whatsappNumber}</p>" else ""}
                </div>

                <div class="double-divider"></div>

                <div class="row"><span>No. TRX</span><span class="bold">${transaction.transactionNumber}</span></div>
                <div class="row"><span>Waktu</span><span>$dateStr</span></div>
                <div class="row"><span>Metode</span><span class="bold">${transaction.paymentMethod}</span></div>

                <div class="divider"></div>

                <table>
                    $itemsHtml
                </table>

                <div class="divider"></div>

                <div class="row" style="font-size: 14px;"><span class="bold">TOTAL</span><span class="bold">${LapakViewModel.formatRupiah(transaction.totalAmount)}</span></div>
                <div class="row"><span>Uang Bayar</span><span>${LapakViewModel.formatRupiah(transaction.cashReceived)}</span></div>
                <div class="row"><span>Kembalian</span><span class="bold">${LapakViewModel.formatRupiah(transaction.changeGiven)}</span></div>

                <div class="double-divider"></div>

                <div class="center" style="margin-top: 10px; font-size: 11px;">
                    <p style="margin: 0;">${shopProfile.footerMessage}</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Generates thermal-styled HTML for seller payout receipt (Bukti Pembayaran Hak Penyetor).
     */
    /**
     * Generates thermal-styled HTML for seller payout receipt (Bukti Pembayaran Hak Penyetor).
     */
    fun buildSellerPayoutReceiptHtml(
        payment: SellerPayment,
        seller: Seller,
        shopProfile: ShopProfile,
        remainingBalance: Double,
        soldItems: List<com.example.data.model.SoldItemSummary> = emptyList()
    ): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID"))
        val dateStr = sdf.format(Date(payment.paymentDate))

        val soldItemsHtml = StringBuilder()
        if (soldItems.isNotEmpty()) {
            for (item in soldItems) {
                soldItemsHtml.append("""
                    <tr>
                        <td style="padding: 3px 0; font-size: 11px;">
                            <b>${item.productName}</b><br>
                            <span style="font-size: 10px; color: #555;">${item.quantity} ${item.unit} x ${LapakViewModel.formatRupiah(item.sellerSharePerUnit)}</span>
                        </td>
                        <td style="padding: 3px 0; text-align: right; vertical-align: top; font-size: 11px; font-weight: bold;">
                            ${LapakViewModel.formatRupiah(item.totalSellerShare)}
                        </td>
                    </tr>
                """.trimIndent())
            }
        } else {
            soldItemsHtml.append("""
                <tr>
                    <td colspan="2" style="padding: 4px 0; font-size: 11px; color: #777; font-style: italic;">
                        (Pembayaran berkala / akumulasi hak penyetor)
                    </td>
                </tr>
            """.trimIndent())
        }

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Courier New', Courier, monospace;
                        font-size: 12px;
                        color: #000;
                        margin: 0;
                        padding: 16px;
                        max-width: 380px;
                    }
                    .center { text-align: center; }
                    .bold { font-weight: bold; }
                    .divider { border-top: 1px dashed #444; margin: 8px 0; }
                    .double-divider { border-top: 2px solid #000; margin: 8px 0; }
                    .box { border: 1px solid #444; padding: 10px; margin: 8px 0; }
                    .row { display: flex; justify-content: space-between; margin: 3px 0; font-size: 12px; }
                    table { width: 100%; border-collapse: collapse; }
                </style>
            </head>
            <body>
                <div class="center">
                    <h2 style="margin: 0 0 4px 0; font-size: 17px;">${shopProfile.shopName.uppercase()}</h2>
                    <p style="margin: 0; font-size: 11px;">${shopProfile.address}</p>
                    ${if (shopProfile.whatsappNumber.isNotEmpty()) "<p style='margin: 0; font-size: 11px;'>WA: ${shopProfile.whatsappNumber}</p>" else ""}
                </div>

                <div class="double-divider"></div>

                <div class="center" style="margin: 6px 0;">
                    <span class="bold" style="font-size: 13px; text-decoration: underline;">BUKTI PEMBAYARAN HAK PENYETOR</span><br>
                    <span style="font-size: 10px; color: #555;">No: PAY-${payment.id}-${payment.paymentDate % 10000}</span>
                </div>

                <div class="divider"></div>

                <div class="row"><span>Waktu Pembayaran</span><span>$dateStr</span></div>
                <div class="row"><span>Nama Penyetor</span><span class="bold">${seller.name}</span></div>
                ${if (seller.whatsappNumber.isNotEmpty()) "<div class='row'><span>No. WhatsApp</span><span>${seller.whatsappNumber}</span></div>" else ""}
                <div class="row"><span>Metode Payout</span><span class="bold">${payment.paymentMethod}</span></div>
                ${if (payment.notes.isNotEmpty()) "<div class='row'><span>Catatan</span><span>${payment.notes}</span></div>" else ""}

                <div class="divider"></div>

                <div style="font-weight: bold; margin-bottom: 4px; font-size: 11px;">RINCIAN MENU TERJUAL:</div>
                <table>
                    $soldItemsHtml
                </table>

                <div class="divider"></div>

                <div class="row" style="font-size: 14px; margin: 8px 0;">
                    <span class="bold">JUMLAH DIBAYARKAN</span>
                    <span class="bold">${LapakViewModel.formatRupiah(payment.amount)}</span>
                </div>

                <div class="row" style="font-size: 12px;">
                    <span>Sisa Hak Belum Dibayar:</span>
                    <span class="bold">${LapakViewModel.formatRupiah(remainingBalance)}</span>
                </div>

                <div class="double-divider"></div>

                <div style="margin-top: 18px; display: flex; justify-content: space-between; text-align: center; font-size: 10px;">
                    <div>
                        <p style="margin: 0 0 35px 0;">Penerima (Penyetor),</p>
                        <p style="margin: 0; font-weight: bold;">( ${seller.name} )</p>
                    </div>
                    <div>
                        <p style="margin: 0 0 35px 0;">Admin Lapak,</p>
                        <p style="margin: 0; font-weight: bold;">( ${shopProfile.ownerName} )</p>
                    </div>
                </div>

                <div class="center" style="margin-top: 14px; font-size: 10px; color: #666;">
                    <p style="margin: 0;">Terima kasih atas kerja sama konsinyasi Anda.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Formats plain text for WhatsApp sharing to the seller.
     */
    fun formatSellerPayoutReceiptText(
        payment: SellerPayment,
        seller: Seller,
        shopProfile: ShopProfile,
        remainingBalance: Double,
        soldItems: List<com.example.data.model.SoldItemSummary> = emptyList()
    ): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("id", "ID"))
        val dateStr = sdf.format(Date(payment.paymentDate))

        val sb = StringBuilder()
        sb.append("================================\n")
        sb.append("BUKTI PEMBAYARAN HAK PENYETOR\n")
        sb.append("${shopProfile.shopName.uppercase()}\n")
        sb.append("================================\n")
        sb.append("No. Bukti : PAY-${payment.id}-${payment.paymentDate % 10000}\n")
        sb.append("Tanggal   : $dateStr\n")
        sb.append("Penyetor  : ${seller.name}\n")
        if (seller.whatsappNumber.isNotEmpty()) {
            sb.append("No. WA    : ${seller.whatsappNumber}\n")
        }
        sb.append("Metode    : ${payment.paymentMethod}\n")
        if (payment.notes.isNotEmpty()) {
            sb.append("Catatan   : ${payment.notes}\n")
        }

        if (soldItems.isNotEmpty()) {
            sb.append("--------------------------------\n")
            sb.append("RINCIAN MENU TERJUAL:\n")
            for (item in soldItems) {
                sb.append("• ${item.productName}: ${item.quantity} ${item.unit} x ${LapakViewModel.formatRupiah(item.sellerSharePerUnit)} = ${LapakViewModel.formatRupiah(item.totalSellerShare)}\n")
            }
        }

        sb.append("--------------------------------\n")
        sb.append("JUMLAH DIBAYARKAN : ${LapakViewModel.formatRupiah(payment.amount)}\n")
        sb.append("--------------------------------\n")
        sb.append("Sisa Hak Penyetor : ${LapakViewModel.formatRupiah(remainingBalance)}\n")
        if (remainingBalance <= 0) {
            sb.append("Status            : LUNAS\n")
        }
        sb.append("================================\n")
        sb.append("Terima kasih atas kerja samanya!\n")
        sb.append("Admin: ${shopProfile.ownerName} (${shopProfile.shopName})\n")
        if (shopProfile.whatsappNumber.isNotEmpty()) {
            sb.append("WA Admin: ${shopProfile.whatsappNumber}\n")
        }
        sb.append("================================\n")
        return sb.toString()
    }
}
