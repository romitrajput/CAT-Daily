package com.catdaily.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.AppViewModel
import com.catdaily.app.data.Attempt
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Question
import com.catdaily.app.data.Section

private enum class Status(val label: String) { All("All"), New("Not tried"), Done("Attempted"), Wrong("Wrong") }

/**
 * Sections tab: pick VARC / DILR / QA -> overview + quick practice + topic cards -> topic page
 * with filters (difficulty, status, year, sub-topic) and a "Practice these" button that steps
 * through the filtered questions with Next.
 */
@Composable
fun PracticeScreen(vm: AppViewModel, open: (String) -> Unit) {
    val tick = vm.tick
    var secIdx by rememberSaveable { mutableStateOf(0) }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val sec = Section.entries[secIdx]
    val all = vm.bank.questions
    val secQs = remember(secIdx, tick, all.size) { all.filter { it.section == sec } }

    val start: (List<Question>) -> Unit = { list ->
        if (list.isNotEmpty()) {
            vm.practiceQueue = list.map { it.id }
            val first = list.firstOrNull { !vm.progress.attempts.containsKey(it.id) } ?: list.first()
            open(first.id)
        }
    }
    val detail = topic != null || query.isNotBlank()
    BackHandler(enabled = detail) { topic = null; query = "" }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SectionSwitcher(vm, secIdx, tick) { secIdx = it; topic = null; query = "" }
        SearchBar(query, sec) { query = it }
        if (detail) {
            TopicDetail(vm, sec, secQs, topic, query, onBack = { topic = null; query = "" }, open = open, start = start)
        } else {
            SectionHome(vm, sec, secQs, onTopic = { topic = it }, start = start)
        }
    }
}

@Composable
private fun SectionSwitcher(vm: AppViewModel, selected: Int, tick: Int, onPick: (Int) -> Unit) {
    val stats = remember(tick, vm.bank.questions.size) {
        Section.entries.map { s ->
            val qs = vm.bank.questions.filter { it.section == s }
            qs.count { vm.progress.attempts.containsKey(it.id) } to qs.size
        }
    }
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Section.entries.forEachIndexed { i, s ->
            val sel = i == selected
            val (done, total) = stats[i]
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) s.color() else s.color().copy(alpha = 0.08f))
                    .clickable { onPick(i) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(s.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (sel) Color.White else s.color())
                Text("$done / $total done", fontSize = 11.sp, color = if (sel) Color.White.copy(alpha = 0.85f) else Color.Gray)
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, sec: Section, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Search ${sec.title} questions") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear") }
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().background(Color.White).padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
    )
}

// ------------------------------------------------------------------ section home

