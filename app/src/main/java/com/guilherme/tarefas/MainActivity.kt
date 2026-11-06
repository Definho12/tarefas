package com.guilherme.tarefas

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)
    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        Notifier.ensureChannel(this)
        Scheduler.rescheduleAll(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { AppTheme { TarefasApp(tick.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

// ---------- Avisos de permissão ----------
class Warning(val text: String, val action: String, val open: (Context) -> Unit)

object Warnings {
    fun list(ctx: Context): List<Warning> {
        val pkg = ctx.packageName
        val out = mutableListOf<Warning>()
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) {
            out += Warning("As notificações estão desligadas. Sem elas, nenhum aviso aparece.", "Ativar") { c ->
                go(c, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg))
            }
        }
        if (!Scheduler.canExact(ctx) && Build.VERSION.SDK_INT >= 31) {
            out += Warning("Permita alarmes para o aviso chegar na hora exata.", "Permitir") { c ->
                go(c, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$pkg")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !nm.canUseFullScreenIntent()) {
            out += Warning("Permita que o aviso apareça na tela inteira com o celular bloqueado.", "Permitir") { c ->
                go(c, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")))
            }
        }
        val pm = ctx.getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(pkg)) {
            out += Warning("Libere a bateria para o Android não atrasar os avisos.", "Liberar") { c ->
                go(c, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
            }
        }
        return out
    }

    private fun go(c: Context, i: Intent) {
        try {
            c.startActivity(i)
        } catch (e: Exception) {
            runCatching { c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))) }
        }
    }
}

// ---------- App ----------
fun newTask(): Task {
    val n = LocalDateTime.now().plusHours(1)
    return Task(repeat = Repeat.ONCE, hour = n.hour, minute = 0, date = n.toLocalDate(), day = n.dayOfMonth, month = n.monthValue)
}

private fun rank(t: Task, now: LocalDateTime) = when {
    !t.active -> 2
    Recurrence.next(t, now) == null -> 0
    else -> 1
}

fun sortTasks(list: List<Task>, now: LocalDateTime): List<Task> = list.sortedWith(
    compareBy<Task>(
        { rank(it, now) },
        { Recurrence.next(it, now)?.let(Scheduler::toMillis) ?: Long.MAX_VALUE },
        { it.title.lowercase() },
    )
)

@Composable
fun TarefasApp(tick: Int) {
    val ctx = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.all(ctx)) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var editing by remember { mutableStateOf<Task?>(null) }
    LaunchedEffect(tick) {
        tasks = TaskStore.all(ctx)
        now = LocalDateTime.now()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    val warnings = remember(tick) { Warnings.list(ctx) }

    fun save(t: Task) {
        val s = TaskStore.upsert(ctx, t)
        Scheduler.schedule(ctx, s)
        tasks = TaskStore.all(ctx)
        now = LocalDateTime.now()
    }

    val e = editing
    if (e != null) {
        BackHandler { editing = null }
        EditorScreen(
            initial = e,
            onSave = { save(it); editing = null },
            onDelete = {
                TaskStore.delete(ctx, it.id)
                Scheduler.cancel(ctx, it.id)
                Notifier.cancel(ctx, it.id)
                tasks = TaskStore.all(ctx)
                editing = null
            },
            onCancel = { editing = null },
        )
    } else {
        ListScreen(
            tasks = tasks, now = now, warnings = warnings,
            onAdd = { editing = newTask() },
            onEdit = { editing = it },
            onToggle = { save(it) },
            onTest = {
                Scheduler.test(ctx)
                toast(ctx, "O aviso de teste chega em 5 segundos. Pode bloquear a tela.")
            },
        )
    }
}

@Composable
fun ListScreen(
    tasks: List<Task>, now: LocalDateTime, warnings: List<Warning>,
    onAdd: () -> Unit, onEdit: (Task) -> Unit, onToggle: (Task) -> Unit, onTest: () -> Unit,
) {
    val sorted = remember(tasks, now) { sortTasks(tasks, now) }
    Scaffold(
        containerColor = Bg,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, containerColor = Accent, contentColor = OnAccent, shape = CircleShape) {
                Icon(Icons.Default.Add, contentDescription = "Nova tarefa")
            }
        },
    ) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = pad.calculateTopPadding() + 24.dp,
                bottom = pad.calculateBottomPadding() + 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tarefas", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Ink)
                        val n = tasks.count { it.active }
                        Text(if (n == 1) "1 ativa" else "$n ativas", color = Muted, fontSize = 14.sp)
                    }
                    TextButton(onClick = onTest) {
                        Icon(Icons.Default.Notifications, null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Testar aviso", color = Accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            items(warnings) { w -> WarningCard(w) }
            if (tasks.isEmpty()) item { EmptyState() }
            items(sorted, key = { it.id }) { t ->
                TaskRow(t, now, onClick = { onEdit(t) }, onToggle = onToggle)
            }
        }
    }
}

