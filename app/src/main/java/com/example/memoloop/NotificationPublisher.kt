package com.example.memoloop

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationPublisher : BroadcastReceiver() {

    companion object {
        const val REMINDER_TITLE_EXTRA = "reminder_title_extra"
        const val REMINDER_MESSAGE_EXTRA = "reminder_message_extra"
        const val NOTIFICATION_ID_EXTRA = "notification_id_extra"
        const val REMINDER_ID_EXTRA = "reminder_id_extra"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            Log.e("NotificationPublisher", "Context or Intent is null in onReceive")
            return
        }

        val title = intent.getStringExtra(REMINDER_TITLE_EXTRA) ?: "Recordatorio"
        val message = intent.getStringExtra(REMINDER_MESSAGE_EXTRA) ?: "¡Es hora de tu recordatorio!"
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_EXTRA, 0)

        Log.d("NotificationPublisher", "Received broadcast for notificationId: $notificationId, Title: $title")

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(title, message, notificationId)
    }
}
