package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.alarm.JarvisAlarmScheduler
import com.example.data.local.JarvisDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class JarvisBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val scheduler = JarvisAlarmScheduler(context)
            val db = JarvisDatabase.getDatabase(context)

            CoroutineScope(Dispatchers.IO).launch {
                val pendingAlarms = db.alarmReminderDao().getPendingAlarmsSync()
                val now = System.currentTimeMillis()
                for (alarm in pendingAlarms) {
                    if (alarm.triggerTimestamp > now) {
                        scheduler.scheduleAlarm(alarm.id, alarm.title, alarm.note, alarm.triggerTimestamp)
                    } else {
                        // Mark as missed
                        db.alarmReminderDao().updateAlarm(alarm.copy(isMissed = true, isEnabled = false))
                    }
                }
            }
        }
    }
}
