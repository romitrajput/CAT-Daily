package com.catdaily.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class Section(val title: String, val long: String) {
    VARC("VARC", "Verbal Ability & Reading Comprehension"),
    DILR("DILR", "Data Interpretation & Logical Reasoning"),
    QA("QA", "Quantitative Ability");
}

enum class Difficulty { Easy, Medium, Hard }

/** A horizontal strip of a PDF page, in PDF points (72 dpi, origin top-left). */
data class Region(val page: Int, val y0: Float, val y1: Float) {
    fun toJson() = JSONArray().put(page).put(y0.toDouble()).put(y1.toDouble())

    companion object {
        fun fromJson(a: JSONArray) = Region(a.getInt(0), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())
    }
}

/**
 * Where to draw a question/passage from: either crop regions of a PDF
 * (bundled asset or locally imported file) or pre-rendered images synced from the cloud.
 */
data class Visual(
    val pdf: String?,            // "pdfs/cat_2019.pdf" (asset) or "file:/abs/path.pdf"
    val regions: List<Region>,
    val images: List<String> = emptyList() // base64 WEBP strips, used when the PDF isn't on this device
)

data class Question(
    val id: String,
    val year: Int,
    val slot: Int,
    val section: Section,
    val number: Int,
    val type: String,            // "MCQ" or "TITA"
    val labels: List<String>,    // option labels as printed (A-D or 1-4)
    var topic: String,
    var sub: String,
    var difficulty: Difficulty,
    val score: Double,
    val group: String?,
    val visual: Visual,
    val text: String,
    val source: String,
    val addedDay: Int
) {
    val isMcq get() = type == "MCQ"
    val yearSlot get() = "$year · Slot $slot"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("year", year); put("slot", slot); put("section", section.name)
        put("number", number); put("type", type); put("labels", JSONArray(labels))
        put("topic", topic); put("sub", sub); put("difficulty", difficulty.name)
        put("score", score); put("group", group ?: JSONObject.NULL)
        put("pdf", visual.pdf ?: JSONObject.NULL)
        put("regions", JSONArray().apply { visual.regions.forEach { put(it.toJson()) } })
        put("text", text); put("source", source); put("addedDay", addedDay)
    }

    companion object {
        fun fromJson(o: JSONObject, images: List<String> = emptyList()): Question {
            val regs = o.optJSONArray("regions") ?: JSONArray()
            val labels = o.optJSONArray("labels") ?: JSONArray()
            return Question(
                id = o.getString("id"),
                year = o.getInt("year"),
                slot = o.optInt("slot", 1),
                section = Section.valueOf(o.getString("section")),
                number = o.optInt("number", 0),
                type = o.optString("type", "MCQ"),
                labels = (0 until labels.length()).map { labels.getString(it) },
                topic = o.optString("topic"),
                sub = o.optString("sub"),
                difficulty = Difficulty.valueOf(o.optString("difficulty", "Medium")),
                score = o.optDouble("score", 0.0),
                group = if (o.isNull("group")) null else o.optString("group"),
                visual = Visual(
                    if (o.isNull("pdf")) null else o.optString("pdf"),
                    (0 until regs.length()).map { Region.fromJson(regs.getJSONArray(it)) },
                    images
                ),
                text = o.optString("text"),
                source = o.optString("source", "CAT ${o.getInt("year")}"),
                addedDay = o.optInt("addedDay", 0)
            )
        }
    }
}

data class Attempt(
    val qid: String,
    val answer: String,
    val correct: Boolean?,      // null when no answer key is available yet
    val seconds: Int,
    val date: String            // yyyy-MM-dd
) {
    fun toJson() = JSONObject().put("qid", qid).put("answer", answer)
        .put("correct", correct ?: JSONObject.NULL).put("seconds", seconds).put("date", date)

    companion object {
        fun fromJson(o: JSONObject) = Attempt(
            o.getString("qid"), o.getString("answer"),
            if (o.isNull("correct")) null else o.getBoolean("correct"),
            o.optInt("seconds"), o.getString("date")
        )
    }
}

/** CAT marking: +3 correct, -1 wrong MCQ, 0 wrong TITA. Without a key, an attempt earns 1 point. */
fun Attempt.points(q: Question?): Int = when (correct) {
    true -> 3
    false -> if (q?.isMcq == true) -1 else 0
    null -> 1
}

data class LeaderRow(
    val uid: String,
    val name: String,
    val value: Int,
    val detail: String,
    val isMe: Boolean
)
