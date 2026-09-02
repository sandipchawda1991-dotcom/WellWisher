package com.example.wellwisher

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import androidx.core.app.NotificationCompat

class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {

        // If phone rebooted — reschedule all reminders
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            rescheduleAll(context)
            return
        }

        val name = intent.getStringExtra("name") ?: return
        val cat = intent.getStringExtra("cat") ?: "birthday"
        val contactId = intent.getStringExtra("contact_id") ?: ""

        val emoji = if (cat == "anniversary") "💑" else "🎂"
        val label = if (cat == "anniversary") "Anniversary" else "Birthday"

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("contact_id", contactId)
        }
        val pi = PendingIntent.getActivity(context, name.hashCode(), mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, "wellwisher")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji $label Today — $name!")
            .setContentText("Tap to open WellWisher and send your wish! 🎉")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Today is ${name}'s $label! Open WellWisher to send them a beautiful personalised wish right now! 🎉"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(name.hashCode(), notification)
    }

    private fun rescheduleAll(context: Context) {
        val prefs = context.getSharedPreferences("wellwisher", Context.MODE_PRIVATE)
        val arr = org.json.JSONArray(prefs.getString("contacts", "[]") ?: "[]")
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = java.util.Calendar.getInstance()

        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optString("id", "")
            val name = o.optString("name", "")
            val cat = o.optString("cat", "birthday")
            val date = o.optString("date", "")
            val remindHour = o.optInt("remindHour", 9)
            val remindMin = o.optInt("remindMin", 0)

            if (name.isEmpty() || date.isEmpty()) continue

            val intent = Intent(context, BirthdayReceiver::class.java).apply {
                putExtra("name", name)
                putExtra("cat", cat)
                putExtra("contact_id", id)
            }
            val pi = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            try {
                val parts = date.split("-")
                val cal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.MONTH, parts[1].toInt() - 1)
                    set(java.util.Calendar.DAY_OF_MONTH, parts[2].toInt())
                    set(java.util.Calendar.HOUR_OF_DAY, remindHour)
                    set(java.util.Calendar.MINUTE, remindMin)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                    if (before(now)) add(java.util.Calendar.YEAR, 1)
                }
                am.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis,
                    365L * 24 * 60 * 60 * 1000, pi)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}
