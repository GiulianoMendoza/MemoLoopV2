package com.example.memoloop

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat


class NotificationPublisher : BroadcastReceiver() {

    companion object {
        const val REMINDER_TITLE_EXTRA = "reminder_title"
        const val REMINDER_MESSAGE_EXTRA = "reminder_message"
        const val REMINDER_MESSAGE_EXTRA_KEY = "reminder_message_key"
        const val NOTIFICATION_ID_EXTRA = "notification_id"
        const val REMINDER_ID_EXTRA = "reminder_id"
        const val CHANNEL_ID = "MemoLoop_Channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(NOTIFICATION_ID_EXTRA, 0)
        val reminderTitle = intent.getStringExtra(REMINDER_TITLE_EXTRA) ?: context.getString(R.string.default_reminder_title)
        val reminderCategoryKey = intent.getStringExtra(REMINDER_MESSAGE_EXTRA_KEY) ?: ReminderConstants.CATEGORY_GENERAL_KEY
        val translatedCategory = ReminderConstants.getCategoryDisplayName(context, reminderCategoryKey)
        val reminderMessage = context.getString(R.string.notification_message_reminder, translatedCategory)


        val notificationHelper = NotificationHelper(context)
        notificationHelper.showNotification(reminderTitle, reminderMessage, notificationId)

        Log.d("NotificationPublisher", "Notification sent: ID $notificationId, Title: $reminderTitle, Category: $translatedCategory")
    }
}
