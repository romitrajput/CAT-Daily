package com.catdaily.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.AppViewModel
import com.catdaily.app.data.Attempt
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Visual
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionScreen(vm: AppViewModel, id: String, daily: Boolean, onBack: () -> Unit, onNext: (String) -> Unit) {
    val q = vm.bank.byId[id] ?: run { Text("Question not found"); return }
    vm.tick
    val existing = if (daily) vm.doneToday(q) else null
    var result by remember(id) { mutableStateOf<Attempt?>(existing) }
    var choice by remember(id) { mutableStateOf(existing?.answer ?: "") }
    var seconds by remember(id) { mutableIntStateOf(0) }
    var zoom by remember { mutableStateOf(1f) }
    var showCtx by remember(id) { mutableStateOf(true) }
    var editing by remember { mutableStateOf(false) }

    LaunchedEffect(id, result) { while (result == null) { delay(1000); seconds++ } }

    val nextId = if (daily) vm.planList.firstOrNull { it.id != id && vm.doneToday(it) == null }?.id
    else vm.practiceQueue.let { l -> val i = l.indexOf(id); if (i >= 0 && i + 1 < l.size) l[i + 1] else null }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = {
                Column {
                    Text("${q.section.title} · ${q.topic}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("${q.source} · Q${q.number}" + if (result == null) "  ⏱ ${seconds / 60}:${"%02d".format(seconds % 60)}" else "",
                        fontSize = 12.sp, color = Color.Gray)
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }) { Icon(Icons.Filled.ZoomOut, "Zoom out") }
                IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(2.5f) }) { Icon(Icons.Filled.ZoomIn, "Zoom in") }
                if (vm.progress.isAdmin) IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, "Edit tags") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(q.difficulty.name, q.difficulty.color())
                Pill(q.type, Color.Gray)
                if (q.sub.isNotBlank()) Pill(q.sub, q.section.color())
            }
            vm.bank.groupVisual(q)?.let { gv ->
                Text(if (showCtx) "▾ Passage / set (shared by ${vm.bank.sameGroup(q).size} questions)" else "▸ Show passage / set",
                    color = q.section.color(), fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { showCtx = !showCtx }.padding(horizontal = 12.dp, vertical = 4.dp))
                if (showCtx) Strips(vm, gv, zoom)
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(2.dp).background(q.section.color().copy(alpha = .3f)))
            }
            Strips(vm, q.visual, zoom)
            Spacer(Modifier.height(12.dp))
        }

        // ------------------------------------------------ answer panel
        Column(Modifier.fillMaxWidth().background(Color.White).padding(12.dp)) {
            if (q.isMcq) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    q.labels.forEach { l ->
                        val key = vm.bank.answerFor(q.id)
                        val isKey = result != null && key?.split("|")?.any { it.trim().equals(l, true) } == true
                        val picked = choice == l
                        val bg = when {
                            isKey -> Color(0xFF2E9D5B)
                            result != null && picked && result?.correct == false -> Color(0xFFD64545)
                            picked -> q.section.color()
                            else -> Color(0xFFEDEEF5)
                        }
                        Button(onClick = { if (result == null) choice = l }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = if (picked || isKey) Color.White else Color.Black),
                            shape = RoundedCornerShape(12.dp)) { Text(l, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    }
                }
            } else {
                OutlinedTextField(choice, { if (result == null) choice = it.take(20) }, enabled = result == null,
                    label = { Text("Type your answer (TITA)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            val r = result
            if (r == null) {
                Button(onClick = { result = vm.submit(q, choice, seconds) }, enabled = choice.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Text("Submit") }
            } else {
                Text(
                    when (r.correct) {
                        true -> "✓ Correct  (+3)"
                        false -> "✗ Not quite — answer: ${vm.bank.answerFor(q.id)}" + if (q.isMcq) "  (−1)" else "  (0)"
                        null -> "Saved: ${r.answer}. Answer key for this paper isn't loaded yet."
                    },
                    color = when (r.correct) { true -> Color(0xFF2E9D5B); false -> Color(0xFFD64545); null -> Color.DarkGray },
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!daily) OutlinedButton(onClick = { result = null; choice = ""; seconds = 0 }) { Text("Try again") }
                    if (nextId != null) Button(onClick = { onNext(nextId) }, modifier = Modifier.weight(1f)) { Text("Next question →") }
                    else Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text(if (daily) "Back to today" else "Done") }
                }
            }
        }
    }

    if (editing) EditTagsDialog(vm, q.topic, q.sub, q.difficulty, onDismiss = { editing = false }) { t, s, d ->
        vm.editTags(q, t, s, d); editing = false
    }
}

@Composable
private fun Strips(vm: AppViewModel, v: Visual, zoom: Float) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val baseDp = cfg.screenWidthDp
    val px = with(density) { (baseDp * zoom).dp.roundToPx() }
    val bitmaps by produceState<List<Bitmap>?>(null, v, px) {
        value = runCatching { withContext(Dispatchers.IO) { vm.renderer.strips(v, px.coerceAtMost(2400)) } }.getOrElse { emptyList() }
    }
    val bm = bitmaps
    if (bm == null) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (bm.isEmpty()) { Text("Couldn't load this question's page.", Modifier.padding(12.dp), color = Color.Gray); return }
    val scroll = rememberScrollState()
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = zoom > 1f)) {
        Column(Modifier.width((baseDp * zoom).dp).background(Color.White)) {
            bm.forEach { b ->
                Image(b.asImageBitmap(), null, contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun EditTagsDialog(vm: AppViewModel, topic0: String, sub0: String, d0: Difficulty, onDismiss: () -> Unit, onSave: (String, String, Difficulty) -> Unit) {
    var topic by remember { mutableStateOf(topic0) }
    var sub by remember { mutableStateOf(sub0) }
    var d by remember { mutableStateOf(d0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags (admin)") },
        text = {
            Column {
                OutlinedTextField(topic, { topic = it }, label = { Text("Topic") }, singleLine = true)
                OutlinedTextField(sub, { sub = it }, label = { Text("Sub-topic") }, singleLine = true)
                Row(Modifier.padding(top = 8.dp)) {
                    Difficulty.entries.forEach { x ->
                        FilterChip(selected = d == x, onClick = { d = x }, label = { Text(x.name) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                Text("Edits sync to every user when cloud is on.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(topic.trim(), sub.trim(), d) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