@Composable
private fun SectionHome(
    vm: AppViewModel, sec: Section, qs: List<Question>,
    onTopic: (String) -> Unit, start: (List<Question>) -> Unit
) {
    val attempts = vm.progress.attempts
    val done = qs.count { attempts.containsKey(it.id) }
    val checked = qs.mapNotNull { attempts[it.id]?.correct }
    val topics = remember(qs) { qs.groupBy { it.topic }.toList().sortedByDescending { it.second.size } }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth().padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(sec.long, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val span = if (qs.isNotEmpty()) " · CAT ${qs.minOf { it.year }}–${qs.maxOf { it.year }}" else ""
                    Text("${qs.size} questions · ${topics.size} topics$span", fontSize = 12.sp, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { if (qs.isEmpty()) 0f else done / qs.size.toFloat() },
                            color = sec.color(), trackColor = sec.color().copy(alpha = 0.12f),
                            modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                        )
                        Text("  $done done", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (checked.isNotEmpty()) {
                        Text(
                            "Accuracy ${100 * checked.count { it } / checked.size}% on ${checked.size} checked",
                            fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    DifficultyBar(qs)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Difficulty.entries.forEach { d -> LegendDot(d, qs.count { it.difficulty == d }) }
                    }
                }
            }
        }

        item { Header("Quick practice", "Random questions you haven't tried yet") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Difficulty.entries.forEach { d ->
                    val left = qs.filter { it.difficulty == d && !attempts.containsKey(it.id) }
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(d.color().copy(alpha = 0.10f))
                            .clickable(enabled = left.isNotEmpty()) { start(left.shuffled()) }
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, tint = d.color())
                        Text(d.name, fontWeight = FontWeight.Bold, color = d.color())
                        Text("${left.size} new", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        item { Header("Topics", "Tap a topic to see and filter its questions") }
        items(topics, key = { it.first }) { (t, tq) -> TopicCard(vm, sec, t, tq) { onTopic(t) } }
    }
}

@Composable
private fun TopicCard(vm: AppViewModel, sec: Section, title: String, qs: List<Question>, onClick: () -> Unit) {
    val done = qs.count { vm.progress.attempts.containsKey(it.id) }
    val subs = qs.map { it.sub }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(sec.color().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text("${qs.size}", fontWeight = FontWeight.Bold, color = sec.color())
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subs.isNotEmpty()) {
                    Text(
                        subs.take(3).joinToString(" · ") { it.key }, fontSize = 12.sp, color = Color.Gray,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                DifficultyBar(qs)
                Text("$done of ${qs.size} attempted", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Color.Gray)
        }
    }
}

// ------------------------------------------------------------------ topic page

@Composable
private fun TopicDetail(
    vm: AppViewModel, sec: Section, secQs: List<Question>, topic: String?, query: String,
    onBack: () -> Unit, open: (String) -> Unit, start: (List<Question>) -> Unit
) {
    val attempts = vm.progress.attempts
    var diff by rememberSaveable(topic) { mutableStateOf<String?>(null) }
    var status by rememberSaveable(topic) { mutableStateOf(Status.All.name) }
    var year by rememberSaveable(topic) { mutableStateOf<Int?>(null) }
    var sub by rememberSaveable(topic) { mutableStateOf<String?>(null) }

    val base = remember(secQs, topic, query) {
        secQs.filter {
            (topic == null || it.topic == topic) &&
                (query.isBlank() || it.text.contains(query, true) || it.sub.contains(query, true) || it.topic.contains(query, true))
        }
    }
    val list = base.filter { q ->
        (diff == null || q.difficulty.name == diff) && (year == null || q.year == year) && (sub == null || q.sub == sub) &&
            when (Status.valueOf(status)) {
                Status.All -> true
                Status.New -> !attempts.containsKey(q.id)
                Status.Done -> attempts.containsKey(q.id)
                Status.Wrong -> attempts[q.id]?.correct == false
            }
    }.sortedWith(compareByDescending<Question> { it.year }.thenBy { it.slot }.thenBy { it.number })
    val years = base.map { it.year }.distinct().sortedDescending()
    val subs = base.map { it.sub }.filter { it.isNotBlank() }.distinct().sorted()

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(
                        topic ?: "Results for \"$query\"", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${list.size} of ${base.size} shown · ${base.count { attempts.containsKey(it.id) }} attempted",
                        fontSize = 12.sp, color = Color.Gray
                    )
                }
            }
        }
        item {
            ChipRow {
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = diff == d.name,
                        onClick = { diff = if (diff == d.name) null else d.name },
                        label = { Text(d.name) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(d.color())) }
                    )
                }
            }
        }
        item {
            ChipRow {
                Status.entries.forEach { s ->
                    FilterChip(selected = status == s.name, onClick = { status = s.name }, label = { Text(s.label) })
                }
            }
        }
        if (years.size > 1) item {
            ChipRow {
                FilterChip(selected = year == null, onClick = { year = null }, label = { Text("All years") })
                years.forEach { y ->
                    FilterChip(selected = year == y, onClick = { year = if (year == y) null else y }, label = { Text("$y") })
                }
            }
        }
        if (subs.size > 1) item {
            ChipRow {
                FilterChip(selected = sub == null, onClick = { sub = null }, label = { Text("All sub-topics") })
                subs.forEach { s ->
                    FilterChip(selected = sub == s, onClick = { sub = if (sub == s) null else s }, label = { Text(s) })
                }
            }
        }
        item {
            Button(
                onClick = { start(list) }, enabled = list.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = sec.color())
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (list.any { !attempts.containsKey(it.id) }) "Practice these ${list.size}" else "Review these ${list.size}")
            }
        }
        if (list.isEmpty()) item {
            Text(
                "No questions match these filters.", color = Color.Gray, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            )
        }
        items(list, key = { it.id }) { q ->
            QuestionRow(q, attempts[q.id], sec) {
                vm.practiceQueue = list.map { it.id }
                open(q.id)
            }
        }
    }
}

@Composable
private fun QuestionRow(q: Question, a: Attempt?, sec: Section, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            val (bg, mark) = when {
                a == null -> Color(0xFFEDEEF5) to "${q.number}"
                a.correct == true -> Color(0xFF2E9D5B) to "✓"
                a.correct == false -> Color(0xFFD64545) to "✗"
                else -> sec.color() to "•"
            }
            Box(Modifier.size(34.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                Text(mark, color = if (a == null) Color.DarkGray else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CAT ${q.year} · Slot ${q.slot}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Pill(q.difficulty.name, q.difficulty.color())
                }
                Text(
                    listOf(q.sub.ifBlank { q.topic }, q.type).joinToString(" · "),
                    fontSize = 12.sp, color = sec.color(), modifier = Modifier.padding(top = 2.dp)
                )
                if (q.text.isNotBlank()) {
                    Text(
                        q.text, fontSize = 13.sp, color = Color.DarkGray, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ small pieces

@Composable
private fun DifficultyBar(qs: List<Question>) {
    Row(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFEDEEF5))) {
        Difficulty.entries.forEach { d ->
            val n = qs.count { it.difficulty == d }
            if (n > 0) Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(d.color()))
        }
    }
}

@Composable
private fun LegendDot(d: Difficulty, n: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(d.color()))
        Text(" ${d.name} $n", fontSize = 12.sp, color = Color.DarkGray)
    }
}

@Composable
private fun Header(title: String, sub: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(sub, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}
