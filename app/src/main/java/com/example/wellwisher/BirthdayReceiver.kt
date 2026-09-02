package com.example.wellwisher

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

class BirthdayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.TIME_SET" -> {
                rescheduleAll(context)
                return
            }
        }

        val name = intent.getStringExtra("name") ?: return
        val cat = intent.getStringExtra("cat") ?: "birthday"
        val contactId = intent.getStringExtra("contact_id") ?: ""

        val emoji = if (cat == "anniversary") "💑" else "🎂"
        val label = if (cat == "anniversary") "Anniversary" else "Birthday"

        val wishIntent = Intent(context, WishActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("contact_id", contactId)
        }
        val pi = PendingIntent.getActivity(
            context, contactId.hashCode(), wishIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "wellwisher")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $label Today — $name!")
            .setContentText("Tap to send a personalised wish right now!")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Today is ${name}'s $label! Tap to open WellWisher and send a beautiful wish!"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pi)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(contactId.hashCode(), notification)

        // Reschedule for next year
        val prefs = context.getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optString("id") == contactId) {
                scheduleAlarm(context, contactId, name, cat,
                    o.optString("date", ""),
                    o.optInt("remindHour", 9),
                    o.optInt("remindMin", 0),
                    nextYear = true)
                break
            }
        }
    }

    fun rescheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            scheduleAlarm(
                context,
                o.optString("id", ""),
                o.optString("name", ""),
                o.optString("cat", "birthday"),
                o.optString("date", ""),
                o.optInt("remindHour", 9),
                o.optInt("remindMin", 0),
                nextYear = false
            )
        }
    }

    fun scheduleAlarm(
        context: Context, id: String, name: String, cat: String,
        date: String, hour: Int, min: Int, nextYear: Boolean = false
    ) {
        if (name.isEmpty() || date.isEmpty() || id.isEmpty()) return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, BirthdayReceiver::class.java).apply {
            putExtra("name", name)
            putExtra("cat", cat)
            putExtra("contact_id", id)
        }
        val pi = PendingIntent.getBroadcast(
            context, id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            val parts = date.split("-")
            val now = Calendar.getInstance()
            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, parts[1].toInt() - 1)
                set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, min)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (nextYear) {
                    add(Calendar.YEAR, 1)
                } else {
                    if (before(now)) add(Calendar.YEAR, 1)
                }
            }
            val alarmInfo = AlarmManager.AlarmClockInfo(cal.timeInMillis, pi)
            am.setAlarmClock(alarmInfo, pi)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
