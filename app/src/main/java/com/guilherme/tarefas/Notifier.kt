package com.guilherme.tarefas

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat

object Notifier {
    private const val CHANNEL = "lembretes"
    val PATTERN = longArrayOf(0, 800, 400, 800, 400, 800)

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Lembretes de tarefas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Aviso na hora marcada de cada tarefa"
            enableVibration(true)
            vibrationPattern = PATTERN
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build()
            )
        }
        nm.createNotificationChannel(ch)
    }

    fun show(ctx: Context, t: Task) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val alert = Intent(ctx, AlertActivity::class.java)
            .putExtra(Scheduler.EXTRA_ID, t.id)
            .putExtra(AlertActivity.EXTRA_TITLE, t.title)
            .putExtra(AlertActivity.EXTRA_NOTES, t.notes)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(ctx, t.id, alert, flags)
        val done = PendingIntent.getBroadcast(
            ctx, t.id,
            Intent(ctx, AlarmReceiver::class.java).setAction(Scheduler.ACTION_DONE).putExtra(Scheduler.EXTRA_ID, t.id),
            flags
        )
        val snooze = PendingIntent.getBroadcast(
            ctx, t.id,
            Intent(ctx, AlarmReceiver::class.java).setAction(Scheduler.ACTION_SNOOZE).putExtra(Scheduler.EXTRA_ID, t.id),
            flags
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setColor(0xFF9B8CFF.toInt())
            .setContentTitle(t.title)
            .setContentText(t.notes.ifBlank { "Hora da tarefa" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(t.notes.ifBlank { "Hora da tarefa" }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(PATTERN)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Feito", done)
            .addAction(0, "Adiar ${Scheduler.SNOOZE_MINUTES} min", snooze)
            .build()

        ctx.getSystemService(NotificationManager::class.java).notify(t.id, n)
    }

    fun cancel(ctx: Context, id: Int) {
        ctx.getSystemService(NotificationManager::class.java).cancel(id)
    }
}
