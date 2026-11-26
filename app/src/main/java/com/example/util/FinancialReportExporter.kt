package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.FinancialReportSnapshot
import com.example.data.model.ShopProfile
import com.example.ui.viewmodel.LapakViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object FinancialReportExporter {

    private val wibTimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val idLocale = Locale("id", "ID")

    private fun formatDateTimeWib(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", idLocale).apply { timeZone = wibTimeZone }
        return sdf.format(Date(millis))
    }

    private fun formatFullDateWib(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", idLocale).apply { timeZone = wibTimeZone }
        return "${sdf.format(Date(millis))} WIB"
    }

    private fun formatStampForFile(millis: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).apply { timeZone = wibTimeZone }
        return sdf.format(Date(millis))
    }

    private fun xmlEscape(text: String): String {
        val sb = StringBuilder(text.length + 16)
        for (ch in text) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun colIndexToLetters(colIndex1Based: Int): String {
        var n = colIndex1Based
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    // =========================================================================
    // 1. MULTI-SHEET EXCEL (.xlsx) OOXML GENERATOR (AUTO-FIT COLUMN WIDTHS)
    // =========================================================================

    fun generateExcelBytes(
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun putEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            putEntry("[Content_Types].xml", buildContentTypesXml())
            putEntry("_rels/.rels", buildRootRelsXml())
            putEntry("xl/workbook.xml", buildWorkbookXml())
            putEntry("xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())
            putEntry("xl/styles.xml", buildStylesXml())
            putEntry("xl/worksheets/sheet1.xml", buildLedgerSheetXml(shopProfile, report))
            putEntry("xl/worksheets/sheet2.xml", buildSellerRecapSheetXml(shopProfile, report))
        }
        return baos.toByteArray()
    }

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
          <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildWorkbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets>
            <sheet name="Buku Kas &amp; Transaksi" sheetId="1" r:id="rId1"/>
            <sheet name="Rekap Setoran Penyetor" sheetId="2" r:id="rId2"/>
          </sheets>
        </workbook>
    """.trimIndent()

    private fun buildWorkbookRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
          <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
          <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
        </Relationships>
    """.trimIndent()

    /**
     * Style indices (s="..."):
     * 0 = Default body left
     * 1 = Big Title bold (Emerald #1F5C46)
     * 2 = Subtitle italic / muted
     * 3 = Section Header bold (#1F5C46)
     * 4 = Table Header (Soft Emerald #2D7A5E fill, white bold, center, border)
     * 5 = Body Text left with thin border
     * 6 = Body Center with thin border (No, Qty, Status)
     * 7 = Body Currency right with thin border ("Rp "#,##0)
     * 8 = Total Label bold left (Soft Mint #E3F4EC fill, border)
     * 9 = Total Center bold (Soft Mint fill, border)
     * 10 = Total Currency bold right (Soft Mint fill, border, "Rp "#,##0)
     * 11 = Summary Label bold left (Warm Cream #FAF7F0 fill, border)
     * 12 = Summary Currency bold right (Warm Cream fill, border, "Rp "#,##0)
     */
    private fun buildStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <numFmts count="1">
            <numFmt numFmtId="164" formatCode="&quot;Rp &quot;#,##0;(&quot;Rp &quot;#,##0);&quot;-&quot;"/>
          </numFmts>
          <fonts count="5">
            <font><sz val="10"/><color rgb="FF1A2421"/><name val="Calibri"/></font>
            <font><b/><sz val="15"/><color rgb="FF1F5C46"/><name val="Calibri"/></font>
            <font><sz val="10"/><color rgb="FF5C6B66"/><name val="Calibri"/></font>
            <font><b/><sz val="11"/><color rgb="FF1F5C46"/><name val="Calibri"/></font>
            <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
          </fonts>
          <fills count="5">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF2D7A5E"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFE3F4EC"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFF6F8F7"/><bgColor indexed="64"/></patternFill></fill>
          </fills>
          <borders count="2">
            <border><left/><right/><top/><bottom/><diagonal/></border>
            <border>
              <left style="thin"><color rgb="FFCBD5E1"/></left>
              <right style="thin"><color rgb="FFCBD5E1"/></right>
              <top style="thin"><color rgb="FFCBD5E1"/></top>
              <bottom style="thin"><color rgb="FFCBD5E1"/></bottom>
              <diagonal/>
            </border>
          </borders>
          <cellStyleXfs count="1">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
          </cellStyleXfs>
          <cellXfs count="13">
            <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0" fontId="3" fillId="0" borderId="0" xfId="0" applyFont="1"/>
            <xf numFmtId="0" fontId="4" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
            <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
            <xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="0" fontId="3" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
            <xf numFmtId="164" fontId="3" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
            <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
            <xf numFmtId="164" fontId="3" fillId="4" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>
          </cellXfs>
        </styleSheet>
    """.trimIndent()

    private class SheetBuilder(private val columnCount: Int) {
        private val maxChars = IntArray(columnCount) { 8 }
        private val rowsXml = StringBuilder()
        private var currentRow = 0

        fun trackWidth(colIndex0Based: Int, displayLen: Int) {
            if (colIndex0Based in 0 until columnCount) {
                if (displayLen > maxChars[colIndex0Based]) {
                    maxChars[colIndex0Based] = displayLen
                }
            }
        }

        fun addEmptyRow() {
            currentRow++
        }

        fun addSingleTextRow(text: String, styleIdx: Int, heightPt: Int? = null) {
            currentRow++
            val htAttr = if (heightPt != null) """ ht="$heightPt" customHeight="1"""" else ""
            rowsXml.append("""<row r="$currentRow"$htAttr>""")
            val ref = "${colIndexToLetters(1)}$currentRow"
            rowsXml.append("""<c r="$ref" t="inlineStr" s="$styleIdx"><is><t>${xmlEscape(text)}</t></is></c>""")
            rowsXml.append("</row>\n")
        }

        fun addSummaryPairRow(label: String, amount: Double, labelStyle: Int = 11, valStyle: Int = 12) {
            currentRow++
            trackWidth(1, label.length)
            trackWidth(2, LapakViewModel.formatRupiah(amount).length + 3)
            rowsXml.append("""<row r="$currentRow" ht="20" customHeight="1">""")
            val refLabel = "${colIndexToLetters(2)}$currentRow"
            val refVal = "${colIndexToLetters(3)}$currentRow"
            rowsXml.append("""<c r="$refLabel" t="inlineStr" s="$labelStyle"><is><t>${xmlEscape(label)}</t></is></c>""")
            rowsXml.append("""<c r="$refVal" s="$valStyle"><v>${amount.toLong()}</v></c>""")
            rowsXml.append("</row>\n")
        }

        fun addRow(cells: List<CellValue>, heightPt: Int? = null) {
            currentRow++
            val htAttr = if (heightPt != null) """ ht="$heightPt" customHeight="1"""" else ""
            rowsXml.append("""<row r="$currentRow"$htAttr>""")
            cells.forEachIndexed { idx, cell ->
                val ref = "${colIndexToLetters(idx + 1)}$currentRow"
                when (cell) {
                    is CellValue.Text -> {
                        trackWidth(idx, cell.value.length)
                        rowsXml.append("""<c r="$ref" t="inlineStr" s="${cell.style}"><is><t>${xmlEscape(cell.value)}</t></is></c>""")
                    }
                    is CellValue.IntNum -> {
                        trackWidth(idx, cell.value.toString().length + 2)
                        rowsXml.append("""<c r="$ref" s="${cell.style}"><v>${cell.value}</v></c>""")
                    }
                    is CellValue.Currency -> {
                        trackWidth(idx, LapakViewModel.formatRupiah(cell.value).length + 3)
                        rowsXml.append("""<c r="$ref" s="${cell.style}"><v>${cell.value.toLong()}</v></c>""")
                    }
                }
            }
            rowsXml.append("</row>\n")
        }

        fun buildXml(): String {
            val colsXml = StringBuilder("<cols>\n")
            for (i in 0 until columnCount) {
                val computedWidth = (maxChars[i] + 4).coerceIn(6, 48).toDouble()
                colsXml.append("""  <col min="${i + 1}" max="${i + 1}" width="$computedWidth" customWidth="1"/>""").append('\n')
            }
            colsXml.append("</cols>\n")

            return buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
                append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""").append('\n')
                append(colsXml)
                append("<sheetData>\n")
                append(rowsXml)
                append("</sheetData>\n")
                append("</worksheet>")
            }
        }
    }

    private sealed class CellValue(val style: Int) {
        class Text(val value: String, style: Int) : CellValue(style)
        class IntNum(val value: Int, style: Int) : CellValue(style)
        class Currency(val value: Double, style: Int) : CellValue(style)
    }

    private fun buildLedgerSheetXml(
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot
    ): String {
        val sheet = SheetBuilder(9)

        // Header Info
        sheet.addSingleTextRow("${shopProfile.shopName.uppercase()} — LAPORAN KEUANGAN & BAGI HASIL KONSINYASI", styleIdx = 1, heightPt = 24)
        sheet.addSingleTextRow("Periode Laporan: ${report.periodLabel}", styleIdx = 2)
        sheet.addSingleTextRow("Tanggal Cetak: ${formatFullDateWib(report.generatedAtMillis)}", styleIdx = 2)
        sheet.addEmptyRow()

        // Executive Summary Table
        sheet.addSingleTextRow("RINGKASAN EKSEKUTIF KEUANGAN", styleIdx = 3, heightPt = 20)
        sheet.addSummaryPairRow("Total Omzet Kotor (Uang Masuk Pembeli)", report.totalGrossOmzet)
        sheet.addSummaryPairRow("Total Hak Penyetor (Setoran ke Penjual)", report.totalSellerShare)
        sheet.addSummaryPairRow("Total Komisi Admin Lapak", report.totalAdminCommission)
        sheet.addSummaryPairRow("Total Biaya Operasional Lapak", report.totalOperatingExpenses)
        sheet.addSummaryPairRow("Untung Bersih Lapak (Komisi - Biaya Operasional)", report.netLapakProfit, labelStyle = 8, valStyle = 10)
        sheet.addEmptyRow()

        // Sheet 1 Table — Chronological Cash Ledger (Oldest -> Newest)
        sheet.addSingleTextRow("RINCIAN BUKU KAS & TRANSAKSI (URUTAN KRONOLOGIS: TERDAHULU -> TERBARU)", styleIdx = 3, heightPt = 20)
        sheet.addRow(
            listOf(
                CellValue.Text("No", 4),
                CellValue.Text("Tanggal & Jam", 4),
                CellValue.Text("Keterangan / Item Terjual", 4),
                CellValue.Text("Nama Dapur Penyetor", 4),
                CellValue.Text("Qty", 4),
                CellValue.Text("Omzet Masuk (Rp)", 4),
                CellValue.Text("Hak Penyetor (Rp)", 4),
                CellValue.Text("Komisi Lapak (Rp)", 4),
                CellValue.Text("Biaya Operasional (Rp)", 4)
            ),
            heightPt = 26
        )

        if (report.ledgerRows.isEmpty()) {
            sheet.addRow(
                listOf(
                    CellValue.Text("-", 6),
                    CellValue.Text("-", 6),
                    CellValue.Text("Belum ada transaksi atau biaya operasional pada periode ini", 5),
                    CellValue.Text("-", 5),
                    CellValue.IntNum(0, 6),
                    CellValue.Currency(0.0, 7),
                    CellValue.Currency(0.0, 7),
                    CellValue.Currency(0.0, 7),
                    CellValue.Currency(0.0, 7)
                )
            )
        } else {
            for (row in report.ledgerRows) {
                sheet.addRow(
                    listOf(
                        CellValue.IntNum(row.number, 6),
                        CellValue.Text(formatDateTimeWib(row.timestampMillis), 6),
                        CellValue.Text(row.description, 5),
                        CellValue.Text(row.sellerName, 5),
                        CellValue.IntNum(row.qty, 6),
                        CellValue.Currency(row.omzetIn, 7),
                        CellValue.Currency(row.sellerShare, 7),
                        CellValue.Currency(row.adminCommission, 7),
                        CellValue.Currency(row.operatingExpense, 7)
                    ),
                    heightPt = 19
                )
            }
        }

        // TOTAL Row
        sheet.addRow(
            listOf(
                CellValue.Text("", 9),
                CellValue.Text("", 9),
                CellValue.Text("TOTAL PERIODE (${report.ledgerRows.size} baris)", 8),
                CellValue.Text("", 8),
                CellValue.IntNum(report.totalItemsSold, 9),
                CellValue.Currency(report.totalGrossOmzet, 10),
                CellValue.Currency(report.totalSellerShare, 10),
                CellValue.Currency(report.totalAdminCommission, 10),
                CellValue.Currency(report.totalOperatingExpenses, 10)
            ),
            heightPt = 22
        )

        return sheet.buildXml()
    }

    private fun buildSellerRecapSheetXml(
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot
    ): String {
        val sheet = SheetBuilder(7)

        sheet.addSingleTextRow("${shopProfile.shopName.uppercase()} — REKAP SETORAN PER DAPUR PENYETOR", styleIdx = 1, heightPt = 24)
        sheet.addSingleTextRow("Periode Laporan: ${report.periodLabel}", styleIdx = 2)
        sheet.addSingleTextRow("Tanggal Cetak: ${formatFullDateWib(report.generatedAtMillis)}", styleIdx = 2)
        sheet.addEmptyRow()

        sheet.addRow(
            listOf(
                CellValue.Text("No", 4),
                CellValue.Text("Nama Dapur Penyetor", 4),
                CellValue.Text("Total Item Terjual", 4),
                CellValue.Text("Total Hak Penyetor (Rp)", 4),
                CellValue.Text("Sudah Dibayar (Rp)", 4),
                CellValue.Text("Sisa Belum Dibayar (Rp)", 4),
                CellValue.Text("Status", 4)
            ),
            heightPt = 26
        )

        if (report.sellerRecapRows.isEmpty()) {
            sheet.addRow(
                listOf(
                    CellValue.Text("-", 6),
                    CellValue.Text("Belum ada aktivitas penjualan/setoran penyetor pada periode ini", 5),
                    CellValue.IntNum(0, 6),
                    CellValue.Currency(0.0, 7),
                    CellValue.Currency(0.0, 7),
                    CellValue.Currency(0.0, 7),
                    CellValue.Text("-", 6)
                )
            )
        } else {
            for (row in report.sellerRecapRows) {
                sheet.addRow(
                    listOf(
                        CellValue.IntNum(row.number, 6),
                        CellValue.Text(row.sellerName, 5),
                        CellValue.IntNum(row.totalItemsSold, 6),
                        CellValue.Currency(row.totalSellerShare, 7),
                        CellValue.Currency(row.totalPaid, 7),
                        CellValue.Currency(row.remainingUnpaid, 7),
                        CellValue.Text(row.status, 6)
                    ),
                    heightPt = 19
                )
            }
        }

        val sumQty = report.sellerRecapRows.sumOf { it.totalItemsSold }
        val sumRights = report.sellerRecapRows.sumOf { it.totalSellerShare }
        val sumPaid = report.sellerRecapRows.sumOf { it.totalPaid }
        val sumUnpaid = report.sellerRecapRows.sumOf { it.remainingUnpaid }

        sheet.addRow(
            listOf(
                CellValue.Text("", 9),
                CellValue.Text("TOTAL (${report.sellerRecapRows.size} Dapur)", 8),
                CellValue.IntNum(sumQty, 9),
                CellValue.Currency(sumRights, 10),
                CellValue.Currency(sumPaid, 10),
                CellValue.Currency(sumUnpaid, 10),
                CellValue.Text(if (sumUnpaid <= 0.0) "Lunas" else "Ada Sisa", 9)
            ),
            heightPt = 22
        )

        return sheet.buildXml()
    }

    // =========================================================================
    // 2. FORMAL A4 PDF GENERATOR (MULTI-PAGE VECTOR LAYOUT)
    // =========================================================================

    fun generatePdfBytes(
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot
    ): ByteArray {
        val pageWidth = 595 // A4 width in PostScript points
        val pageHeight = 842 // A4 height in PostScript points
        val marginX = 28f
        val marginTop = 30f
        val marginBottom = 38f
        val contentWidth = pageWidth - marginX * 2f

        val pdf = PdfDocument()
        var pageNum = 1
        var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = currentPage.canvas
        var y = marginTop

        val emeraldColor = 0xFF2D7A5E.toInt()
        val emeraldDarkColor = 0xFF1F5C46.toInt()
        val mintLightBg = 0xFFE6F4EE.toInt()
        val zebraBg = 0xFFF7FAF8.toInt()
        val borderColor = 0xFFCBD5E1.toInt()
        val textMain = 0xFF1A2421.toInt()
        val textMuted = 0xFF5C6B66.toInt()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emeraldColor }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 7.2f
            textAlign = Paint.Align.LEFT
        }
        val cellCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 7.2f
            textAlign = Paint.Align.CENTER
        }
        val cellRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 7.2f
            textAlign = Paint.Align.RIGHT
        }
        val totalLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val totalCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val totalRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = borderColor
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val zebraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = zebraBg }
        val totalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mintLightBg }
        val pageFooterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 7.5f
        }

        fun drawPageFooter(c: Canvas, pNum: Int) {
            val fy = pageHeight - 16f
            pageFooterPaint.textAlign = Paint.Align.LEFT
            c.drawText("${shopProfile.shopName} • Dicetak ${formatFullDateWib(report.generatedAtMillis)}", marginX, fy, pageFooterPaint)
            pageFooterPaint.textAlign = Paint.Align.RIGHT
            c.drawText("Halaman $pNum", pageWidth - marginX, fy, pageFooterPaint)
        }

        fun startNewPage() {
            drawPageFooter(canvas, pageNum)
            pdf.finishPage(currentPage)
            pageNum++
            currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = currentPage.canvas
            y = marginTop
        }

        fun ensureSpace(neededHeight: Float, onNewPageHeader: (() -> Unit)? = null) {
            if (y + neededHeight > pageHeight - marginBottom) {
                startNewPage()
                onNewPageHeader?.invoke()
            }
        }

        fun clipText(text: String, paint: Paint, maxW: Float): String {
            if (paint.measureText(text) <= maxW) return text
            var end = text.length
            while (end > 2 && paint.measureText(text.substring(0, end) + "..") > maxW) {
                end--
            }
            return text.substring(0, end.coerceAtLeast(1)) + ".."
        }

        // === 1. KOP LAPORAN ===
        val centerX = pageWidth / 2f
        canvas.drawText(shopProfile.shopName.uppercase(), centerX, y + 12f, titlePaint)
        y += 18f
        val contactLine = listOf(shopProfile.address, if (shopProfile.whatsappNumber.isNotBlank()) "WA: ${shopProfile.whatsappNumber}" else "")
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(clipText(contactLine, metaPaint, contentWidth), centerX, y + 8f, metaPaint)
            y += 13f
        }
        val kopRule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldColor
            strokeWidth = 1.8f
        }
        canvas.drawLine(marginX, y + 3f, pageWidth - marginX, y + 3f, kopRule)
        y += 15f

        canvas.drawText("LAPORAN KEUANGAN & BAGI HASIL KONSINYASI", centerX, y + 8f, subtitlePaint)
        y += 13f
        canvas.drawText("Periode: ${report.periodLabel}", centerX, y + 7f, metaPaint)
        y += 16f

        // === 2. KOTAK RINGKASAN KEUANGAN (4 CARDS) ===
        val cardGap = 7f
        val cardW = (contentWidth - cardGap * 3f) / 4f
        val cardH = 44f
        val summaryCards = listOf(
            Triple("Total Omzet Kotor", LapakViewModel.formatRupiah(report.totalGrossOmzet), 0xFFE6F4EE.toInt()),
            Triple("Total Hak Penyetor", LapakViewModel.formatRupiah(report.totalSellerShare), 0xFFFEF3C7.toInt()),
            Triple("Biaya Operasional", LapakViewModel.formatRupiah(report.totalOperatingExpenses), 0xFFF1F5F9.toInt()),
            Triple("Untung Bersih Lapak", LapakViewModel.formatRupiah(report.netLapakProfit), 0xFFE0F2FE.toInt())
        )
        val sumTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 7.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sumValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        summaryCards.forEachIndexed { idx, (label, value, bgColor) ->
            val left = marginX + idx * (cardW + cardGap)
            val rect = RectF(left, y, left + cardW, y + cardH)
            canvas.drawRoundRect(rect, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor })
            canvas.drawRoundRect(rect, 6f, 6f, borderPaint)
            canvas.drawText(label, left + 7f, y + 15f, sumTitlePaint)
            canvas.drawText(clipText(value, sumValPaint, cardW - 14f), left + 7f, y + 32f, sumValPaint)
        }
        y += cardH + 16f

        // === 3. TABEL RINCIAN BUKU KAS KRONOLOGIS (TERDAHULU -> TERBARU) ===
        canvas.drawText("A. Rincian Buku Kas & Transaksi (Urutan Kronologis: Terdahulu -> Terbaru)", marginX, y + 8f, sectionPaint)
        y += 14f

        // 9 columns totaling contentWidth (539 pt)
        val ledgerWidths = floatArrayOf(22f, 62f, 118f, 75f, 24f, 62f, 60f, 58f, 58f)
        val ledgerHeaders = listOf("No", "Tgl & Jam", "Keterangan / Item", "Dapur Penyetor", "Qty", "Omzet (Rp)", "Hak Penyetor", "Komisi", "Biaya Ops")

        fun drawLedgerTableHeader() {
            val rowH = 20f
            var x = marginX
            canvas.drawRect(marginX, y, marginX + contentWidth, y + rowH, headerBgPaint)
            ledgerHeaders.forEachIndexed { i, title ->
                val w = ledgerWidths[i]
                headerTextPaint.textAlign = if (i >= 5) Paint.Align.RIGHT else if (i == 0 || i == 4) Paint.Align.CENTER else Paint.Align.LEFT
                val tx = when (headerTextPaint.textAlign) {
                    Paint.Align.RIGHT -> x + w - 3f
                    Paint.Align.CENTER -> x + w / 2f
                    else -> x + 3f
                }
                canvas.drawText(clipText(title, headerTextPaint, w - 5f), tx, y + 12.5f, headerTextPaint)
                canvas.drawRect(x, y, x + w, y + rowH, borderPaint)
                x += w
            }
            y += rowH
        }

        drawLedgerTableHeader()

        val rowH = 16.5f
        if (report.ledgerRows.isEmpty()) {
            canvas.drawRect(marginX, y, marginX + contentWidth, y + 20f, borderPaint)
            cellCenterPaint.color = textMuted
            canvas.drawText("Belum ada transaksi atau biaya operasional pada periode ini.", centerX, y + 12.5f, cellCenterPaint)
            cellCenterPaint.color = textMain
            y += 20f
        } else {
            report.ledgerRows.forEachIndexed { idx, row ->
                ensureSpace(rowH) { drawLedgerTableHeader() }
                if (idx % 2 == 1) {
                    canvas.drawRect(marginX, y, marginX + contentWidth, y + rowH, zebraPaint)
                }
                var x = marginX
                val vals = listOf(
                    row.number.toString(),
                    formatDateTimeWib(row.timestampMillis),
                    row.description,
                    row.sellerName,
                    if (row.qty > 0) row.qty.toString() else "-",
                    if (row.omzetIn > 0) formatShortRupiahCell(row.omzetIn) else "-",
                    if (row.sellerShare > 0) formatShortRupiahCell(row.sellerShare) else "-",
                    if (row.adminCommission > 0) formatShortRupiahCell(row.adminCommission) else "-",
                    if (row.operatingExpense > 0) formatShortRupiahCell(row.operatingExpense) else "-"
                )
                vals.forEachIndexed { i, text ->
                    val w = ledgerWidths[i]
                    val paint = when {
                        i >= 5 -> cellRightPaint
                        i == 0 || i == 4 -> cellCenterPaint
                        else -> cellLeftPaint
                    }
                    val tx = when (paint.textAlign) {
                        Paint.Align.RIGHT -> x + w - 3f
                        Paint.Align.CENTER -> x + w / 2f
                        else -> x + 3f
                    }
                    canvas.drawText(clipText(text, paint, w - 5f), tx, y + 11f, paint)
                    canvas.drawRect(x, y, x + w, y + rowH, borderPaint)
                    x += w
                }
                y += rowH
            }
        }

        // TOTAL Row for Ledger
        ensureSpace(20f) { drawLedgerTableHeader() }
        val totalH = 19f
        canvas.drawRect(marginX, y, marginX + contentWidth, y + totalH, totalBgPaint)
        var txCursor = marginX
        val mergedLabelW = ledgerWidths[0] + ledgerWidths[1] + ledgerWidths[2] + ledgerWidths[3]
        canvas.drawText("TOTAL KESELURUHAN", txCursor + 5f, y + 12.5f, totalLeftPaint)
        canvas.drawRect(txCursor, y, txCursor + mergedLabelW, y + totalH, borderPaint)
        txCursor += mergedLabelW

        val ledgerTotals = listOf(
            report.totalItemsSold.toString(),
            formatShortRupiahCell(report.totalGrossOmzet),
            formatShortRupiahCell(report.totalSellerShare),
            formatShortRupiahCell(report.totalAdminCommission),
            formatShortRupiahCell(report.totalOperatingExpenses)
        )
        ledgerTotals.forEachIndexed { offset, text ->
            val i = 4 + offset
            val w = ledgerWidths[i]
            val p = if (i == 4) totalCenterPaint else totalRightPaint
            val tx = if (i == 4) txCursor + w / 2f else txCursor + w - 3f
            canvas.drawText(clipText(text, p, w - 5f), tx, y + 12.5f, p)
            canvas.drawRect(txCursor, y, txCursor + w, y + totalH, borderPaint)
            txCursor += w
        }
        y += totalH + 18f

        // === 4. TABEL REKAP SETORAN PER DAPUR PENYETOR ===
        ensureSpace(64f)
        canvas.drawText("B. Rekap Setoran per Dapur Penyetor", marginX, y + 8f, sectionPaint)
        y += 14f

        // 7 columns totaling contentWidth (539 pt)
        val recapWidths = floatArrayOf(24f, 145f, 52f, 82f, 82f, 82f, 72f)
        val recapHeaders = listOf("No", "Nama Dapur Penyetor", "Item Terjual", "Hak Penyetor", "Sudah Dibayar", "Sisa Belum Bayar", "Status")

        fun drawRecapHeader() {
            val h = 20f
            var x = marginX
            canvas.drawRect(marginX, y, marginX + contentWidth, y + h, headerBgPaint)
            recapHeaders.forEachIndexed { i, title ->
                val w = recapWidths[i]
                headerTextPaint.textAlign = when {
                    i in 3..5 -> Paint.Align.RIGHT
                    i == 0 || i == 2 || i == 6 -> Paint.Align.CENTER
                    else -> Paint.Align.LEFT
                }
                val tx = when (headerTextPaint.textAlign) {
                    Paint.Align.RIGHT -> x + w - 4f
                    Paint.Align.CENTER -> x + w / 2f
                    else -> x + 4f
                }
                canvas.drawText(clipText(title, headerTextPaint, w - 6f), tx, y + 12.5f, headerTextPaint)
                canvas.drawRect(x, y, x + w, y + h, borderPaint)
                x += w
            }
            y += h
        }

        drawRecapHeader()

        if (report.sellerRecapRows.isEmpty()) {
            canvas.drawRect(marginX, y, marginX + contentWidth, y + 20f, borderPaint)
            cellCenterPaint.color = textMuted
            canvas.drawText("Belum ada data setoran penyetor pada periode ini.", centerX, y + 12.5f, cellCenterPaint)
            cellCenterPaint.color = textMain
            y += 20f
        } else {
            report.sellerRecapRows.forEachIndexed { idx, row ->
                ensureSpace(rowH) { drawRecapHeader() }
                if (idx % 2 == 1) {
                    canvas.drawRect(marginX, y, marginX + contentWidth, y + rowH, zebraPaint)
                }
                var x = marginX
                val vals = listOf(
                    row.number.toString(),
                    row.sellerName,
                    row.totalItemsSold.toString(),
                    formatShortRupiahCell(row.totalSellerShare),
                    formatShortRupiahCell(row.totalPaid),
                    formatShortRupiahCell(row.remainingUnpaid),
                    row.status
                )
                vals.forEachIndexed { i, text ->
                    val w = recapWidths[i]
                    val p = when {
                        i in 3..5 -> cellRightPaint
                        i == 0 || i == 2 || i == 6 -> cellCenterPaint
                        else -> cellLeftPaint
                    }
                    val tx = when (p.textAlign) {
                        Paint.Align.RIGHT -> x + w - 4f
                        Paint.Align.CENTER -> x + w / 2f
                        else -> x + 4f
                    }
                    canvas.drawText(clipText(text, p, w - 6f), tx, y + 11f, p)
                    canvas.drawRect(x, y, x + w, y + rowH, borderPaint)
                    x += w
                }
                y += rowH
            }
        }

        // Recap TOTAL Row
        ensureSpace(20f) { drawRecapHeader() }
        canvas.drawRect(marginX, y, marginX + contentWidth, y + totalH, totalBgPaint)
        var rx = marginX
        val recapMergeW = recapWidths[0] + recapWidths[1]
        canvas.drawText("TOTAL SETORAN (${report.sellerRecapRows.size} Dapur)", rx + 5f, y + 12.5f, totalLeftPaint)
        canvas.drawRect(rx, y, rx + recapMergeW, y + totalH, borderPaint)
        rx += recapMergeW

        val sumRecapQty = report.sellerRecapRows.sumOf { it.totalItemsSold }
        val sumRecapRights = report.sellerRecapRows.sumOf { it.totalSellerShare }
        val sumRecapPaid = report.sellerRecapRows.sumOf { it.totalPaid }
        val sumRecapUnpaid = report.sellerRecapRows.sumOf { it.remainingUnpaid }
        val recapTotals = listOf(
            sumRecapQty.toString(),
            formatShortRupiahCell(sumRecapRights),
            formatShortRupiahCell(sumRecapPaid),
            formatShortRupiahCell(sumRecapUnpaid),
            if (sumRecapUnpaid <= 0.0) "Lunas" else "Ada Sisa"
        )
        recapTotals.forEachIndexed { offset, text ->
            val i = 2 + offset
            val w = recapWidths[i]
            val p = if (i in 3..5) totalRightPaint else totalCenterPaint
            val tx = if (i in 3..5) rx + w - 4f else rx + w / 2f
            canvas.drawText(clipText(text, p, w - 6f), tx, y + 12.5f, p)
            canvas.drawRect(rx, y, rx + w, y + totalH, borderPaint)
            rx += w
        }
        y += totalH + 26f

        // === 5. KOLOM TANDA TANGAN / PENGESAHAN ADMIN LAPAK ===
        ensureSpace(95f)
        val sigBoxLeft = pageWidth - marginX - 185f
        val sigCenterX = sigBoxLeft + 92.5f
        val sigDateFmt = SimpleDateFormat("dd MMMM yyyy", idLocale).apply { timeZone = wibTimeZone }
        val sigTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }
        val sigBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Disahkan pada ${sigDateFmt.format(Date(report.generatedAtMillis))}", sigCenterX, y + 10f, sigTextPaint)
        canvas.drawText("Admin / Pengelola ${shopProfile.shopName}", sigCenterX, y + 22f, sigTextPaint)
        // Signature space
        canvas.drawLine(sigBoxLeft + 18f, y + 66f, sigBoxLeft + 167f, y + 66f, borderPaint)
        canvas.drawText(shopProfile.ownerName.ifBlank { "Admin Lapak" }, sigCenterX, y + 78f, sigBoldPaint)

        drawPageFooter(canvas, pageNum)
        pdf.finishPage(currentPage)

        val baos = ByteArrayOutputStream()
        pdf.writeTo(baos)
        pdf.close()
        return baos.toByteArray()
    }

    private fun formatShortRupiahCell(value: Double): String {
        val formatted = String.format(Locale("id", "ID"), "%,d", value.toLong()).replace(',', '.')
        return "Rp $formatted"
    }

    // =========================================================================
    // 3. SAVE TO DOWNLOADS & DIRECT SHARE (WHATSAPP / SYSTEM CHOOSER)
    // =========================================================================

    fun saveExcelToDownloads(
        context: Context,
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot
    ): Result<String> {
        return try {
            val bytes = generateExcelBytes(shopProfile, report)
            val fileName = "Laporan_Keuangan_LapakKita_${formatStampForFile(report.generatedAtMillis)}.xlsx"
            saveBytesToDownloads(
                context = context,
                fileName = fileName,
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes = bytes
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun savePdfToDownloads(
        context: Context,
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot
    ): Result<String> {
        return try {
            val bytes = generatePdfBytes(shopProfile, report)
            val fileName = "Laporan_Keuangan_LapakKita_${formatStampForFile(report.generatedAtMillis)}.pdf"
            saveBytesToDownloads(
                context = context,
                fileName = fileName,
                mimeType = "application/pdf",
                bytes = bytes
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveBytesToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Lapak Kita")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return Result.failure(IllegalStateException("Gagal membuat file di folder Downloads"))
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: return Result.failure(IllegalStateException("Gagal menulis file laporan"))
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result.success("Downloads/Lapak Kita/$fileName")
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Lapak Kita").apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            Result.success(outFile.absolutePath)
        }
    }

    fun shareReportFile(
        context: Context,
        shopProfile: ShopProfile,
        report: FinancialReportSnapshot,
        isExcel: Boolean,
        preferWhatsApp: Boolean = true
    ): Result<Unit> {
        return try {
            val bytes = if (isExcel) generateExcelBytes(shopProfile, report) else generatePdfBytes(shopProfile, report)
            val ext = if (isExcel) "xlsx" else "pdf"
            val mime = if (isExcel) {
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            } else {
                "application/pdf"
            }
            val fileName = "Laporan_Keuangan_LapakKita_${formatStampForFile(report.generatedAtMillis)}.$ext"
            val cacheFolder = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(cacheFolder, fileName)
            FileOutputStream(file).use { it.write(bytes) }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val caption = "Laporan Keuangan ${shopProfile.shopName} — Periode: ${report.periodLabel}"

            if (preferWhatsApp) {
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(waIntent)
                    return Result.success(Unit)
                } catch (_: Exception) {
                    // Fallback to system share sheet
                }
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Bagikan Laporan Keuangan").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