@Composable
fun WarningCard(w: Warning) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface2)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = Accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(w.text, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { w.open(ctx) }) { Text(w.action, color = Accent, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Nada por aqui ainda", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        Text("Toque em + para criar sua primeira tarefa.", color = Muted, fontSize = 14.sp)
    }
}

@Composable
fun CheckCircle(checked: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(CircleShape)
            .background(if (checked) Accent else Color.Transparent)
            .border(2.dp, if (checked) Accent else Muted, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, null, tint = OnAccent, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TaskRow(t: Task, now: LocalDateTime, onClick: () -> Unit, onToggle: (Task) -> Unit) {
    val next = if (t.active) Recurrence.next(t, now) else null
    val overdue = t.active && next == null
    Row(
        Modifier.fillMaxWidth().alpha(if (t.active) 1f else 0.45f)
            .clip(RoundedCornerShape(18.dp)).background(Surface1)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (t.repeat == Repeat.ONCE) {
            CheckCircle(t.done) { onToggle(t.copy(done = !t.done)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                t.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium,
                textDecoration = if (t.done) TextDecoration.LineThrough else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text("${hhmm(t.hour, t.minute)} · ${describe(t)}", color = Muted, fontSize = 13.sp)
            when {
                overdue -> Text("Atrasada", color = Danger, fontSize = 13.sp)
                next != null -> Text("Próximo aviso: ${nextLabel(next, now)}", color = Accent, fontSize = 13.sp)
                t.done -> Text("Feita", color = Muted, fontSize = 13.sp)
                !t.enabled -> Text("Pausada", color = Muted, fontSize = 13.sp)
            }
        }
        if (t.repeat != Repeat.ONCE) {
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = t.enabled,
                onCheckedChange = { onToggle(t.copy(enabled = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = OnAccent, checkedTrackColor = Accent,
                    uncheckedThumbColor = Muted, uncheckedTrackColor = Surface2, uncheckedBorderColor = Line,
                ),
            )
        }
    }
}

// ---------- Editor ----------
@Composable
fun Section(text: String) {
    Spacer(Modifier.height(24.dp))
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(10.dp))
}

@Composable
fun Hint(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(text, color = Muted, fontSize = 13.sp)
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) Accent else Surface1)
            .border(1.dp, if (selected) Accent else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text, color = if (selected) OnAccent else Ink, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, placeholder: String, focus: Boolean = false) {
    val fr = remember { FocusRequester() }
    OutlinedTextField(
        value = value, onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().focusRequester(fr),
        label = { Text(label) },
        placeholder = { Text(placeholder, color = Muted) },
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Line,
            focusedLabelColor = Accent, unfocusedLabelColor = Muted,
            cursorColor = Accent, focusedTextColor = Ink, unfocusedTextColor = Ink,
        ),
    )
    if (focus) LaunchedEffect(Unit) {
        delay(250)
        runCatching { fr.requestFocus() }
    }
}

@Composable
fun CircleCell(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.aspectRatio(1f).clip(CircleShape).background(if (selected) Accent else Surface1).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) OnAccent else Ink, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
fun WeekdayPicker(selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in 1..7) {
            val on = d in selected
            CircleCell(DIAS[d - 1], on, Modifier.weight(1f)) { onChange(if (on) selected - d else selected + d) }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Dias úteis", selected == setOf(1, 2, 3, 4, 5)) { onChange(setOf(1, 2, 3, 4, 5)) }
        Chip("Fins de semana", selected == setOf(6, 7)) { onChange(setOf(6, 7)) }
        Chip("Todos", selected.size == 7) { onChange((1..7).toSet()) }
    }
}

@Composable
fun DayGrid(max: Int, selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..max).chunked(7).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val d = row.getOrNull(i)
                    if (d == null) Spacer(Modifier.weight(1f))
                    else CircleCell("$d", d == selected, Modifier.weight(1f)) { onPick(d) }
                }
            }
        }
    }
}

