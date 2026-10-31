package com.catdaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.AppViewModel
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Section

/** The three sections with every question mapped by topic, year and difficulty. */
@Composable
fun PracticeScreen(vm: AppViewModel, open: (String) -> Unit) {
    vm.tick
    var secIdx by rememberSaveable { mutableStateOf(0) }
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var year by rememberSaveable { mutableStateOf<Int?>(null) }
    var diff by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val sec = Section.entries[secIdx]

    val list = remember(secIdx, topic, year, diff, query, vm.tick, vm.bank.questions.size) {
        vm.bank.questions.filter {
            it.section == sec && (topic == null || it.topic == topic) && (year == null || it.year == year) &&
                (diff == null || it.difficulty.name == diff) &&
                (query.isBlank() || it.text.contains(query, true) || it.sub.contains(query, true))
        }.sortedWith(compareBy({ it.year }, { it.slot }, { it.number }))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = secIdx, containerColor = Color.White) {
            Section.entries.forEachIndexed { i, s ->
                Tab(selected = i == secIdx, onClick = { secIdx = i; topic = null },
                    text = { Text(s.title, fontWeight = FontWeight.Bold, color = if (i == secIdx) s.color() else Color.Gray) })
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Dropdown("Topic", topic, vm.bank.topics(sec)) { topic = it }
            Dropdown("Year", year?.toString(), vm.bank.years().map { it.toString() }) { year = it?.toInt() }
            Difficulty.entries.forEach { d ->
                FilterChip(selected = diff == d.name, onClick = { diff = if (diff == d.name) null else d.name },
                    label = { Text(d.name) }, modifier = Modifier.padding(horizontal = 3.dp))
            }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search text or sub-topic") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
        val done = list.count { vm.progress.attempts.containsKey(it.id) }
        Text("${list.size} questions · $done attempted", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

        // Topic overview when no topic is selected
        if (topic == null && query.isBlank()) {
            val byTopic = vm.bank.questions.filter { it.section == sec && (year == null || it.year == year) && (diff == null || it.difficulty.name == diff) }
                .groupBy { it.topic }.toList().sortedByDescending { it.second.size }
            LazyColumn {
                items(byTopic, key = { it.first }) { (t, qs) ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { topic = t },
                        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("${qs.count { vm.progress.attempts.containsKey(it.id) }}/${qs.size}", color = sec.color(), fontWeight = FontWeight.SemiBold)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                Difficulty.entries.forEach { d -> Pill("${d.name} ${qs.count { it.difficulty == d }}", d.color()) }
                            }
                            val subs = qs.map { it.sub }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4)
                            if (subs.isNotEmpty()) Text(subs.joinToString(" · ") { "${it.key} (${it.value})" }, color = Color.Gray, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        } else {
            LazyColumn {
                items(list, key = { it.id }) { q ->
                    val a = vm.progress.attempts[q.id]
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { open(q.id) },
                        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${q.year} · S${q.slot} · Q${q.number}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Pill(q.difficulty.name, q.difficulty.color())
                                if (a != null) Text(when (a.correct) { true -> "  ✓"; false -> "  ✗"; null -> "  •" },
                                    color = when (a.correct) { true -> Color(0xFF2E9D5B); false -> Color(0xFFD64545); null -> Color.Gray }, fontWeight = FontWeight.Bold)
                            }
                            Text(listOf(q.topic, q.sub, q.type).filter { it.isNotBlank() }.joinToString(" · "), color = sec.color(), fontSize = 12.sp)
                            if (q.text.isNotBlank()) Text(q.text, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = Color.DarkGray,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dropdown(label: String, value: String?, options: List<String>, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(if (value == null) "$label ▾" else "$value ✕", maxLines = 1) }
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onPick(null); open = false })
            options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(o); open = false }) }
        }
    }
}
