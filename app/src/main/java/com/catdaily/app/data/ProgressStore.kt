package com.catdaily.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Local progress: attempts, completed days (for the streak), profile & preferences. */
class ProgressStore(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("cat_daily", Context.MODE_PRIVATE)
    private val file get() = File(ctx.filesDir, "progress.json")

    /** Latest attempt per question id. */
    val attempts = LinkedHashMap<String, Attempt>()
    /** Every attempt (for per-day history). */
    private val history = mutableListOf<Attempt>()
    val completedDays = sortedSetOf<String>()

    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) = prefs.edit().putString("name", v.trim()).apply()

    /** Local identity used when Firebase isn't configured. */
    val localId: String
        get() = prefs.getString("local_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("local_id", it).apply()
        }

    var isAdmin: Boolean
        get() = prefs.getBoolean("admin", false)
        set(v) = prefs.edit().putBoolean("admin", v).apply()

    var reminderHour: Int
        get() = prefs.getInt("rem_h", 8)
        set(v) = prefs.edit().putInt("rem_h", v).apply()
    var reminderMinute: Int
        get() = prefs.getInt("rem_m", 0)
        set(v) = prefs.edit().putInt("rem_m", v).apply()
    var reminderOn: Boolean
        get() = prefs.getBoolean("rem_on", true)
        set(v) = prefs.edit().putBoolean("rem_on", v).apply()

    var streakFreezes: Int
        get() = prefs.getInt("freezes", 0)
        set(v) = prefs.edit().putInt("freezes", v).apply()

    fun load() {
        attempts.clear(); history.clear(); completedDays.clear()
        if (!file.exists()) return
        val o = JSONObject(file.readText())
        val a = o.optJSONArray("history") ?: JSONArray()
        for (i in 0 until a.length()) {
            val at = Attempt.fromJson(a.getJSONObject(i))
            history += at; attempts[at.qid] = at
        }
        val d = o.optJSONArray("completed") ?: JSONArray()
        for (i in 0 until d.length()) completedDays += d.getString(i)
    }

    private fun save() {
        val o = JSONObject()
        o.put("history", JSONArray().apply { history.forEach { put(it.toJson()) } })
        o.put("completed", JSONArray(completedDays.toList()))
        file.writeText(o.toString())
    }

    fun record(a: Attempt) {
        history += a; attempts[a.qid] = a; save()
    }

    fun attemptsOn(date: String): List<Attempt> = history.filter { it.date == date }

    /** Attempt for [qid] made on [date] (today's plan only counts same-day attempts). */
    fun attemptOn(qid: String, date: String): Attempt? = history.lastOrNull { it.qid == qid && it.date == date }

    fun markCompleted(date: String) {
        if (completedDays.add(date)) {
            // earn a streak freeze every 7 completed days (max 2 banked)
            if (completedDays.size % 7 == 0 && streakFreezes < 2) streakFreezes += 1
            save()
        }
    }

    /** Uses a banked freeze to protect yesterday if it was missed. */
    fun useFreeze(today: LocalDate): Boolean {
        val y = today.minusDays(1).toString()
        if (streakFreezes <= 0 || y in completedDays) return false
        if (today.minusDays(2).toString() !in completedDays) return false
        streakFreezes -= 1
        completedDays += y; save()
        return true
    }

    /** Consecutive completed days ending today (or yesterday, if today isn't done yet). */
    fun currentStreak(today: LocalDate): Int {
        var d = if (today.toString() in completedDays) today else today.minusDays(1)
        var n = 0
        while (d.toString() in completedDays) { n++; d = d.minusDays(1) }
        return n
    }

    fun bestStreak(): Int {
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (s in completedDays) {
            val d = LocalDate.parse(s)
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    val totalAttempted get() = attempts.size
    val totalCorrect get() = attempts.values.count { it.correct == true }
    fun totalPoints(bank: QuestionBank) = history.sumOf { it.points(bank.byId[it.qid]) }

    /** Accuracy per topic across all attempts that could be checked. */
    fun topicAccuracy(bank: QuestionBank): Map<String, Pair<Int, Int>> {
        val m = HashMap<String, Pair<Int, Int>>()
        for (a in attempts.values) {
            val q = bank.byId[a.qid] ?: continue
            if (a.correct == null) continue
            val key = "${q.section.title} · ${q.topic}"
            val (c, t) = m[key] ?: (0 to 0)
            m[key] = (c + if (a.correct) 1 else 0) to (t + 1)
        }
        return m
    }
}
