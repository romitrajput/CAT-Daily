package com.catdaily.app.logic

import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Question
import com.catdaily.app.data.Section
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Picks 5 questions per section for a date.
 *
 * Deterministic: every phone with the same question bank gets the SAME 15 questions on the
 * same day, which is what makes the daily leaderboard fair. Questions are not repeated until
 * every question of that section & difficulty has been served once.
 *
 * Mix rules for each section's 5 questions:
 *  - difficulty pattern rotates daily: [E,E,M,M,H] -> [E,M,M,H,H] -> [E,M,M,M,H]
 *  - no two questions from the same year if it can be avoided
 *  - no two questions from the same passage / DILR set
 */
object DailyPicker {
    val EPOCH: LocalDate = LocalDate.of(2026, 11, 1)
    const val PER_SECTION = 5

    private val PATTERNS = listOf(
        listOf(Difficulty.Easy, Difficulty.Easy, Difficulty.Medium, Difficulty.Medium, Difficulty.Hard),
        listOf(Difficulty.Easy, Difficulty.Medium, Difficulty.Medium, Difficulty.Hard, Difficulty.Hard),
        listOf(Difficulty.Easy, Difficulty.Medium, Difficulty.Medium, Difficulty.Medium, Difficulty.Hard),
    )

    fun dayIndex(date: LocalDate): Int = ChronoUnit.DAYS.between(EPOCH, date).toInt().coerceAtLeast(0)

    fun plan(all: List<Question>, date: LocalDate): Map<Section, List<Question>> {
        val target = dayIndex(date)
        return Section.entries.associateWith { sec ->
            val pool = all.filter { it.section == sec }.sortedBy { stableHash(it.id) }
            simulate(pool, target)
        }
    }

    private fun simulate(pool: List<Question>, target: Int): List<Question> {
        if (pool.isEmpty()) return emptyList()
        val used = HashSet<String>()
        var today: List<Question> = emptyList()
        for (day in 0..target) {
            val eligible = pool.filter { it.addedDay <= day }
            val pattern = PATTERNS[day % PATTERNS.size]
            val picks = mutableListOf<Question>()
            for (want in pattern) {
                val q = pick(eligible, used, picks, want) ?: continue
                picks += q; used += q.id
            }
            today = picks
        }
        return today
    }

    private fun pick(eligible: List<Question>, used: MutableSet<String>, picks: List<Question>, want: Difficulty): Question? {
        val years = picks.map { it.year }.toSet()
        val groups = picks.mapNotNull { it.group }.toSet()
        fun ok(q: Question, strictYear: Boolean) =
            q.id !in used && q.id !in picks.map { it.id } &&
                (q.group == null || q.group !in groups) && (!strictYear || q.year !in years)

        val ofDiff = eligible.filter { it.difficulty == want }
        // every question of this difficulty served -> start a new cycle for it
        if (ofDiff.isNotEmpty() && ofDiff.all { it.id in used }) used.removeAll(ofDiff.map { it.id }.toSet())

        return ofDiff.firstOrNull { ok(it, true) }
            ?: ofDiff.firstOrNull { ok(it, false) }
            ?: eligible.firstOrNull { ok(it, true) }
            ?: eligible.firstOrNull { ok(it, false) }
    }

    /** FNV-1a 64-bit: identical on every device, unlike String.hashCode ordering assumptions. */
    fun stableHash(s: String): Long {
        var h = -0x340d631b7bdddcdbL
        for (c in s) { h = h xor c.code.toLong(); h *= 0x100000001b3L }
        return h
    }
}
