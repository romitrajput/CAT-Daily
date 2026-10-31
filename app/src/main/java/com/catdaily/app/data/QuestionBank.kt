package com.catdaily.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * All questions available to the app:
 *  1. bundled  – assets/questions.json, parsed from the CAT 2017-2025 PDFs shipped in assets/pdfs
 *  2. imported – PDFs the admin added from Settings on this device (filesDir/imported.json)
 *  3. cloud    – PDFs the admin imported on another device, synced via Firestore (filesDir/cloud_bank.json)
 * Admin tag edits (topic/difficulty) are kept in overrides.json and applied on top.
 */
class QuestionBank(private val ctx: Context) {

    var questions: List<Question> = emptyList(); private set
    var byId: Map<String, Question> = emptyMap(); private set
    private var groups: Map<String, Visual> = emptyMap()
    var cuts: Map<Section, Pair<Double, Double>> = emptyMap(); private set
    private var answers: MutableMap<String, String> = mutableMapOf()

    private val importedFile get() = File(ctx.filesDir, "imported.json")
    private val cloudFile get() = File(ctx.filesDir, "cloud_bank.json")
    private val overridesFile get() = File(ctx.filesDir, "overrides.json")
    private val answersFile get() = File(ctx.filesDir, "answers_local.json")

    fun load() {
        val all = LinkedHashMap<String, Question>()
        val grp = HashMap<String, Visual>()

        val bundled = JSONObject(ctx.assets.open("questions.json").bufferedReader().readText())
        bundled.optJSONObject("cuts")?.let { c ->
            cuts = Section.entries.associateWith { s ->
                val a = c.getJSONArray(s.name); a.getDouble(0) to a.getDouble(1)
            }
        }
        readInto(bundled, all, grp, null)
        if (cloudFile.exists()) readInto(JSONObject(cloudFile.readText()), all, grp, "images")
        if (importedFile.exists()) readInto(JSONObject(importedFile.readText()), all, grp, null)

        if (overridesFile.exists()) {
            val o = JSONObject(overridesFile.readText())
            for (k in o.keys()) all[k]?.let { q ->
                val v = o.getJSONObject(k)
                q.topic = v.optString("topic", q.topic)
                q.sub = v.optString("sub", q.sub)
                q.difficulty = Difficulty.valueOf(v.optString("difficulty", q.difficulty.name))
            }
        }
        answers = mutableMapOf()
        loadAnswerJson(JSONObject(ctx.assets.open("answers.json").bufferedReader().readText()))
        if (answersFile.exists()) loadAnswerJson(JSONObject(answersFile.readText()))

        questions = all.values.toList()
        byId = all
        groups = grp
    }

    private fun loadAnswerJson(o: JSONObject) {
        for (k in o.keys()) answers[k] = o.getString(k)
    }

    private fun readInto(root: JSONObject, out: MutableMap<String, Question>, grp: MutableMap<String, Visual>, imgKey: String?) {
        val qs = root.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until qs.length()) {
            val o = qs.getJSONObject(i)
            val imgs = if (imgKey != null) o.optJSONArray(imgKey).toStrings() else emptyList()
            val q = Question.fromJson(o, imgs)
            out[q.id] = q
        }
        val gs = root.optJSONObject("groups") ?: JSONObject()
        for (k in gs.keys()) {
            val g = gs.getJSONObject(k)
            val regs = g.optJSONArray("regions") ?: JSONArray()
            grp[k] = Visual(
                if (g.isNull("pdf")) null else g.optString("pdf"),
                (0 until regs.length()).map { Region.fromJson(regs.getJSONArray(it)) },
                if (imgKey != null) g.optJSONArray(imgKey).toStrings() else emptyList()
            )
        }
    }

    fun groupVisual(q: Question): Visual? = q.group?.let { groups[it] }

    fun sameGroup(q: Question): List<Question> =
        if (q.group == null) listOf(q) else questions.filter { it.group == q.group }.sortedBy { it.number }

    // ---------- answers ----------
    fun answerFor(qid: String): String? = answers[qid]
    val answerCount get() = answers.size

    /** Returns null when no key is available. TITA answers compare numerically when possible. */
    fun check(q: Question, given: String): Boolean? {
        val key = answers[q.id] ?: return null
        val g = given.trim()
        return key.split("|").any { k ->
            val kk = k.trim()
            val a = kk.toDoubleOrNull(); val b = g.toDoubleOrNull()
            if (a != null && b != null) kotlin.math.abs(a - b) < 1e-6
            else kk.equals(g, ignoreCase = true) || kk.replace(" ", "").equals(g.replace(" ", ""), true)
        }
    }

    fun mergeAnswers(newOnes: Map<String, String>) {
        val o = if (answersFile.exists()) JSONObject(answersFile.readText()) else JSONObject()
        newOnes.forEach { (k, v) -> o.put(k, v) }
        answersFile.writeText(o.toString())
        answers.putAll(newOnes)
    }

    // ---------- admin edits ----------
    fun overrideTags(q: Question, topic: String, sub: String, difficulty: Difficulty) {
        val o = if (overridesFile.exists()) JSONObject(overridesFile.readText()) else JSONObject()
        o.put(q.id, JSONObject().put("topic", topic).put("sub", sub).put("difficulty", difficulty.name))
        overridesFile.writeText(o.toString())
        q.topic = topic; q.sub = sub; q.difficulty = difficulty
    }

    fun overridesJson(): JSONObject = if (overridesFile.exists()) JSONObject(overridesFile.readText()) else JSONObject()

    fun applyOverrides(o: JSONObject) {
        val cur = overridesJson()
        for (k in o.keys()) cur.put(k, o.getJSONObject(k))
        overridesFile.writeText(cur.toString())
        load()
    }

    /** Save questions parsed from an admin-imported PDF (replaces any with the same id). */
    fun addImported(newQs: List<Question>, newGroups: Map<String, Visual>) {
        val root = if (importedFile.exists()) JSONObject(importedFile.readText())
        else JSONObject().put("questions", JSONArray()).put("groups", JSONObject())
        val ids = newQs.map { it.id }.toSet()
        val old = root.getJSONArray("questions")
        val keep = JSONArray()
        for (i in 0 until old.length()) if (old.getJSONObject(i).getString("id") !in ids) keep.put(old.getJSONObject(i))
        newQs.forEach { keep.put(it.toJson()) }
        root.put("questions", keep)
        val g = root.getJSONObject("groups")
        newGroups.forEach { (k, v) ->
            g.put(k, JSONObject().put("pdf", v.pdf).put("regions", JSONArray().apply { v.regions.forEach { put(it.toJson()) } }))
        }
        importedFile.writeText(root.toString())
        load()
    }

    /** Store questions pulled from the cloud (imported by the admin on another phone). */
    fun saveCloud(root: JSONObject) {
        cloudFile.writeText(root.toString())
        load()
    }

    fun importedSummary(): Map<String, Int> =
        questions.filter { it.addedDay > 0 || it.visual.pdf?.startsWith("file:") == true || it.visual.images.isNotEmpty() }
            .groupingBy { it.source }.eachCount()

    fun topics(section: Section): List<String> =
        questions.filter { it.section == section }.map { it.topic }.distinct().sorted()

    fun years(): List<Int> = questions.map { it.year }.distinct().sorted()
}

fun JSONArray?.toStrings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { getString(it) }
