package com.guilherme.tarefas

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

object Scheduler {
    const val ACTION_FIRE = "com.guilherme.tarefas.FIRE"
    const val ACTION_DONE = "com.guilherme.tarefas.DONE"
    const val ACTION_SNOOZE = "com.guilherme.tarefas.SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_AT = "at"
    const val EXTRA_SNOOZE = "snooze"
    const val TEST_ID = -1
    const val SNOOZE_MINUTES = 10

    fun toMillis(dt: LocalDateTime): Long = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun fromMillis(ms: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())

    private fun fireIntent(ctx: Context, id: Int, snooze: Boolean, at: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_SNOOZE, snooze)
            .putExtra(EXTRA_AT, at)
        return PendingIntent.getBroadcast(
            ctx, id * 2 + (if (snooze) 1 else 0), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alarms(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    /** Agenda o próximo aviso da tarefa (ou cancela, se ela estiver pausada/feita). */
    fun schedule(ctx: Context, t: Task, after: LocalDateTime = LocalDateTime.now()) {
        val am = alarms(ctx)
        am.cancel(fireIntent(ctx, t.id, false, 0))
        if (!t.active) return
        val next = Recurrence.next(t, after) ?: return
        val at = toMillis(next)
        setExact(am, at, fireIntent(ctx, t.id, false, at))
    }

    fun cancel(ctx: Context, id: Int) {
        val am = alarms(ctx)
        am.cancel(fireIntent(ctx, id, false, 0))
        am.cancel(fireIntent(ctx, id, true, 0))
    }

    fun snooze(ctx: Context, id: Int) {
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setExact(alarms(ctx), at, fireIntent(ctx, id, true, at))
    }

    fun test(ctx: Context) {
        val at = System.currentTimeMillis() + 5_000L
        setExact(alarms(ctx), at, fireIntent(ctx, TEST_ID, false, at))
    }

    fun rescheduleAll(ctx: Context) {
        TaskStore.all(ctx).forEach { schedule(ctx, it) }
    }

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || alarms(ctx).canScheduleExactAlarms()

    private fun setExact(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

object Actions {
    fun done(ctx: Context, id: Int) {
        Notifier.cancel(ctx, id)
        if (id == Scheduler.TEST_ID) return
        val t = TaskStore.get(ctx, id) ?: return
        if (t.repeat == Repeat.ONCE) {
            TaskStore.upsert(ctx, t.copy(done = true))
            Scheduler.cancel(ctx, id)
        }
    }

    fun snooze(ctx: Context, id: Int) {
        Notifier.cancel(ctx, id)
        Scheduler.snooze(ctx, id)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(Scheduler.EXTRA_ID, 0)
        when (intent.action) {
            Scheduler.ACTION_FIRE -> {
                if (id == Scheduler.TEST_ID) {
                    Notifier.show(ctx, Task(id = Scheduler.TEST_ID, title = "Teste de aviso", notes = "Se o celular vibrou, está tudo certo!"))
                    return
                }
                val t = TaskStore.get(ctx, id) ?: return
                if (!t.active) return
                Notifier.show(ctx, t)
                val snoozed = intent.getBooleanExtra(Scheduler.EXTRA_SNOOZE, false)
                if (!snoozed && t.repeat != Repeat.ONCE) {
                    val at = intent.getLongExtra(Scheduler.EXTRA_AT, System.currentTimeMillis())
                    val base = maxOf(at, System.currentTimeMillis()) + 1_000L
                    Scheduler.schedule(ctx, t, Scheduler.fromMillis(base))
                }
            }
            Scheduler.ACTION_DONE -> Actions.done(ctx, id)
            Scheduler.ACTION_SNOOZE -> Actions.snooze(ctx, id)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Scheduler.rescheduleAll(ctx)
    }
}
