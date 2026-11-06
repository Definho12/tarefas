package com.guilherme.tarefas

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalDateTime

val Bg = Color(0xFF0E0F12)
val Surface1 = Color(0xFF16181D)
val Surface2 = Color(0xFF1E2127)
val Line = Color(0xFF2A2E36)
val Ink = Color(0xFFECEEF2)
val Muted = Color(0xFF8B92A0)
val Accent = Color(0xFF9B8CFF)
val AccentSoft = Color(0xFF2B2745)
val OnAccent = Color(0xFF15121F)
val Danger = Color(0xFFFF6B6B)

private val scheme = darkColorScheme(
    primary = Accent, onPrimary = OnAccent,
    primaryContainer = AccentSoft, onPrimaryContainer = Ink,
    secondary = Accent, onSecondary = OnAccent,
    secondaryContainer = AccentSoft, onSecondaryContainer = Ink,
    tertiary = Accent, onTertiary = OnAccent,
    tertiaryContainer = AccentSoft, onTertiaryContainer = Ink,
    background = Bg, onBackground = Ink,
    surface = Bg, onSurface = Ink,
    surfaceVariant = Surface2, onSurfaceVariant = Muted,
    outline = Line, outlineVariant = Line,
    surfaceContainerLowest = Bg, surfaceContainerLow = Surface1,
    surfaceContainer = Surface1, surfaceContainerHigh = Surface2, surfaceContainerHighest = Surface2,
    error = Danger,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)

// ---------- Textos ----------
val DIAS = arrayOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom")
val MESES = arrayOf("janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro", "outubro", "novembro", "dezembro")
val MESES_CURTOS = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

fun hhmm(h: Int, m: Int) = "%02d:%02d".format(h, m)

fun describe(t: Task): String = when (t.repeat) {
    Repeat.ONCE -> "Uma vez"
    Repeat.DAILY -> "Todo dia"
    Repeat.WEEKLY -> when {
        t.weekdays.size == 7 -> "Todo dia"
        t.weekdays == setOf(1, 2, 3, 4, 5) -> "Dias úteis"
        t.weekdays == setOf(6, 7) -> "Fins de semana"
        else -> t.weekdays.sorted().joinToString(", ") { DIAS[it - 1] }
    }
    Repeat.MONTHLY -> "Todo dia ${t.day} do mês"
    Repeat.YEARLY -> "Todo ano, ${t.day} de ${MESES[t.month - 1]}"
}

fun dayLabel(d: LocalDate, today: LocalDate): String = when {
    d == today -> "hoje"
    d == today.plusDays(1) -> "amanhã"
    d.year == today.year -> "${DIAS[d.dayOfWeek.value - 1].lowercase()}, %02d/%02d".format(d.dayOfMonth, d.monthValue)
    else -> "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
}

fun nextLabel(dt: LocalDateTime, now: LocalDateTime) =
    "${dayLabel(dt.toLocalDate(), now.toLocalDate())} às ${hhmm(dt.hour, dt.minute)}"

fun longDate(d: LocalDate) =
    "${DIAS[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} de ${MESES[d.monthValue - 1]} de ${d.year}"

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
