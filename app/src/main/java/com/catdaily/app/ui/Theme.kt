package com.catdaily.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catdaily.app.data.Difficulty
import com.catdaily.app.data.Section

val Ink = Color(0xFF1B1F3B)
val Flame = Color(0xFFFF8A3D)
val Gold = Color(0xFFFFD166)
val Paper = Color(0xFFF7F7FB)

fun Section.color() = when (this) {
    Section.VARC -> Color(0xFF5B6CFF)
    Section.DILR -> Color(0xFF12A88A)
    Section.QA -> Color(0xFFE85D75)
}

fun Difficulty.color() = when (this) {
    Difficulty.Easy -> Color(0xFF2E9D5B)
    Difficulty.Medium -> Color(0xFFE0A100)
    Difficulty.Hard -> Color(0xFFD64545)
}

@Composable
fun CatTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink, onPrimary = Color.White,
            secondary = Flame, onSecondary = Color.White,
            background = Paper, surface = Color.White,
            surfaceVariant = Color(0xFFEDEEF5),
        ),
        content = content
    )
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