@Composable
fun MonthGrid(selected: Int, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..12).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { m ->
                    val on = m == selected
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .background(if (on) Accent else Surface1)
                            .clickable { onPick(m) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            MESES_CURTOS[m - 1], color = if (on) OnAccent else Ink, fontSize = 14.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private fun monthLength(m: Int) = YearMonth.of(2024, m).lengthOfMonth() // 2024: fevereiro com 29

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(initial: Task, onSave: (Task) -> Unit, onDelete: (Task) -> Unit, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    var t by remember { mutableStateOf(initial) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val isNew = initial.id == 0

    fun trySave() {
        val title = t.title.trim()
        when {
            title.isEmpty() -> toast(ctx, "Dê um nome para a tarefa")
            t.repeat == Repeat.WEEKLY && t.weekdays.isEmpty() -> toast(ctx, "Escolha pelo menos um dia da semana")
            t.repeat == Repeat.ONCE && Recurrence.next(t, LocalDateTime.now()) == null ->
                toast(ctx, "Esse dia e horário já passaram")
            else -> onSave(t.copy(title = title, notes = t.notes.trim(), done = false))
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "Cancelar", tint = Muted) }
                Text(
                    if (isNew) "Nova tarefa" else "Editar tarefa", color = Ink, fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { trySave() }) {
                    Text("Salvar", color = Accent, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Field(t.title, { t = t.copy(title = it) }, "O que fazer", "Ex.: Tomar remédio", focus = isNew)
            Spacer(Modifier.height(12.dp))
            Field(t.notes, { t = t.copy(notes = it) }, "Detalhes (opcional)", "Aparece junto com o aviso")

            Section("Horário")
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Surface1)
                    .clickable { showTime = true }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(hhmm(t.hour, t.minute), color = Ink, fontSize = 46.sp, fontWeight = FontWeight.Light)
            }

            Section("Repete")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Repeat.ONCE to "Uma vez", Repeat.DAILY to "Todo dia", Repeat.WEEKLY to "Semanal",
                    Repeat.MONTHLY to "Mensal", Repeat.YEARLY to "Anual",
                ).forEach { (r, label) -> Chip(label, t.repeat == r) { t = t.copy(repeat = r) } }
            }

            when (t.repeat) {
                Repeat.ONCE -> {
                    Section("Dia")
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface1)
                            .clickable { showDate = true }.padding(horizontal = 16.dp, vertical = 16.dp),
                    ) {
                        Text(longDate(t.date), color = Ink, fontSize = 16.sp)
                    }
                }
                Repeat.DAILY -> {}
                Repeat.WEEKLY -> {
                    Section("Dias da semana")
                    WeekdayPicker(t.weekdays) { t = t.copy(weekdays = it) }
                }
                Repeat.MONTHLY -> {
                    Section("Dia do mês")
                    DayGrid(31, t.day) { t = t.copy(day = it) }
                    if (t.day > 28) Hint("Nos meses que não têm o dia ${t.day}, o aviso vem no último dia do mês.")
                }
                Repeat.YEARLY -> {
                    Section("Mês")
                    MonthGrid(t.month) { m -> t = t.copy(month = m, day = minOf(t.day, monthLength(m))) }
                    Section("Dia")
                    DayGrid(monthLength(t.month), t.day) { t = t.copy(day = it) }
                    if (t.month == 2 && t.day == 29) Hint("Nos anos sem 29 de fevereiro, o aviso vem no dia 28.")
                }
            }

            Spacer(Modifier.height(24.dp))
            val preview = Recurrence.next(t, LocalDateTime.now())
            Text(
                if (preview != null) "Próximo aviso: ${nextLabel(preview, LocalDateTime.now())}"
                else if (t.repeat == Repeat.WEEKLY) "Escolha pelo menos um dia"
                else "Esse dia e horário já passaram",
                color = if (preview != null) Accent else Danger, fontSize = 14.sp,
            )

            if (!isNew) {
                Spacer(Modifier.height(32.dp))
                TextButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Delete, null, tint = Danger, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Apagar tarefa", color = Danger, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (showTime) {
        val st = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            containerColor = Surface1,
            confirmButton = {
                TextButton(onClick = { t = t.copy(hour = st.hour, minute = st.minute); showTime = false }) {
                    Text("OK", color = Accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancelar", color = Muted) } },
            text = { TimePicker(state = st) },
        )
    }

    if (showDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val ds = rememberDatePickerState(
            initialSelectedDateMillis = t.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    ds.selectedDateMillis?.let { ms ->
                        t = t.copy(date = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDate = false
                }) { Text("OK", color = Accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancelar", color = Muted) } },
        ) { DatePicker(state = ds) }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            containerColor = Surface1,
            title = { Text("Apagar tarefa?", color = Ink) },
            text = { Text(t.title, color = Muted) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDelete(initial) }) { Text("Apagar", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancelar", color = Muted) } },
        )
    }
}
