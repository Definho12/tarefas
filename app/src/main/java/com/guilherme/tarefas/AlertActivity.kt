package com.guilherme.tarefas

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/** Tela cheia que aparece na hora da tarefa, inclusive com o celular bloqueado. */
class AlertActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_NOTES = "notes"
    }

    private var taskId = 0
    private val title = mutableStateOf("")
    private val notes = mutableStateOf("")
    private val time = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        read(intent)
        setContent {
            AppTheme {
                AlertScreen(
                    time = time.value, title = title.value, notes = notes.value,
                    onDone = { Actions.done(this, taskId); finish() },
                    onSnooze = { Actions.snooze(this, taskId); finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(i: Intent) {
        taskId = i.getIntExtra(Scheduler.EXTRA_ID, 0)
        title.value = i.getStringExtra(EXTRA_TITLE) ?: ""
        notes.value = i.getStringExtra(EXTRA_NOTES) ?: ""
        val now = LocalTime.now()
        time.value = hhmm(now.hour, now.minute)
    }
}

@androidx.compose.runtime.Composable
private fun AlertScreen(time: String, title: String, notes: String, onDone: () -> Unit, onSnooze: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("LEMBRETE", color = Accent, fontSize = 13.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text(time, color = Muted, fontSize = 56.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(24.dp))
        Text(title, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, lineHeight = 36.sp)
        if (notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(notes, color = Muted, fontSize = 17.sp, textAlign = TextAlign.Center, lineHeight = 24.sp)
        }
        Spacer(Modifier.weight(1.3f))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) { Text("Feito", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Line),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Ink),
        ) { Text("Adiar ${Scheduler.SNOOZE_MINUTES} min", fontSize = 17.sp) }
    }
}
