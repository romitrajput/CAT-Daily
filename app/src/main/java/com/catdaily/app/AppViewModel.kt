package com.catdaily.app

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catdaily.app.data.Attempt
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.LeaderRow
import com.catdaily.app.data.Question
import com.catdaily.app.data.Section
import com.catdaily.app.data.points
import com.catdaily.app.importer.PdfImporter
import com.catdaily.app.logic.Classifier
import com.catdaily.app.logic.DailyPicker
import com.catdaily.app.notify.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ca = app as CatApp
    val bank get() = ca.bank
    val progress get() = ca.progress
    val renderer get() = ca.renderer
    val cloud get() = ca.cloud

    /** Bumped whenever progress/bank changes so Compose re-reads derived values. */
    var tick by mutableIntStateOf(0); private set
    var today by mutableStateOf(LocalDate.now()); private set
    var plan by mutableStateOf<Map<Section, List<Question>>>(emptyMap()); private set
    var message by mutableStateOf<String?>(null)

    init {
        progress.useFreeze(today)
        refreshPlan()
        Reminder.schedule(app, progress.reminderHour, progress.reminderMinute, progress.reminderOn)
        syncFromCloud()
    }

    fun onResume() {
        val now = LocalDate.now()
        if (now != today) { today = now; progress.useFreeze(now); refreshPlan() }
    }

    /** Today's set is frozen once computed so later bank edits can't swap questions mid-day. */
    fun refreshPlan() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("plans", 0)
            val key = "plan_$todayKey"
            val cached = prefs.getString(key, null)?.split(",")?.mapNotNull { bank.byId[it] }
            plan = if (cached != null && cached.size >= 15) cached.groupBy { it.section }
            else withContext(Dispatchers.Default) { DailyPicker.plan(bank.questions, today) }.also { p ->
                val ids = Section.entries.flatMap { p[it].orEmpty() }.map { it.id }
                if (ids.size >= 15) prefs.edit().clear().putString(key, ids.joinToString(",")).apply()
            }
            tick++
        }
    }

    val todayKey get() = today.toString()
    val planList get() = Section.entries.flatMap { plan[it].orEmpty() }
    fun doneToday(q: Question) = progress.attemptOn(q.id, todayKey)
    val doneCount get() = planList.count { doneToday(it) != null }
    val isTodayComplete get() = todayKey in progress.completedDays
    val streak get() = progress.currentStreak(today)
    val best get() = progress.bestStreak()

    fun last7(): List<Pair<LocalDate, Boolean>> =
        (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (it.toString() in progress.completedDays) }

    // ---------------------------------------------------------------- answering
    fun submit(q: Question, answer: String, seconds: Int): Attempt {
        val a = Attempt(q.id, answer.trim(), bank.check(q, answer), seconds, todayKey)
        progress.record(a)
        val planIds = planList.map { it.id }.toSet()
        val todays = progress.attemptsOn(todayKey).filter { it.qid in planIds }.associateBy { it.qid }
        var justCompleted = false
        if (planIds.isNotEmpty() && todays.keys.containsAll(planIds) && !isTodayComplete) {
            progress.markCompleted(todayKey); justCompleted = true
        }
        tick++
        if (q.id in planIds) pushToday()
        if (justCompleted) message = "🔥 All 15 done! Streak: $streak day${if (streak == 1) "" else "s"}"
        return a
    }

    private fun pushToday() {
        if (!cloud.enabled) return
        val planIds = planList.map { it.id }.toSet()
        val todays = progress.attemptsOn(todayKey).filter { it.qid in planIds }.associateBy { it.qid }.values
        viewModelScope.launch {
            runCatching {
                cloud.pushProgress(
                    todayKey, displayName(), todays.size, todays.count { it.correct == true },
                    todays.sumOf { it.points(bank.byId[it.qid]) }, isTodayComplete, todays.sumOf { it.seconds },
                    streak, best, progress.completedDays.size, progress.totalAttempted, progress.totalCorrect,
                    progress.totalPoints(bank)
                )
            }.onFailure { message = "Couldn't update leaderboard: ${it.message}" }
        }
    }

    fun displayName() = progress.name.ifBlank { "Aspirant-" + progress.localId.take(4) }

    // ---------------------------------------------------------------- leaderboard
    var board by mutableStateOf<List<LeaderRow>>(emptyList()); private set
    var boardLoading by mutableStateOf(false); private set

    fun loadBoard(tab: Int) {
        if (!cloud.enabled) {
            val pts = progress.attemptsOn(todayKey).sumOf { it.points(bank.byId[it.qid]) }
            board = listOf(
                when (tab) {
                    0 -> LeaderRow("me", displayName(), pts, "$doneCount/15 today", true)
                    1 -> LeaderRow("me", displayName(), streak, "best $best", true)
                    else -> LeaderRow("me", displayName(), progress.totalPoints(bank), "${progress.totalAttempted} attempted", true)
                }
            )
            return
        }
        boardLoading = true
        viewModelScope.launch {
            board = runCatching {
                when (tab) {
                    0 -> cloud.dailyBoard(todayKey)
                    1 -> cloud.usersBoard("streak")
                    else -> cloud.usersBoard("points")
                }
            }.getOrElse { message = "Leaderboard error: ${it.message}"; emptyList() }
            boardLoading = false
        }
    }

    // ---------------------------------------------------------------- settings
    fun setName(n: String) { progress.name = n; tick++; pushToday() }

    fun setReminder(on: Boolean, h: Int, m: Int) {
        progress.reminderOn = on; progress.reminderHour = h; progress.reminderMinute = m
        Reminder.schedule(getApplication<Application>(), h, m, on); tick++
    }

    fun unlockAdmin(pin: String): Boolean {
        if (pin != BuildConfig.ADMIN_PIN) return false
        progress.isAdmin = true; tick++
        if (cloud.enabled) viewModelScope.launch {
            val owner = runCatching { cloud.claimOwner() }.getOrDefault(false)
            if (!owner) message = "Admin unlocked on this phone, but another device already owns the cloud bank — cloud publishing will be refused."
        }
        return true
    }
    fun lockAdmin() { progress.isAdmin = false; tick++ }

    // ---------------------------------------------------------------- admin: PDF import
    var importing by mutableStateOf(false); private set
    var importResult by mutableStateOf<PdfImporter.Result?>(null)
    var publishProgress by mutableStateOf<String?>(null); private set

    fun parsePdf(uri: Uri, year: Int?) {
        importing = true
        viewModelScope.launch {
            importResult = runCatching {
                withContext(Dispatchers.IO) {
                    val cls = Classifier(getApplication<Application>(), bank.cuts)
                    PdfImporter(getApplication<Application>(), cls).import(uri, year, DailyPicker.dayIndex(today))
                }
            }.getOrElse { message = "Import failed: ${it.message}"; null }
            importing = false
        }
    }

    fun confirmImport(publish: Boolean) {
        val r = importResult ?: return
        importResult = null
        bank.addImported(r.questions, r.groups)
        refreshPlan()
        message = "Added ${r.questions.size} questions from CAT ${r.year ?: ""}. They join the daily mix from today."
        if (publish && cloud.enabled) viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    cloud.publishImported(r.questions, r.groups, renderer) { d, t -> publishProgress = "Uploading $d / $t" }
                }
            }.onFailure { message = "Upload failed: ${it.message}" }
            publishProgress = null
        }
    }

    /** CSV lines:  id,answer   |   year,slot,number,answer   |   year,slot,section,number,answer */
    fun importAnswerKey(uri: Uri) {
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.bufferedReader().readText()
                parseAnswerCsv(text)
            }
            bank.mergeAnswers(map)
            // re-grade earlier attempts that had no key
            tick++
            message = "Answer key: ${map.size} answers loaded."
            if (cloud.enabled) runCatching { cloud.publishAnswers(map) }
        }
    }

    private fun parseAnswerCsv(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (raw in text.lines()) {
            val c = raw.split(',').map { it.trim().trim('"') }
            if (c.size < 2 || c[0].isEmpty() || c.last().isEmpty() || c[0].equals("id", true) || c[0].equals("year", true)) continue
            when (c.size) {
                2 -> if (bank.byId.containsKey(c[0])) out[c[0]] = c[1]
                4 -> {
                    val y = c[0].toIntOrNull(); val s = c[1].toIntOrNull(); val n = c[2].toIntOrNull()
                    bank.questions.firstOrNull { it.year == y && it.slot == s && it.number == n }?.let { out[it.id] = c[3] }
                }
                5 -> {
                    val y = c[0].toIntOrNull(); val s = c[1].toIntOrNull(); val n = c[3].toIntOrNull()
                    bank.questions.firstOrNull { it.year == y && it.slot == s && it.section.name.equals(c[2], true) && it.number == n }
                        ?.let { out[it.id] = c[4] }
                }
            }
        }
        return out
    }

    fun editTags(q: Question, topic: String, sub: String, d: Difficulty) {
        bank.overrideTags(q, topic, sub, d); tick++
        if (cloud.enabled) viewModelScope.launch { runCatching { cloud.publishOverrides(bank.overridesJson()) } }
    }

    // ---------------------------------------------------------------- cloud sync
    fun syncFromCloud() {
        if (!cloud.enabled) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    cloud.signIn()
                    cloud.pullBank()?.let { bank.saveCloud(it) }
                    cloud.pullAnswers().takeIf { it.isNotEmpty() }?.let { bank.mergeAnswers(it) }
                    cloud.pullOverrides()?.let { bank.applyOverrides(it) }
                }
                refreshPlan()
            }.onFailure { message = "Offline — using questions on this phone." }
        }
    }
}
