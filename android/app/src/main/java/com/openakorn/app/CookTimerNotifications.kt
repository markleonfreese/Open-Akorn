package com.openakorn.app

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object CookTimerNotifications {
    const val ACTION_TIMER_EXPIRED = "com.openakorn.app.action.COOK_TIMER_EXPIRED"
    private const val CHANNEL_ID = "cook_timer"
    private const val NOTIFICATION_ID = 17401
    private const val ALARM_REQUEST_CODE = 17402

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun schedule(context: Context, triggerAtElapsedRealtime: Long): Boolean {
        if (!canPost(context)) return false
        createChannel(context)
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedRealtime,
                alarmPendingIntent(context),
            )
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(alarmPendingIntent(context))
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    fun showExpired(context: Context) {
        if (!canPost(context)) return
        createChannel(context)
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                context,
                ALARM_REQUEST_CODE + 1,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.timer_notification_title))
            .setContentText(context.getString(R.string.timer_notification_body))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun alarmPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CookTimerAlarmReceiver::class.java).setAction(ACTION_TIMER_EXPIRED)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.timer_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.timer_notification_channel_description) }
        manager.createNotificationChannel(channel)
    }
}

class CookTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != CookTimerNotifications.ACTION_TIMER_EXPIRED) return
        CookTimerNotifications.showExpired(context)
    }
}
