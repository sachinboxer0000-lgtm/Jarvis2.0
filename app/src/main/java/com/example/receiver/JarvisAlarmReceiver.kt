package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.JarvisDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra("alarm_id", -1L)
        val title = intent.getStringExtra("alarm_title") ?: "Scheduled Task"
        val note = intent.getStringExtra("alarm_note") ?: "JARVIS reminder"

        showAlarmNotification(context, title, note)

        // Mark as completed in database
        if (alarmId != -1L) {
            val db = JarvisDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                val alarm = db.alarmReminderDao().getAlarmById(alarmId)
                if (alarm != null) {
                    db.alarmReminderDao().updateAlarm(alarm.copy(isCompleted = true, isEnabled = false))
                }
            }
        }
    }

    private fun showAlarmNotification(context: Context, title: String, note: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "jarvis_alarms_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "JARVIS Alarms & Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority autonomous task reminders from JARVIS"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("JARVIS Alert: $title")
            .setContentText(note.ifBlank { "Sir, your scheduled reminder is due now." })
            .setStyle(NotificationCompat.BigTextStyle().bigText("Sir, your scheduled task '$title' has triggered.\n$note"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
}
