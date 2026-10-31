package com.catdaily.app.importer

import android.content.Context
import android.net.Uri
import com.catdaily.app.data.Question
import com.catdaily.app.data.Section
import com.catdaily.app.data.Visual
import com.catdaily.app.logic.Classifier
import com.catdaily.app.logic.PaperParser
import com.catdaily.app.logic.PaperParser.Line
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs

/**
 * Turns a CAT-style question paper PDF into tagged questions — the same pipeline that built the
 * bundled 2017-2025 bank:
 *   text lines with coordinates -> slots / SECTION headings / DIRECTIONS blocks / question numbers
 *   -> crop regions (so passages, tables and charts show exactly as printed)
 *   -> MCQ vs TITA -> topic + Easy/Medium/Hard via [Classifier].
 *
 * Supported numbering styles: "Question No. : 12" and "12." at the left margin.
 */
class PdfImporter(private val ctx: Context, private val classifier: Classifier) {

    data class Result(
        val year: Int?,
        val questions: List<Question>,
        val groups: Map<String, Visual>,
        val pdfPath: String,
        val warnings: List<String>
    ) {
        fun counts() = questions.groupingBy { it.section }.eachCount()
    }

    private class Glyph(val page: Int, val x0: Float, val x1: Float, val base: Float, val h: Float, val ch: String)

    private class Collector : PDFTextStripper() {
        val glyphs = ArrayList<Glyph>()
        init { setSortByPosition(true) }
        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            val p = currentPageNo - 1
            textPositions?.forEach { tp ->
                glyphs.add(Glyph(p, tp.xDirAdj, tp.xDirAdj + tp.widthDirAdj, tp.yDirAdj, tp.heightDir, tp.unicode ?: ""))
            }
        }
    }

    /** Copies the picked PDF into app storage and parses it. [yearOverride]/[label] come from the admin dialog. */
    fun import(uri: Uri, yearOverride: Int?, addedDay: Int): Result {
        val parser = PaperParser(classifier)
        PDFBoxResourceLoader.init(ctx)
        val dir = File(ctx.filesDir, "pdfs").apply { mkdirs() }
        val dest = File(dir, "import_${System.currentTimeMillis()}.pdf")
        ctx.contentResolver.openInputStream(uri)!!.use { inp -> dest.outputStream().use { inp.copyTo(it) } }

        val (raw, sizes) = PDDocument.load(dest).use { doc -> extractLines(doc) }
        val (lines, pages) = parser.layout(raw, sizes)
        val warnings = mutableListOf<String>()
        val allText = lines.joinToString("\n") { it.s }
        val detectedYear = Regex("CAT\\s*[-–]?\\s*(20\\d\\d)").find(allText)?.groupValues?.get(1)?.toIntOrNull()
        val year = yearOverride ?: detectedYear ?: run {
            warnings += "Year not found in the PDF — using the current year."
            java.time.LocalDate.now().year
        }
        val (qs, gs) = parser.build(lines, pages, year, "file:${dest.absolutePath}", addedDay, warnings)
        if (qs.isEmpty()) warnings += "No questions found. The PDF must use 'Question No. : N' or 'N.' numbering with SECTION headings."
        return Result(detectedYear ?: yearOverride, qs, gs, dest.absolutePath, warnings)
    }

    // ------------------------------------------------------------------ text lines
    private fun extractLines(doc: PDDocument): Pair<List<Line>, List<Pair<Float, Float>>> {
        val c = Collector()
        c.startPage = 1; c.endPage = doc.numberOfPages
        c.getText(doc)
        val raw = ArrayList<Line>()
        c.glyphs.groupBy { it.page }.toSortedMap().forEach { (p, gl) ->
            val sorted = gl.sortedWith(compareBy({ it.base }, { it.x0 }))
            val rows = ArrayList<MutableList<Glyph>>()
            for (g in sorted) {
                val row = rows.lastOrNull()
                if (row != null && abs(row[0].base - g.base) < 2.5f) row.add(g) else rows.add(mutableListOf(g))
            }
            for (row in rows) {
                row.sortBy { it.x0 }
                val sb = StringBuilder(); var prevX1: Float? = null
                for (g in row) {
                    if (prevX1 != null && g.ch != " " && !sb.endsWith(" ")) {
                        val gap = g.x0 - prevX1
                        if (gap > 12f) sb.append("   ") else if (gap > 1.5f) sb.append(' ')
                    }
                    sb.append(g.ch); prevX1 = g.x1
                }
                val s = sb.toString().replace('–', '-').replace('—', '-').trim()
                if (s.isEmpty()) continue
                val base = row.maxOf { it.base }
                val h = maxOf(row.maxOf { it.h }, 8f)
                raw += Line(p, base - h, base + 2f, row.first().x0, s)
            }
        }
        val sizes = (0 until doc.numberOfPages).map { val b = doc.getPage(it).mediaBox; b.width to b.height }
        return raw to sizes
    }
}
