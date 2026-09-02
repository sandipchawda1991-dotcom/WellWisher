package com.example.wellwisher

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class BirthdayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: return
        val cat = intent.getStringExtra("cat") ?: "birthday"
        val emoji = when(cat) {
            "anniversary" -> "💑"
            "work" -> "💼"
            "graduation" -> "🎓"
            "friendship" -> "🤝"
            else -> "🎂"
        }
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, "wellwisher")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji Wish $name today!")
            .setContentText("Tap to open WellWisher and send your message")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(name.hashCode(), n)
    }
}
