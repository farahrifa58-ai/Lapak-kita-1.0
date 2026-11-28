package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.SellerPeriodReportSnapshot
import com.example.data.model.SellerPeriodSection
import com.example.data.model.ShopProfile
import com.example.ui.viewmodel.LapakViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SellerReportExporter {

    private val wibTimeZone = TimeZone.getTimeZone("Asia/Jakarta")
    private val idLocale = Locale("id", "ID")

    private fun formatFullDateWib(millis: Long): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", idLocale).apply { timeZone = wibTimeZone }
        return "${sdf.format(Date(millis))} WIB"
    }

    private fun formatStampForFile(millis: Long): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).apply { timeZone = wibTimeZone }
        return sdf.format(Date(millis))
    }

    private fun sanitizeSlug(raw: String): String {
        val clean = raw.replace(Regex("[^a-zA-Z0-9]+"), "_").trim('_')
        return clean.take(28).ifBlank { "Semua" }
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

    private fun formatShortRupiahCell(value: Double): String {
        val formatted = String.format(idLocale, "%,d", value.toLong()).replace(',', '.')
        return "Rp $formatted"
    }

    // =========================================================================
    // 1. EXCEL (.xlsx) OOXML GENERATOR WITH AUTOMATIC SUM FORMULAS & AUTO-FIT WIDTHS
    // =========================================================================

    fun generateExcelBytes(
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): ByteArray {
        val includeRecapSheet = !report.isSingleSeller
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            fun putEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            putEntry("[Content_Types].xml", buildContentTypesXml(includeRecapSheet))
            putEntry("_rels/.rels", buildRootRelsXml())
            putEntry("xl/workbook.xml", buildWorkbookXml(includeRecapSheet))
            putEntry("xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(includeRecapSheet))
            putEntry("xl/styles.xml", buildStylesXml())
            if (includeRecapSheet) {
                putEntry("xl/worksheets/sheet1.xml", buildSellerSummarySheetXml(shopProfile, report))
                putEntry("xl/worksheets/sheet2.xml", buildSellerProductsDetailSheetXml(shopProfile, report))
            } else {
                putEntry("xl/worksheets/sheet1.xml", buildSellerProductsDetailSheetXml(shopProfile, report))
            }
        }
        return baos.toByteArray()
    }

    private fun buildContentTypesXml(twoSheets: Boolean): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
        appendLine("""  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        appendLine("""  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        appendLine("""  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        if (twoSheets) {
            appendLine("""  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildWorkbookXml(twoSheets: Boolean): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        appendLine("""  <sheets>""")
        if (twoSheets) {
            appendLine("""    <sheet name="Rekap Penyetor" sheetId="1" r:id="rId1"/>""")
            appendLine("""    <sheet name="Rincian Produk Penyetor" sheetId="2" r:id="rId2"/>""")
        } else {
            appendLine("""    <sheet name="Rincian Produk Penyetor" sheetId="1" r:id="rId1"/>""")
        }
        appendLine("""  </sheets>""")
        append("""</workbook>""")
    }

    private fun buildWorkbookRelsXml(twoSheets: Boolean): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        appendLine("""  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""")
        if (twoSheets) {
            appendLine("""  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>""")
            appendLine("""  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        } else {
            appendLine("""  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildStylesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
          <numFmts count="1">
            <numFmt numFmtId="164" formatCode="&quot;Rp &quot;#,##0;(&quot;Rp &quot;#,##0);&quot;-&quot;"/>
          </numFmts>
          <fonts count="5">
            <font><sz val="10"/><color rgb="FF1A2421"/><name val="Calibri"/></font>
            <font><b/><sz val="14"/><color rgb="FF1F5C46"/><name val="Calibri"/></font>
            <font><sz val="10"/><color rgb="FF5C6B66"/><name val="Calibri"/></font>
            <font><b/><sz val="10"/><color rgb="FF1F5C46"/><name val="Calibri"/></font>
            <font><b/><sz val="10"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
          </fonts>
          <fills count="5">
            <fill><patternFill patternType="none"/></fill>
            <fill><patternFill patternType="gray125"/></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FF2D7A5E"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFE3F4EC"/><bgColor indexed="64"/></patternFill></fill>
            <fill><patternFill patternType="solid"><fgColor rgb="FFF3F7F5"/><bgColor indexed="64"/></patternFill></fill>
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
            <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
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

    private sealed class XlsCell(val style: Int) {
        class Text(val value: String, style: Int) : XlsCell(style)
        class IntNum(val value: Int, style: Int) : XlsCell(style)
        class Currency(val value: Double, style: Int) : XlsCell(style)
        class IntFormula(val formula: String, val cachedValue: Int, style: Int) : XlsCell(style)
        class CurrencyFormula(val formula: String, val cachedValue: Double, style: Int) : XlsCell(style)
    }

    private class FormulaSheetBuilder(private val columnCount: Int) {
        private val maxChars = IntArray(columnCount) { 8 }
        private val rowsXml = StringBuilder()
        var currentRow = 0
            private set

        fun trackWidth(colIdx0: Int, len: Int) {
            if (colIdx0 in 0 until columnCount && len > maxChars[colIdx0]) {
                maxChars[colIdx0] = len
            }
        }

        fun addEmptyRow(): Int {
            currentRow++
            return currentRow
        }

        fun addBannerRow(text: String, styleIdx: Int, heightPt: Int? = null): Int {
            currentRow++
            val htAttr = if (heightPt != null) """ ht="$heightPt" customHeight="1"""" else ""
            rowsXml.append("""<row r="$currentRow"$htAttr>""")
            val ref = "${colIndexToLetters(1)}$currentRow"
            rowsXml.append("""<c r="$ref" t="inlineStr" s="$styleIdx"><is><t>${xmlEscape(text)}</t></is></c>""")
            rowsXml.append("</row>\n")
            return currentRow
        }

        fun addRow(cells: List<XlsCell>, heightPt: Int? = null): Int {
            currentRow++
            val htAttr = if (heightPt != null) """ ht="$heightPt" customHeight="1"""" else ""
            rowsXml.append("""<row r="$currentRow"$htAttr>""")
            cells.forEachIndexed { idx, cell ->
                val ref = "${colIndexToLetters(idx + 1)}$currentRow"
                when (cell) {
                    is XlsCell.Text -> {
                        trackWidth(idx, cell.value.length)
                        rowsXml.append("""<c r="$ref" t="inlineStr" s="${cell.style}"><is><t>${xmlEscape(cell.value)}</t></is></c>""")
                    }
                    is XlsCell.IntNum -> {
                        trackWidth(idx, cell.value.toString().length + 2)
                        rowsXml.append("""<c r="$ref" s="${cell.style}"><v>${cell.value}</v></c>""")
                    }
                    is XlsCell.Currency -> {
                        trackWidth(idx, LapakViewModel.formatRupiah(cell.value).length + 3)
                        rowsXml.append("""<c r="$ref" s="${cell.style}"><v>${cell.value.toLong()}</v></c>""")
                    }
                    is XlsCell.IntFormula -> {
                        trackWidth(idx, cell.cachedValue.toString().length + 3)
                        rowsXml.append("""<c r="$ref" s="${cell.style}"><f>${xmlEscape(cell.formula)}</f><v>${cell.cachedValue}</v></c>""")
                    }
                    is XlsCell.CurrencyFormula -> {
                        trackWidth(idx, LapakViewModel.formatRupiah(cell.cachedValue).length + 3)
                        rowsXml.append("""<c r="$ref" s="${cell.style}"><f>${xmlEscape(cell.formula)}</f><v>${cell.cachedValue.toLong()}</v></c>""")
                    }
                }
            }
            rowsXml.append("</row>\n")
            return currentRow
        }

        fun buildXml(): String {
            val colsXml = StringBuilder("<cols>\n")
            for (i in 0 until columnCount) {
                val width = (maxChars[i] + 4).coerceIn(6, 44).toDouble()
                colsXml.append("""  <col min="${i + 1}" max="${i + 1}" width="$width" customWidth="1"/>""").append('\n')
            }
            colsXml.append("</cols>\n")
            return buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                append(colsXml)
                appendLine("<sheetData>")
                append(rowsXml)
                appendLine("</sheetData>")
                append("</worksheet>")
            }
        }
    }

    private fun buildSellerSummarySheetXml(
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): String {
        val sheet = FormulaSheetBuilder(11)
        sheet.addBannerRow("${shopProfile.shopName.uppercase()} — REKAP LAPORAN PENYETOR", styleIdx = 1, heightPt = 24)
        sheet.addBannerRow("Periode: ${report.periodLabel} • Cakupan: ${report.scopeLabel}", styleIdx = 2)
        sheet.addBannerRow("Dicetak: ${formatFullDateWib(report.generatedAtMillis)}", styleIdx = 2)
        sheet.addEmptyRow()

        sheet.addRow(
            listOf(
                XlsCell.Text("No", 4),
                XlsCell.Text("Nama Penyetor", 4),
                XlsCell.Text("Kode", 4),
                XlsCell.Text("Status", 4),
                XlsCell.Text("Stok Masuk", 4),
                XlsCell.Text("Terjual", 4),
                XlsCell.Text("Retur/Sisa", 4),
                XlsCell.Text("Komisi (Rp)", 4),
                XlsCell.Text("Total Hak (Rp)", 4),
                XlsCell.Text("Dibayar (Rp)", 4),
                XlsCell.Text("Sisa Setoran (Rp)", 4)
            ),
            heightPt = 26
        )

        val firstDataRow = sheet.currentRow + 1
        if (report.sections.isEmpty()) {
            sheet.addRow(
                listOf(
                    XlsCell.Text("-", 6),
                    XlsCell.Text("Belum ada data penyetor pada periode ini", 5),
                    XlsCell.Text("-", 6),
                    XlsCell.Text("-", 6),
                    XlsCell.IntNum(0, 6),
                    XlsCell.IntNum(0, 6),
                    XlsCell.IntNum(0, 6),
                    XlsCell.Currency(0.0, 7),
                    XlsCell.Currency(0.0, 7),
                    XlsCell.Currency(0.0, 7),
                    XlsCell.Currency(0.0, 7)
                )
            )
        } else {
            report.sections.forEachIndexed { idx, sec ->
                val rowIdx = sheet.currentRow + 1
                sheet.addRow(
                    listOf(
                        XlsCell.IntNum(idx + 1, 6),
                        XlsCell.Text(sec.sellerName, 5),
                        XlsCell.Text(sec.vendorCode.ifBlank { "-" }, 6),
                        XlsCell.Text(sec.paymentStatus, 6),
                        XlsCell.IntNum(sec.totalQtyIn, 6),
                        XlsCell.IntNum(sec.totalQtySold, 6),
                        XlsCell.IntNum(sec.totalQtyLeftover, 6),
                        XlsCell.Currency(sec.totalCommission, 7),
                        XlsCell.Currency(sec.totalSellerShare, 7),
                        XlsCell.Currency(sec.totalPaid, 7),
                        XlsCell.CurrencyFormula("MAX(0,I$rowIdx-J$rowIdx)", sec.remainingBalance, 7)
                    ),
                    heightPt = 20
                )
            }
        }
        val lastDataRow = sheet.currentRow

        // Grand Total row using live Excel SUM formulas
        sheet.addRow(
            listOf(
                XlsCell.Text("", 9),
                XlsCell.Text("TOTAL (${report.sections.size} Penyetor)", 8),
                XlsCell.Text("", 9),
                XlsCell.Text(if (report.grandRemaining <= 0.0) "Lunas" else "Ada Sisa", 9),
                XlsCell.IntFormula("SUM(E$firstDataRow:E$lastDataRow)", report.grandQtyIn, 9),
                XlsCell.IntFormula("SUM(F$firstDataRow:F$lastDataRow)", report.grandQtySold, 9),
                XlsCell.IntFormula("SUM(G$firstDataRow:G$lastDataRow)", report.grandQtyLeftover, 9),
                XlsCell.CurrencyFormula("SUM(H$firstDataRow:H$lastDataRow)", report.grandCommission, 10),
                XlsCell.CurrencyFormula("SUM(I$firstDataRow:I$lastDataRow)", report.grandSellerShare, 10),
                XlsCell.CurrencyFormula("SUM(J$firstDataRow:J$lastDataRow)", report.grandPaid, 10),
                XlsCell.CurrencyFormula("SUM(K$firstDataRow:K$lastDataRow)", report.grandRemaining, 10)
            ),
            heightPt = 22
        )

        return sheet.buildXml()
    }

    private fun buildSellerProductsDetailSheetXml(
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): String {
        val sheet = FormulaSheetBuilder(10)
        sheet.addBannerRow("${shopProfile.shopName.uppercase()} — RINCIAN PRODUK PER PENYETOR", styleIdx = 1, heightPt = 24)
        sheet.addBannerRow("Periode: ${report.periodLabel} • Cakupan: ${report.scopeLabel}", styleIdx = 2)
        sheet.addBannerRow("Dicetak: ${formatFullDateWib(report.generatedAtMillis)}", styleIdx = 2)
        sheet.addEmptyRow()

        val subtotalRowNumbers = mutableListOf<Int>()

        if (report.sections.isEmpty()) {
            sheet.addBannerRow("Belum ada data penyetor pada periode yang dipilih.", styleIdx = 2)
            return sheet.buildXml()
        }

        for (sec in report.sections) {
            val codeSuffix = if (sec.vendorCode.isNotBlank()) " [${sec.vendorCode}]" else ""
            val waSuffix = if (sec.whatsappNumber.isNotBlank()) " • WA: ${sec.whatsappNumber}" else ""
            sheet.addBannerRow(
                "PENYETOR: ${sec.sellerName}$codeSuffix$waSuffix • Tarif: ${sec.tariffLabel}",
                styleIdx = 3,
                heightPt = 21
            )

            sheet.addRow(
                listOf(
                    XlsCell.Text("No", 4),
                    XlsCell.Text("Nama Menu", 4),
                    XlsCell.Text("Stok Masuk", 4),
                    XlsCell.Text("Terjual", 4),
                    XlsCell.Text("Retur/Sisa", 4),
                    XlsCell.Text("Harga Jual (Rp)", 4),
                    XlsCell.Text("Komisi (Rp)", 4),
                    XlsCell.Text("Total Hak (Rp)", 4),
                    XlsCell.Text("Dibayar (Rp)", 4),
                    XlsCell.Text("Sisa Setoran (Rp)", 4)
                ),
                heightPt = 26
            )

            val firstProdRow = sheet.currentRow + 1
            if (sec.productRows.isEmpty()) {
                sheet.addRow(
                    listOf(
                        XlsCell.Text("-", 6),
                        XlsCell.Text("Belum ada produk terdaftar", 5),
                        XlsCell.IntNum(0, 6),
                        XlsCell.IntNum(0, 6),
                        XlsCell.IntNum(0, 6),
                        XlsCell.Currency(0.0, 7),
                        XlsCell.Currency(0.0, 7),
                        XlsCell.Currency(0.0, 7),
                        XlsCell.Text("-", 6),
                        XlsCell.Text("-", 6)
                    ),
                    heightPt = 19
                )
            } else {
                sec.productRows.forEachIndexed { idx, prod ->
                    sheet.addRow(
                        listOf(
                            XlsCell.IntNum(idx + 1, 6),
                            XlsCell.Text("${prod.productName} (${prod.unit})", 5),
                            XlsCell.IntNum(prod.qtyIn, 6),
                            XlsCell.IntNum(prod.qtySold, 6),
                            XlsCell.IntNum(prod.qtyLeftover, 6),
                            XlsCell.Currency(prod.sellingPrice, 7),
                            XlsCell.Currency(prod.totalCommission, 7),
                            XlsCell.Currency(prod.totalSellerShare, 7),
                            XlsCell.Text("-", 6),
                            XlsCell.Text("-", 6)
                        ),
                        heightPt = 19
                    )
                }
            }
            val lastProdRow = sheet.currentRow
            val subRowNum = sheet.currentRow + 1

            // Per-seller Subtotal row with automatic SUM & MAX formulas
            sheet.addRow(
                listOf(
                    XlsCell.Text("", 9),
                    XlsCell.Text("SUBTOTAL ${sec.sellerName}", 8),
                    XlsCell.IntFormula("SUM(C$firstProdRow:C$lastProdRow)", sec.totalQtyIn, 9),
                    XlsCell.IntFormula("SUM(D$firstProdRow:D$lastProdRow)", sec.totalQtySold, 9),
                    XlsCell.IntFormula("SUM(E$firstProdRow:E$lastProdRow)", sec.totalQtyLeftover, 9),
                    XlsCell.Text("-", 9),
                    XlsCell.CurrencyFormula("SUM(G$firstProdRow:G$lastProdRow)", sec.totalCommission, 10),
                    XlsCell.CurrencyFormula("SUM(H$firstProdRow:H$lastProdRow)", sec.totalSellerShare, 10),
                    XlsCell.Currency(sec.totalPaid, 10),
                    XlsCell.CurrencyFormula("MAX(0,H$subRowNum-I$subRowNum)", sec.remainingBalance, 10)
                ),
                heightPt = 22
            )
            subtotalRowNumbers.add(subRowNum)
            sheet.addEmptyRow()
        }

        // Grand Total row when exporting multiple sellers using SUM of all subtotal cells
        if (subtotalRowNumbers.size > 1) {
            fun sumCells(colLetter: String): String =
                "SUM(${subtotalRowNumbers.joinToString(",") { "$colLetter$it" }})"

            sheet.addRow(
                listOf(
                    XlsCell.Text("", 9),
                    XlsCell.Text("GRAND TOTAL SEMUA PENYETOR (${report.sections.size} Dapur)", 8),
                    XlsCell.IntFormula(sumCells("C"), report.grandQtyIn, 9),
                    XlsCell.IntFormula(sumCells("D"), report.grandQtySold, 9),
                    XlsCell.IntFormula(sumCells("E"), report.grandQtyLeftover, 9),
                    XlsCell.Text("-", 9),
                    XlsCell.CurrencyFormula(sumCells("G"), report.grandCommission, 10),
                    XlsCell.CurrencyFormula(sumCells("H"), report.grandSellerShare, 10),
                    XlsCell.CurrencyFormula(sumCells("I"), report.grandPaid, 10),
                    XlsCell.CurrencyFormula(sumCells("J"), report.grandRemaining, 10)
                ),
                heightPt = 24
            )
        }

        return sheet.buildXml()
    }

    // =========================================================================
    // 2. FORMAL A4 LANDSCAPE PDF GENERATOR (READY TO PRINT & SHARE TO WHATSAPP)
    // =========================================================================

    fun generatePdfBytes(
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): ByteArray {
        val pageWidth = 842 // A4 Landscape width in PostScript points
        val pageHeight = 595 // A4 Landscape height in PostScript points
        val marginX = 28f
        val marginTop = 28f
        val marginBottom = 34f
        val contentWidth = pageWidth - marginX * 2f // 786 pt

        val pdf = PdfDocument()
        var pageNum = 1
        var currentPage = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = currentPage.canvas
        var y = marginTop

        val emeraldColor = 0xFF2D7A5E.toInt()
        val emeraldDarkColor = 0xFF1F5C46.toInt()
        val mintLightBg = 0xFFE6F4EE.toInt()
        val sectionBg = 0xFFF0F6F3.toInt()
        val zebraBg = 0xFFF8FBFA.toInt()
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
        val sellerBannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 9.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val sellerBannerSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = emeraldColor }
        val sectionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = sectionBg }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val cellLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 8f
            textAlign = Paint.Align.LEFT
        }
        val cellCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        val cellRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMain
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        val totalLeftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val totalCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 8.2f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val totalRightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldDarkColor
            textSize = 8.2f
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
            val fy = pageHeight - 14f
            pageFooterPaint.textAlign = Paint.Align.LEFT
            c.drawText(
                "${shopProfile.shopName} • Laporan Penyetor (${report.periodLabel}) • Dicetak ${formatFullDateWib(report.generatedAtMillis)}",
                marginX,
                fy,
                pageFooterPaint
            )
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
        y += 17f
        val contactLine = listOf(
            shopProfile.address,
            if (shopProfile.whatsappNumber.isNotBlank()) "WA: ${shopProfile.whatsappNumber}" else ""
        ).filter { it.isNotBlank() }.joinToString(" • ")
        if (contactLine.isNotBlank()) {
            canvas.drawText(clipText(contactLine, metaPaint, contentWidth), centerX, y + 7f, metaPaint)
            y += 12f
        }
        val kopRule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = emeraldColor
            strokeWidth = 1.6f
        }
        canvas.drawLine(marginX, y + 2f, pageWidth - marginX, y + 2f, kopRule)
        y += 13f

        canvas.drawText("LAPORAN RINCIAN PRODUK & SETORAN PENYETOR", centerX, y + 8f, subtitlePaint)
        y += 13f
        canvas.drawText("Periode: ${report.periodLabel}  •  Cakupan: ${report.scopeLabel}", centerX, y + 7f, metaPaint)
        y += 15f

        // === 2. RINGKASAN EKSEKUTIF (4 CARDS) ===
        val cardGap = 8f
        val cardW = (contentWidth - cardGap * 3f) / 4f
        val cardH = 38f
        val summaryCards = listOf(
            Triple("Masuk / Terjual / Sisa", "${report.grandQtyIn} / ${report.grandQtySold} / ${report.grandQtyLeftover} item", 0xFFE6F4EE.toInt()),
            Triple("Total Komisi Lapak", LapakViewModel.formatRupiah(report.grandCommission), 0xFFE0F2FE.toInt()),
            Triple("Total Hak Penyetor", LapakViewModel.formatRupiah(report.grandSellerShare), 0xFFFEF3C7.toInt()),
            Triple("Dibayar / Sisa Setoran", "${formatShortRupiahCell(report.grandPaid)} / ${formatShortRupiahCell(report.grandRemaining)}", 0xFFFCE7F3.toInt())
        )
        val sumTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textMuted
            textSize = 7.5f
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
            canvas.drawText(label, left + 8f, y + 13f, sumTitlePaint)
            canvas.drawText(clipText(value, sumValPaint, cardW - 16f), left + 8f, y + 29f, sumValPaint)
        }
        y += cardH + 14f

        // === 3. TABEL RINCIAN PRODUK PER PENYETOR ===
        // 10 columns totaling contentWidth = 786 pt
        // No(26), Nama Menu(162), Stok Masuk(54), Terjual(48), Retur/Sisa(56), Harga Jual(82), Komisi(82), Total Hak(92), Dibayar(92), Sisa Setoran(92)
        val colWidths = floatArrayOf(26f, 162f, 54f, 48f, 56f, 82f, 82f, 92f, 92f, 92f)
        val colHeaders = listOf(
            "No", "Nama Menu", "Stok Masuk", "Terjual", "Retur/Sisa",
            "Harga Jual", "Komisi", "Total Hak", "Dibayar", "Sisa Setoran"
        )

        fun drawProductTableHeader() {
            val h = 21f
            var x = marginX
            canvas.drawRect(marginX, y, marginX + contentWidth, y + h, headerBgPaint)
            colHeaders.forEachIndexed { i, title ->
                val w = colWidths[i]
                headerTextPaint.textAlign = when {
                    i >= 5 -> Paint.Align.RIGHT
                    i == 1 -> Paint.Align.LEFT
                    else -> Paint.Align.CENTER
                }
                val tx = when (headerTextPaint.textAlign) {
                    Paint.Align.RIGHT -> x + w - 4f
                    Paint.Align.CENTER -> x + w / 2f
                    else -> x + 4f
                }
                canvas.drawText(clipText(title, headerTextPaint, w - 7f), tx, y + 13.5f, headerTextPaint)
                canvas.drawRect(x, y, x + w, y + h, borderPaint)
                x += w
            }
            y += h
        }

        if (report.sections.isEmpty()) {
            canvas.drawRect(marginX, y, marginX + contentWidth, y + 24f, borderPaint)
            cellCenterPaint.color = textMuted
            canvas.drawText("Belum ada data penyetor pada periode yang dipilih.", centerX, y + 15f, cellCenterPaint)
            cellCenterPaint.color = textMain
            y += 28f
        } else {
            val rowH = 17.5f
            val subH = 20f

            for (sec in report.sections) {
                ensureSpace(64f)

                // Seller banner header
                val bannerH = 21f
                canvas.drawRect(marginX, y, marginX + contentWidth, y + bannerH, sectionBgPaint)
                canvas.drawRect(marginX, y, marginX + contentWidth, y + bannerH, borderPaint)
                val leftTitle = buildString {
                    append(sec.sellerName)
                    if (sec.vendorCode.isNotBlank()) append("  [${sec.vendorCode}]")
                }
                val rightMeta = buildString {
                    append("Tarif: ${sec.tariffLabel}")
                    if (sec.whatsappNumber.isNotBlank()) append("  •  WA: ${sec.whatsappNumber}")
                    append("  •  Status: ${sec.paymentStatus}")
                }
                canvas.drawText(clipText(leftTitle, sellerBannerTextPaint, contentWidth * 0.48f), marginX + 7f, y + 13.5f, sellerBannerTextPaint)
                canvas.drawText(clipText(rightMeta, sellerBannerSubPaint, contentWidth * 0.50f), marginX + contentWidth - 7f, y + 13.5f, sellerBannerSubPaint)
                y += bannerH

                drawProductTableHeader()

                if (sec.productRows.isEmpty()) {
                    ensureSpace(rowH) { drawProductTableHeader() }
                    canvas.drawRect(marginX, y, marginX + contentWidth, y + rowH, borderPaint)
                    cellCenterPaint.color = textMuted
                    canvas.drawText("Belum ada produk terdaftar untuk penyetor ini.", centerX, y + 11.5f, cellCenterPaint)
                    cellCenterPaint.color = textMain
                    y += rowH
                } else {
                    sec.productRows.forEachIndexed { idx, prod ->
                        ensureSpace(rowH) { drawProductTableHeader() }
                        if (idx % 2 == 1) {
                            canvas.drawRect(marginX, y, marginX + contentWidth, y + rowH, zebraPaint)
                        }
                        val vals = listOf(
                            (idx + 1).toString(),
                            "${prod.productName} (${prod.unit})",
                            prod.qtyIn.toString(),
                            prod.qtySold.toString(),
                            prod.qtyLeftover.toString(),
                            formatShortRupiahCell(prod.sellingPrice),
                            formatShortRupiahCell(prod.totalCommission),
                            formatShortRupiahCell(prod.totalSellerShare),
                            "-",
                            "-"
                        )
                        var x = marginX
                        vals.forEachIndexed { i, text ->
                            val w = colWidths[i]
                            val p = when {
                                i in 5..7 -> cellRightPaint
                                i == 1 -> cellLeftPaint
                                else -> cellCenterPaint
                            }
                            val tx = when (p.textAlign) {
                                Paint.Align.RIGHT -> x + w - 4f
                                Paint.Align.CENTER -> x + w / 2f
                                else -> x + 4f
                            }
                            canvas.drawText(clipText(text, p, w - 7f), tx, y + 12f, p)
                            canvas.drawRect(x, y, x + w, y + rowH, borderPaint)
                            x += w
                        }
                        y += rowH
                    }
                }

                // Seller Subtotal row (includes Dibayar & Sisa Setoran)
                ensureSpace(subH) { drawProductTableHeader() }
                canvas.drawRect(marginX, y, marginX + contentWidth, y + subH, totalBgPaint)
                var sx = marginX
                val labelMergeW = colWidths[0] + colWidths[1]
                canvas.drawText(
                    clipText("SUBTOTAL ${sec.sellerName}", totalLeftPaint, labelMergeW - 10f),
                    sx + 6f,
                    y + 13f,
                    totalLeftPaint
                )
                canvas.drawRect(sx, y, sx + labelMergeW, y + subH, borderPaint)
                sx += labelMergeW

                val subVals = listOf(
                    sec.totalQtyIn.toString(),
                    sec.totalQtySold.toString(),
                    sec.totalQtyLeftover.toString(),
                    "-",
                    formatShortRupiahCell(sec.totalCommission),
                    formatShortRupiahCell(sec.totalSellerShare),
                    formatShortRupiahCell(sec.totalPaid),
                    formatShortRupiahCell(sec.remainingBalance)
                )
                subVals.forEachIndexed { offset, text ->
                    val i = 2 + offset
                    val w = colWidths[i]
                    val p = if (i >= 6) totalRightPaint else totalCenterPaint
                    val tx = if (i >= 6) sx + w - 4f else sx + w / 2f
                    canvas.drawText(clipText(text, p, w - 7f), tx, y + 13f, p)
                    canvas.drawRect(sx, y, sx + w, y + subH, borderPaint)
                    sx += w
                }
                y += subH + 12f
            }

            // Grand Total Banner if multiple sellers
            if (report.sections.size > 1) {
                ensureSpace(28f)
                val gtH = 22f
                canvas.drawRect(marginX, y, marginX + contentWidth, y + gtH, headerBgPaint)
                var gx = marginX
                val mergeW = colWidths[0] + colWidths[1]
                headerTextPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(
                    clipText("GRAND TOTAL (${report.sections.size} PENYETOR)", headerTextPaint, mergeW - 10f),
                    gx + 6f,
                    y + 14f,
                    headerTextPaint
                )
                canvas.drawRect(gx, y, gx + mergeW, y + gtH, borderPaint)
                gx += mergeW

                val gtVals = listOf(
                    report.grandQtyIn.toString(),
                    report.grandQtySold.toString(),
                    report.grandQtyLeftover.toString(),
                    "-",
                    formatShortRupiahCell(report.grandCommission),
                    formatShortRupiahCell(report.grandSellerShare),
                    formatShortRupiahCell(report.grandPaid),
                    formatShortRupiahCell(report.grandRemaining)
                )
                gtVals.forEachIndexed { offset, text ->
                    val i = 2 + offset
                    val w = colWidths[i]
                    headerTextPaint.textAlign = if (i >= 6) Paint.Align.RIGHT else Paint.Align.CENTER
                    val tx = if (i >= 6) gx + w - 4f else gx + w / 2f
                    canvas.drawText(clipText(text, headerTextPaint, w - 7f), tx, y + 14f, headerTextPaint)
                    canvas.drawRect(gx, y, gx + w, y + gtH, borderPaint)
                    gx += w
                }
                y += gtH + 16f
            }
        }

        // === 4. KOLOM TANDA TANGAN ===
        ensureSpace(78f)
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

        // Optional left signature for Single Seller confirmation
        if (report.isSingleSeller && report.sections.isNotEmpty()) {
            val leftSigX = marginX + 95f
            canvas.drawText("Diterima oleh Penyetor,", leftSigX, y + 12f, sigTextPaint)
            canvas.drawLine(marginX + 20f, y + 54f, marginX + 170f, y + 54f, borderPaint)
            canvas.drawText(report.sections.first().sellerName, leftSigX, y + 66f, sigBoldPaint)
        }

        val rightSigCenterX = pageWidth - marginX - 95f
        canvas.drawText("Disahkan pada ${sigDateFmt.format(Date(report.generatedAtMillis))}", rightSigCenterX, y + 10f, sigTextPaint)
        canvas.drawText("Admin / Pengelola ${shopProfile.shopName}", rightSigCenterX, y + 21f, sigTextPaint)
        canvas.drawLine(pageWidth - marginX - 170f, y + 54f, pageWidth - marginX - 20f, y + 54f, borderPaint)
        canvas.drawText(shopProfile.ownerName.ifBlank { "Admin Lapak" }, rightSigCenterX, y + 66f, sigBoldPaint)

        drawPageFooter(canvas, pageNum)
        pdf.finishPage(currentPage)

        val baos = ByteArrayOutputStream()
        pdf.writeTo(baos)
        pdf.close()
        return baos.toByteArray()
    }

    // =========================================================================
    // 3. SAVE TO DOWNLOADS, OPEN FILE, SHARE FILE & WHATSAPP SLIP FORMATTER
    // =========================================================================

    data class SavedReportFile(
        val displayPath: String,
        val fileName: String,
        val contentUri: Uri,
        val mimeType: String,
        val isExcel: Boolean
    )

    fun saveExcelWithResult(
        context: Context,
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): Result<SavedReportFile> {
        return try {
            val bytes = generateExcelBytes(shopProfile, report)
            val slug = if (report.isSingleSeller) sanitizeSlug(report.scopeLabel) else "Semua_Penyetor"
            val fileName = "Laporan_Penyetor_${slug}_${formatStampForFile(report.generatedAtMillis)}.xlsx"
            saveBytesToDownloadsWithUri(
                context = context,
                fileName = fileName,
                mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes = bytes,
                isExcel = true
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun savePdfWithResult(
        context: Context,
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): Result<SavedReportFile> {
        return try {
            val bytes = generatePdfBytes(shopProfile, report)
            val slug = if (report.isSingleSeller) sanitizeSlug(report.scopeLabel) else "Semua_Penyetor"
            val fileName = "Laporan_Penyetor_${slug}_${formatStampForFile(report.generatedAtMillis)}.pdf"
            saveBytesToDownloadsWithUri(
                context = context,
                fileName = fileName,
                mimeType = "application/pdf",
                bytes = bytes,
                isExcel = false
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveExcelToDownloads(
        context: Context,
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): Result<String> = saveExcelWithResult(context, shopProfile, report).map { it.displayPath }

    fun savePdfToDownloads(
        context: Context,
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot
    ): Result<String> = savePdfWithResult(context, shopProfile, report).map { it.displayPath }

    private fun saveBytesToDownloadsWithUri(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        isExcel: Boolean
    ): Result<SavedReportFile> {
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
                ?: return Result.failure(IllegalStateException("Gagal menulis file laporan penyetor"))
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Result.success(
                SavedReportFile(
                    displayPath = "Downloads/Lapak Kita/$fileName",
                    fileName = fileName,
                    contentUri = uri,
                    mimeType = mimeType,
                    isExcel = isExcel
                )
            )
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Lapak Kita").apply { mkdirs() }
            val outFile = File(dir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            Result.success(
                SavedReportFile(
                    displayPath = outFile.absolutePath,
                    fileName = fileName,
                    contentUri = uri,
                    mimeType = mimeType,
                    isExcel = isExcel
                )
            )
        }
    }

    fun openSavedReportFile(context: Context, savedFile: SavedReportFile): Result<Unit> {
        return try {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(savedFile.contentUri, savedFile.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(openIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException("Tidak ditemukan aplikasi pembuka file ${if (savedFile.isExcel) "Excel (.xlsx)" else "PDF"} di perangkat ini.")
            )
        }
    }

    fun shareSavedReportFile(
        context: Context,
        savedFile: SavedReportFile,
        caption: String,
        preferWhatsApp: Boolean = false
    ): Result<Unit> {
        return try {
            if (preferWhatsApp) {
                val waIntent = Intent(Intent.ACTION_SEND).apply {
                    type = savedFile.mimeType
                    putExtra(Intent.EXTRA_STREAM, savedFile.contentUri)
                    putExtra(Intent.EXTRA_TEXT, caption)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(waIntent)
                    return Result.success(Unit)
                } catch (_: Exception) {
                    // Fallback to system chooser
                }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = savedFile.mimeType
                putExtra(Intent.EXTRA_STREAM, savedFile.contentUri)
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Bagikan Laporan Penyetor").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildSellerWhatsAppSlipText(
        shopProfile: ShopProfile,
        periodLabel: String,
        section: SellerPeriodSection
    ): String = buildString {
        appendLine("*SLIP SETORAN PENYETOR*")
        appendLine("*${shopProfile.shopName.ifBlank { "Lapak Kita" }}*")
        appendLine("==============================")
        appendLine("Penyetor : *${section.sellerName}*")
        if (section.vendorCode.isNotBlank()) {
            appendLine("Kode ID  : ${section.vendorCode}")
        }
        appendLine("Periode  : $periodLabel")
        appendLine("------------------------------")
        if (section.productRows.isEmpty()) {
            appendLine("_Belum ada aktivitas produk pada periode ini._")
        } else {
            appendLine("*Rincian Produk:*")
            section.productRows.forEachIndexed { idx, row ->
                appendLine("${idx + 1}. *${row.productName}*")
                appendLine("   Masuk: ${row.qtyIn} | Laku: ${row.qtySold} | Sisa/Retur: ${row.qtyLeftover} ${row.unit}")
                appendLine("   Harga: ${LapakViewModel.formatRupiah(row.sellingPrice)} | Hak: *${LapakViewModel.formatRupiah(row.totalSellerShare)}*")
            }
        }
        appendLine("------------------------------")
        appendLine("Total Stok Masuk : ${section.totalQtyIn} item")
        appendLine("Total Terjual    : ${section.totalQtySold} item")
        appendLine("Total Retur/Sisa : ${section.totalQtyLeftover} item")
        appendLine("------------------------------")
        appendLine("Total Hak        : *${LapakViewModel.formatRupiah(section.totalSellerShare)}*")
        appendLine("Sudah Dibayar    : ${LapakViewModel.formatRupiah(section.totalPaid)}")
        appendLine("Sisa Setoran     : *${LapakViewModel.formatRupiah(section.remainingBalance)}*")
        appendLine("Status           : ${section.paymentStatus}")
        appendLine("==============================")
        if (shopProfile.footerMessage.isNotBlank()) {
            appendLine(shopProfile.footerMessage)
        } else {
            appendLine("Terima kasih atas kerja samanya!")
        }
    }.trim()

    fun shareReportFile(
        context: Context,
        shopProfile: ShopProfile,
        report: SellerPeriodReportSnapshot,
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
            val slug = if (report.isSingleSeller) sanitizeSlug(report.scopeLabel) else "Semua_Penyetor"
            val fileName = "Laporan_Penyetor_${slug}_${formatStampForFile(report.generatedAtMillis)}.$ext"
            val cacheFolder = File(context.cacheDir, "reports").apply { mkdirs() }
            val file = File(cacheFolder, fileName)
            FileOutputStream(file).use { it.write(bytes) }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val caption = "Laporan Penyetor (${report.scopeLabel}) — ${shopProfile.shopName} • Periode: ${report.periodLabel}"

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
            val chooser = Intent.createChooser(shareIntent, "Bagikan Laporan Penyetor").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
