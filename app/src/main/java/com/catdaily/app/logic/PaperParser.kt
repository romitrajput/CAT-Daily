package com.catdaily.app.logic

import com.catdaily.app.data.Question
import com.catdaily.app.data.Region
import com.catdaily.app.data.Section
import com.catdaily.app.data.Visual

/**
 * Pure-Kotlin structure parser for CAT-style papers (no Android / PDF library code, so it is
 * unit-testable). Input: text lines with page coordinates. Output: tagged questions + crop regions.
 */
class PaperParser(private val classifier: Classifier) {

    data class Line(val page: Int, val top: Float, val bottom: Float, val x0: Float, val s: String) {
        var kind: String? = null
        var slot: Int = 0
    }
    data class PageInfo(val w: Float, val h: Float, val contentTop: Float, val contentBottom: Float)

    /** Marks headers/footers, tracks slots, and returns content lines + page bounds. */
    fun layout(raw: List<Line>, sizes: List<Pair<Float, Float>>): Pair<MutableList<Line>, List<PageInfo>> {
        val nPages = sizes.size
        // header / footer detection: repeated text near the top/bottom, or "Actual CAT", "Slot", page numbers
        val pageInfos = ArrayList<PageInfo>()
        val norm = { s: String -> s.replace(Regex("\\d+"), "#").lowercase() }
        // running headers repeat at the same height on many pages (keyed by text + y-band)
        val band = { l: Line -> norm(l.s) + "@" + (l.top / 6f).toInt() }
        val repeated = raw.groupBy(band).filter { (_, v) -> v.map { it.page }.distinct().size >= maxOf(3, nPages * 2 / 5) }.keys
        val hdr = Regex("(Actual CAT|^Slot\\s*-?\\s*(I{1,3}|[1-3])\\b|^\\d{1,3}$|^\\d{1,3}\\s+Slot)", RegexOption.IGNORE_CASE)
        for (p in 0 until nPages) {
            val (w, h) = sizes[p]
            val pl = raw.filter { it.page == p }
            pl.forEach {
                val nearTop = it.top < h * 0.14f; val nearBottom = it.top > h * 0.92f
                val numeric = it.s.matches(Regex("\\d{1,3}"))
                // bare numbers only count as page numbers very close to the edge (table cells can sit near the top)
                val edge = if (numeric) it.top < h * 0.105f || nearBottom else nearTop || nearBottom
                val structural = it.s.startsWith("Question No", true) || it.s.startsWith("DIRECTIONS", true) ||
                    it.s.startsWith("SECTION", true) || Regex("^\\d{1,2}\\.\\s+\\S").containsMatchIn(it.s)
                if (edge && !structural && (band(it) in repeated || hdr.containsMatchIn(it.s))) it.kind = if (nearTop) "hdr" else "ftr"
                if (it.s.startsWith("Actual CAT")) it.kind = it.kind ?: "hdr"
            }
            val ct = pl.filter { it.kind == "hdr" }.maxOfOrNull { it.bottom + 3f } ?: 30f
            val cb = pl.filter { it.kind == "ftr" }.minOfOrNull { it.top - 3f } ?: (h - 25f)
            pageInfos += PageInfo(w, h, ct, cb)
        }
        // slot tracking (from header text) before dropping headers
        var slot = 0
        val slotRe = Regex("Slot\\s*-?\\s*(III|II|I|[1-3])\\b", RegexOption.IGNORE_CASE)
        for (l in raw) {
            slotRe.find(l.s)?.let { m ->
                val v = when (m.groupValues[1].uppercase()) { "I", "1" -> 1; "II", "2" -> 2; "III", "3" -> 3; else -> 0 }
                if (v == slot + 1 && (l.kind != null || l.s.length < 40)) slot = v
            }
            l.slot = maxOf(slot, 1)
        }
        val content = raw.filter { it.kind == null }.toMutableList()
        content.forEach { it.kind = null }
        return content to pageInfos
    }

    // ------------------------------------------------------------------ structure
    private data class Item(val slot: Int, val sec: Section, val no: Int, val start: Int, val gid: Int, val gstart: Int) { var end = 0 }

