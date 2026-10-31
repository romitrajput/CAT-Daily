package com.catdaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.AppViewModel

@Composable
fun LeaderboardScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab, vm.tick) { vm.loadBoard(tab) }
    val titles = listOf("Today", "Streak", "All-time")
    val unit = listOf("pts", "days", "pts")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = tab, containerColor = Color.White) {
            titles.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontWeight = FontWeight.SemiBold) }) }
        }
        Text(
            when (tab) {
                0 -> "Everyone gets the same 15 questions today. Ranked by score (+3 / −1 MCQ / 0 TITA), then completion, then time."
                1 -> "Consecutive days with all 15 questions solved."
                else -> "Total points across all days."
            }, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(12.dp)
        )
        if (!vm.cloud.enabled) {
            Card(Modifier.fillMaxWidth().padding(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5))) {
                Text("Offline mode: add google-services.json (see README) to rank against other users. Showing only you.",
                    Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
        if (vm.boardLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn {
            itemsIndexed(vm.board, key = { _, r -> r.uid }) { i, r ->
                val medal = when (i) { 0 -> Gold; 1 -> Color(0xFFC0C6D0); 2 -> Color(0xFFD9A066); else -> Color(0xFFEDEEF5) }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = if (r.isMe) Color(0xFFEFF1FF) else Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(CircleShape).background(medal), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(r.name + if (r.isMe) " (you)" else "", fontWeight = FontWeight.SemiBold)
                            Text(r.detail, fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("${r.value} ${unit[tab]}", fontWeight = FontWeight.Bold, color = Ink)
                    }
                }
            }
        }
    }
}
