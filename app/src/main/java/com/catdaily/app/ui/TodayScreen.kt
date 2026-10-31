package com.catdaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.AppViewModel
import com.catdaily.app.data.Question
import com.catdaily.app.data.Section
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(vm: AppViewModel, open: (String) -> Unit) {
    vm.tick // recompose on progress changes
    LazyColumn(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        item { StreakHeader(vm) }
        Section.entries.forEach { sec ->
            val qs = vm.plan[sec].orEmpty()
            item {
                val done = qs.count { vm.doneToday(it) != null }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(sec.color()))
                    Spacer(Modifier.width(8.dp))
                    Text(sec.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(sec.long, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("$done/${qs.size}", fontWeight = FontWeight.SemiBold, color = sec.color())
                }
            }
            qs.forEachIndexed { i, q -> item(key = "d-${q.id}") { DailyRow(vm, i + 1, q) { open(q.id) } } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StreakHeader(vm: AppViewModel) {
    val done = vm.doneCount
    Column(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Ink, Color(0xFF2D3266))))
            .padding(20.dp)
    ) {
        Text(vm.today.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), color = Color.White.copy(alpha = .7f), fontSize = 13.sp)
        Text("Hi ${vm.displayName()}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, null, tint = if (vm.streak > 0) Flame else Color.Gray, modifier = Modifier.size(44.dp))
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text("${vm.streak}-day streak", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Best ${vm.best} · ${vm.progress.streakFreezes} freeze${if (vm.progress.streakFreezes == 1) "" else "s"} banked",
                    color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        // streak bar: last 7 days
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            vm.last7().forEach { (d, ok) ->
                val isToday = d == vm.today
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (ok) Flame else Color.White.copy(alpha = .10f))
                            .border(if (isToday) 2.dp else 0.dp, if (isToday) Gold else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (ok) Icon(Icons.Filled.LocalFireDepartment, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        else Text("${d.dayOfMonth}", color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                    }
                    Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { done / 15f }, color = Gold, trackColor = Color.White.copy(alpha = .15f),
                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Text("  $done/15", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Text(
            if (vm.isTodayComplete) "Done for today 🎉 Come back tomorrow." else "Solve all 15 today to grow your streak.",
            color = Color.White.copy(alpha = .75f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun DailyRow(vm: AppViewModel, n: Int, q: Question, onClick: () -> Unit) {
    val a = vm.doneToday(q)
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(q.section.color().copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Text("$n", color = q.section.color(), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (q.sub.isNotBlank() && q.section == Section.QA) q.sub else q.topic, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Pill(q.difficulty.name, q.difficulty.color())
                    Pill(q.yearSlot, Color.Gray)
                    Pill(q.type, Color.Gray)
                }
            }
            when {
                a == null -> {}
                a.correct == true -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E9D5B))
                a.correct == false -> Text("✗", color = Color(0xFFD64545), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                else -> Icon(Icons.Filled.CheckCircle, null, tint = Color.Gray)
            }
        }
    }
}