    private val secRe = Regex("^\\s*SECTION\\s*(?:-\\s*I{1,3}\\s*)?[:\\-]?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val qaRe = Regex("^Question No\\.?\\s*:?\\s*(\\d+)\\b", RegexOption.IGNORE_CASE)
    private val qbRe = Regex("^(\\d{1,2})\\.\\s+")

    private fun secName(s: String): Section? {
        val l = s.lowercase()
        return when {
            "verbal" in l || "reading" in l -> Section.VARC
            "quant" in l -> Section.QA
            "di" in l || "data" in l || "reason" in l -> Section.DILR
            else -> null
        }
    }

    fun build(flat: MutableList<Line>, pages: List<PageInfo>, year: Int, pdf: String, addedDay: Int, warnings: MutableList<String>)
        : Pair<List<Question>, Map<String, Visual>> {
        val fmtA = flat.any { qaRe.containsMatchIn(it.s) }
        val leftMargin = flat.groupBy { it.page }.mapValues { (_, v) -> v.minOf { it.x0 } }
        val items = ArrayList<Item>()
        var sec: Section? = null; var expected = 1; var curSlot = 0; var gid = 0; var gstart = -1; var inGroup = false
        var autoSlotBump = 0
        for ((i, l) in flat.withIndex()) {
            val slot = l.slot + autoSlotBump
            if (slot != curSlot) { curSlot = slot; expected = 1; sec = null; inGroup = false }
            secRe.find(l.s)?.let { m ->
                val sn = secName(m.groupValues[1])
                if (sn != null && l.s.length < 80) { sec = sn; l.kind = "sec"; inGroup = false }
            }
            if (l.kind == "sec") continue
            if (l.s.startsWith("DIRECTIONS", ignoreCase = true)) { gid++; gstart = i; inGroup = true; l.kind = "dir"; continue }
            val n: Int? = if (fmtA) qaRe.find(l.s)?.groupValues?.get(1)?.toIntOrNull()
            else qbRe.find(l.s)?.groupValues?.get(1)?.toIntOrNull()?.takeIf {
                it == expected && l.x0 < (leftMargin[l.page] ?: 0f) + 25f
            }
            if (n != null) {
                // numbering restarted without a "Slot" marker -> next paper/slot
                if (fmtA && n == 1 && items.isNotEmpty() && items.last().slot == curSlot && items.last().no > 1) {
                    autoSlotBump++; curSlot++
                }
                val s = sec ?: run { warnings += "Question $n appears before any SECTION heading — skipped."; null }
                if (s != null) {
                    if (!inGroup) { gid++; gstart = i; inGroup = true }
                    items += Item(curSlot, s, n, i, gid, gstart)
                    l.kind = "q"; expected = n + 1
                }
            }
        }
        for (it in items) {
            it.end = (it.start + 1 until flat.size).firstOrNull { j -> flat[j].kind in setOf("q", "dir", "sec") } ?: flat.size
        }

        val out = ArrayList<Question>(); val groups = HashMap<String, Visual>()
        val prefix = year.toString()
        for ((g, qs) in items.groupBy { it.gid }) {
            val first = qs.first()
            val gkey = "$prefix-${first.slot}-g$g"
            var ctxStart = first.gstart; var ctxEnd = if (first.gstart < first.start) first.start else first.gstart
            var q1Start = first.start
            if (fmtA && qs.size > 1) {
                val ls = flat.subList(first.start + 1, first.end)
                val opt = ls.indexOfFirst { it.s.startsWith("A)") }
                val anchor = if (opt >= 0) opt else ls.size
                val gaps = (0 until ls.size - 1).filter { ls[it + 1].page == ls[it].page }.map { ls[it + 1].top - ls[it].top }.sorted()
                val med = if (gaps.isEmpty()) 12f else gaps[gaps.size / 2]
                var split: Int? = null
                for (k in anchor - 1 downTo 1) {
                    if (ls[k].page == ls[k - 1].page && ls[k].top - ls[k - 1].top > 1.55f * med) { split = k; break }
                    if (ls[k].page != ls[k - 1].page && anchor - k > 6) { split = k; break }
                }
                if (split != null && split > 2) { q1Start = first.start + 1 + split; ctxEnd = q1Start }
            }
            val directions = (first.gstart until first.start).joinToString(" ") { flat[it].s }
            val ctxText = (ctxStart until ctxEnd).joinToString("\n") { flat[it].s }
            val ctxRegs = if (ctxEnd > ctxStart) regions(flat, pages, ctxStart, ctxEnd, ctxEnd) else emptyList()
            if (ctxRegs.isNotEmpty()) groups[gkey] = Visual(pdf, ctxRegs)

            for (it in qs) {
                val s = if (it === first) q1Start else it.start
                val body = (it.start until it.end).joinToString("\n") { j -> flat[j].s }
                var labels = optionLabels(body, fmtA)
                if (it.sec == Section.VARC && Regex("key in|type in|enter the|sequence of (four|five|six) numbers", RegexOption.IGNORE_CASE).containsMatchIn(directions)) labels = emptyList()
                if (Regex("\\(type in|type your answer|key in your answer", RegexOption.IGNORE_CASE).containsMatchIn(body)) labels = emptyList()
                var c = classifier.classify(it.sec, body, directions, ctxText, qs.size, labels.isEmpty())
                // CAT para-jumbles and odd-one-out are always TITA
                if (labels.isNotEmpty() && c.topic in setOf("Para Jumbles", "Odd Sentence Out")) {
                    labels = emptyList(); c = classifier.classify(it.sec, body, directions, ctxText, qs.size, true)
                }
                val tita = labels.isEmpty()
                out += Question(
                    id = "$prefix-S${it.slot}-${it.sec.name}-Q${it.no}", year = year, slot = it.slot, section = it.sec,
                    number = it.no, type = if (tita) "TITA" else "MCQ", labels = labels, topic = c.topic, sub = c.sub,
                    difficulty = c.difficulty, score = c.score, group = if (ctxRegs.isNotEmpty()) gkey else null,
                    visual = Visual(pdf, regions(flat, pages, s, it.end, it.end)),
                    text = classifier.stem(body).replace(Regex("\\s+"), " ").trim().take(500),
                    source = "CAT $year Slot ${it.slot}", addedDay = addedDay
                )
            }
        }
        return out to groups
    }

    private fun optionLabels(body: String, fmtA: Boolean): List<String> {
        if (body.contains("A)") && body.contains("B)")) return listOf("A", "B", "C", "D")
        if (fmtA) return emptyList()
        if (Regex("(?m)^A\\.\\s").containsMatchIn(body) && body.contains(Regex("B\\.\\s"))) return listOf("A", "B", "C", "D")
        val rest = body.replaceFirst(Regex("^\\s*\\d{1,2}\\.\\s+"), "")
        val seq = Regex("(?:^|\\n|\\s{2,})([1-5])\\.\\s").findAll(rest).map { it.groupValues[1].toInt() }.toList()
        if (seq.size >= 4 && seq.takeLast(4) == listOf(1, 2, 3, 4) && 5 !in seq.takeLast(5)) return listOf("1", "2", "3", "4")
        return emptyList()
    }

    /** Lines [a, b) -> vertical strips per page; the strip ends where the next block starts. */
    private fun regions(flat: List<Line>, pages: List<PageInfo>, a: Int, b: Int, next: Int): List<Region> {
        if (a >= b) return emptyList()
        val ps = flat[a].page; val pl = flat[b - 1].page
        val nm = flat.getOrNull(next)
        val out = ArrayList<Region>()
        for (p in ps..pl) {
            val pi = pages[p]
            val y0 = if (p == ps) flat[a].top - 4f else pi.contentTop
            val y1 = if (p == pl && nm != null && nm.page == pl) nm.top - 3f else pi.contentBottom
            if (y1 - y0 > 6f) out += Region(p, y0, y1)
        }
        // a chart/table that slipped onto the next page above the next block
        if (nm != null && nm.page == pl + 1) {
            val pi = pages[pl + 1]
            if (nm.top - pi.contentTop > 60f) out += Region(pl + 1, pi.contentTop, nm.top - 3f)
        }
        return out
    }
}
