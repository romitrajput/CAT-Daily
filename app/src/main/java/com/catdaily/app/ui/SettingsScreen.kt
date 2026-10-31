package com.catdaily.app.ui

import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.AppViewModel
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Section

@Composable
fun SettingsScreen(vm: AppViewModel) {
    vm.tick
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(12.dp)) {

        Block("Profile") {
            var name by remember { mutableStateOf(vm.progress.name) }
            OutlinedTextField(name, { name = it.take(24) }, label = { Text("Name on leaderboard") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.setName(name) }, modifier = Modifier.padding(top = 8.dp)) { Text("Save name") }
        }

        Block("Daily reminder") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notify me when today's 15 are ready", Modifier.weight(1f))
                Switch(vm.progress.reminderOn, { vm.setReminder(it, vm.progress.reminderHour, vm.progress.reminderMinute) })
            }
            TextButton(onClick = {
                TimePickerDialog(ctx, { _, h, m -> vm.setReminder(true, h, m) }, vm.progress.reminderHour, vm.progress.reminderMinute, false).show()
            }) { Text("Time: %02d:%02d".format(vm.progress.reminderHour, vm.progress.reminderMinute)) }
        }

        Block("Your stats") {
            Text("Streak ${vm.streak} · Best ${vm.best} · Days completed ${vm.progress.completedDays.size}")
            Text("Attempted ${vm.progress.totalAttempted} · Correct ${vm.progress.totalCorrect} · Points ${vm.progress.totalPoints(vm.bank)}")
            val acc = vm.progress.topicAccuracy(vm.bank).filter { it.value.second >= 3 }
                .entries.sortedBy { it.value.first.toFloat() / it.value.second }.take(5)
            if (acc.isNotEmpty()) {
                Text("Weakest topics", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                acc.forEach { (t, p) -> Text("• $t — ${p.first}/${p.second} (${100 * p.first / p.second}%)", fontSize = 13.sp) }
            }
        }

        Block("Question bank") {
            Section.entries.forEach { s ->
                val qs = vm.bank.questions.filter { it.section == s }
                Text("${s.title}: ${qs.size} questions — " + Difficulty.entries.joinToString(" · ") { d -> "${d.name} ${qs.count { it.difficulty == d }}" }, fontSize = 13.sp)
            }
            Text("Years: ${vm.bank.years().joinToString(", ")}", fontSize = 13.sp, color = Color.Gray)
            Text("Answer key loaded for ${vm.bank.answerCount} questions", fontSize = 13.sp, color = Color.Gray)
            if (vm.cloud.enabled) OutlinedButton(onClick = { vm.syncFromCloud() }, modifier = Modifier.padding(top = 6.dp)) { Text("Sync from cloud") }
        }

        AdminBlock(vm)
        Spacer(Modifier.height(30.dp))
    }

    vm.importResult?.let { r ->
        var publish by remember { mutableStateOf(vm.cloud.enabled) }
        AlertDialog(
            onDismissRequest = { vm.importResult = null },
            title = { Text("Import preview") },
            text = {
                Column {
                    Text("Year: ${r.year ?: "?"} · ${r.questions.size} questions", fontWeight = FontWeight.SemiBold)
                    Section.entries.forEach { s ->
                        val qs = r.questions.filter { it.section == s }
                        Text("${s.title}: ${qs.size}  (E ${qs.count { it.difficulty == Difficulty.Easy }} · M ${qs.count { it.difficulty == Difficulty.Medium }} · H ${qs.count { it.difficulty == Difficulty.Hard }})")
                    }
                    val topics = r.questions.groupingBy { "${it.section.title} · ${it.topic}" }.eachCount().entries.sortedByDescending { it.value }.take(6)
                    Text(topics.joinToString("\n") { "${it.key}: ${it.value}" }, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                    r.warnings.forEach { Text("⚠ $it", fontSize = 12.sp, color = Color(0xFFB26A00)) }
                    if (vm.cloud.enabled) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(publish, { publish = it }); Text("Publish to all users", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport(publish) }, enabled = r.questions.isNotEmpty()) { Text("Add to app") } },
            dismissButton = { TextButton(onClick = { vm.importResult = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminBlock(vm: AppViewModel) {
    Block("Admin (main user)") {
        if (!vm.progress.isAdmin) {
            var pin by remember { mutableStateOf("") }
            var err by remember { mutableStateOf(false) }
            Text("Unlock to add new question-paper PDFs and answer keys.", fontSize = 13.sp, color = Color.Gray)
            OutlinedTextField(pin, { pin = it; err = false }, label = { Text("Admin PIN") }, singleLine = true, isError = err,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth())
            Button(onClick = { err = !vm.unlockAdmin(pin) }, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
            return@Block
        }
        var year by remember { mutableStateOf("") }
        val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.parsePdf(uri, year.toIntOrNull())
        }
        val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importAnswerKey(uri)
        }
        Text("Add a question paper", fontWeight = FontWeight.SemiBold)
        Text("The PDF is split into VARC / DILR / QA, each question is tagged by topic, year and Easy/Medium/Hard, and it joins the daily mix automatically. Works with CAT-style papers that use SECTION headings and 'Question No.' or '1.' numbering.",
            fontSize = 12.sp, color = Color.Gray)
        OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year (optional — auto-detected)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Button(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }, enabled = !vm.importing, modifier = Modifier.padding(top = 8.dp)) {
            Text(if (vm.importing) "Reading PDF…" else "Choose PDF")
        }
        if (vm.importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
        vm.publishProgress?.let { Text(it, fontSize = 12.sp, color = Color.Gray) }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Text("Answer key (CSV)", fontWeight = FontWeight.SemiBold)
        Text("One answer per line: id,answer  — or  year,slot,section,number,answer  (e.g. 2024,1,QA,47,B). Several accepted answers: 12|12.0",
            fontSize = 12.sp, color = Color.Gray)
        OutlinedButton(onClick = { csvPicker.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel")) },
            modifier = Modifier.padding(top = 6.dp)) { Text("Import answer key") }

        val imported = vm.bank.importedSummary()
        if (imported.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Added papers", fontWeight = FontWeight.SemiBold)
            imported.forEach { (k, v) -> Text("• $k — $v questions", fontSize = 13.sp) }
        }
        TextButton(onClick = { vm.lockAdmin() }) { Text("Lock admin tools") }
    }
}

@Composable
private fun Block(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}
