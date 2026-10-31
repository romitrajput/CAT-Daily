package com.catdaily.app.logic

import android.content.Context
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Section
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Topic + difficulty tagger. Same rules (assets/taxonomy.json) and same scoring as the script
 * used to tag the bundled CAT 2017-2025 papers, so imported PDFs are tagged consistently.
 */
class Classifier(ctx: Context, private val cuts: Map<Section, Pair<Double, Double>>) {
    private val t = JSONObject(ctx.assets.open("taxonomy.json").bufferedReader().readText())
    private val CI = Pattern.CASE_INSENSITIVE

    data class Rule(val topic: String, val sub: String?, val re: Pattern, val w: Double)
    data class Result(val topic: String, val sub: String, val difficulty: Difficulty, val score: Double)

    private fun rules(key: String): List<Rule> {
        val a = t.getJSONArray(key)
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Rule(o.getString("topic"), o.optString("sub").ifEmpty { null }, Pattern.compile(o.getString("re"), CI), o.optDouble("w", 1.0))
        }
    }
    private val dilr = rules("DILR")
    private val qa = rules("QA")
    private val varcDir = t.getJSONObject("VARC").getJSONArray("byDirections").let { a ->
        (0 until a.length()).map { val o = a.getJSONObject(it); Rule(o.getString("topic"), o.getString("sub"), Pattern.compile(o.getString("re"), CI), 1.0) }
    }
    private val rcSub = t.getJSONObject("VARC").getJSONArray("rcSub").let { a ->
        (0 until a.length()).map { val o = a.getJSONObject(it); o.getString("sub") to Pattern.compile(o.getString("re"), CI) }
    }
    private val hardCues = Pattern.compile(t.getJSONObject("difficulty").getString("hardCues"), CI)
    private val exceptCues = Pattern.compile(t.getJSONObject("difficulty").getString("exceptCues"))
    private val fallback = t.optJSONObject("QAFallback")

    private fun count(p: Pattern, s: String): Int { val m = p.matcher(s); var n = 0; while (m.find()) n++; return n }
    private fun words(s: String) = count(Pattern.compile("[A-Za-z0-9']+"), s)
    private fun best(rs: List<Rule>, text: String): Rule? {
        val sc = LinkedHashMap<Pair<String, String?>, Double>()
        val first = LinkedHashMap<Pair<String, String?>, Rule>()
        for (r in rs) {
            val n = count(r.re, text)
            if (n > 0) { val k = r.topic to r.sub; sc[k] = (sc[k] ?: 0.0) + n * r.w; first.putIfAbsent(k, r) }
        }
        val k = sc.maxByOrNull { it.value }?.key ?: return null
        return first[k]
    }

    /** Strip "Question No. : 12" / "12." and cut at the first option. */
    fun stem(text: String): String {
        val t0 = text.replaceFirst(Regex("^\\s*(Question No\\.?\\s*:?\\s*\\d+|\\d{1,2}\\.)\\s*"), "")
        return t0.split(Regex("\\n\\s*(?:A\\)|A\\.|1\\.)\\s"))[0]
    }

    fun classify(section: Section, text: String, directions: String, context: String, groupSize: Int, tita: Boolean): Result {
        val st = stem(text)
        var topic: String; var sub: String; var s: Double
        when (section) {
            Section.VARC -> {
                val d = varcDir.firstOrNull { it.re.matcher(directions).find() }
                topic = d?.topic ?: "Reading Comprehension"; sub = d?.sub ?: ""
                if (d == null || groupSize > 1) {
                    topic = "Reading Comprehension"
                    sub = rcSub.first { it.second.matcher(st).find() }.first
                }
                s = when (topic) {
                    "Reading Comprehension" -> {
                        val ws = Regex("[A-Za-z]+").findAll(context).map { it.value.length }.toList().ifEmpty { listOf(1) }
                        val avg = ws.sum().toDouble() / ws.size
                        var v = minOf(words(context) / 250.0, 2.5) + maxOf(0.0, avg - 4.6) * 1.5 + when (sub) {
                            "Inference" -> 0.5; "Strengthen / Weaken" -> 0.6; "Central Idea / Title" -> 0.35
                            "Tone & Author's View" -> 0.4; "Vocabulary & Purpose" -> 0.15; else -> 0.0
                        }
                        if (exceptCues.matcher(st).find()) v += 0.5
                        v
                    }
                    "Para Jumbles" -> 1.9 + if (count(Pattern.compile("(?m)^\\s*(?:\\d{1,2}\\.\\s+)?[1-6][\\.\\)]\\s"), text) >= 5) 0.8 else 0.0
                    "Odd Sentence Out" -> 2.2
                    "Para Summary" -> 1.2 + minOf(words(text) / 220.0, 1.2)
                    else -> 0.9 + minOf(words(text) / 220.0, 1.2)
                }
            }
            Section.DILR -> {
                val r = best(dilr, "$context $st")
                topic = r?.topic ?: "Tables & Charts (DI)"; sub = ""
                val conds = count(Pattern.compile("(?m)^\\s*\\d{1,2}[\\.\\)]\\s"), context)
                s = minOf(words(context) / 130.0, 2.5) + minOf(conds * 0.15, 1.2) + minOf(words(st) / 45.0, 1.0)
                s += when (topic) {
                    "Games & Tournaments", "Grids, Cubes & Puzzles" -> 0.5; "Seating & Arrangements" -> 0.3
                    "Tables & Charts (DI)" -> -0.3; else -> 0.0
                }
                if (tita) s += 0.6
                if (hardCues.matcher(st).find()) s += 0.8
            }
            Section.QA -> {
                val r = if (words(st) >= 6) best(qa, st) else null
                topic = r?.topic ?: fallback?.optString("topic") ?: "Algebra"
                sub = r?.sub ?: fallback?.optString("sub") ?: ""
                s = minOf(words(st) / 40.0, 2.5) + (if (tita) 0.9 else 0.0)
                s += when (topic) { "Modern Maths", "Number System" -> 0.6; "Geometry & Mensuration", "Algebra" -> 0.5; else -> 0.0 }
                s += when (sub) { "Functions & Graphs", "Logarithms & Surds" -> 0.3; "Mixtures & Alligation", "Time, Speed & Distance" -> 0.2; else -> 0.0 }
                if (hardCues.matcher(st).find()) s += 0.8
                if (count(Pattern.compile("\\d+"), st) > 7) s += 0.3
            }
        }
        val (e, m) = cuts[section] ?: (2.0 to 3.0)
        val diff = if (s < e) Difficulty.Easy else if (s < m) Difficulty.Medium else Difficulty.Hard
        return Result(topic, sub, diff, s)
    }
}
