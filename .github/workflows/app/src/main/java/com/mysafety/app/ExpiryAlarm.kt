package com.mysafety.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager

/** মেয়াদ শেষের অফলাইন অ্যালার্ম (ইন্টারনেট লাগে না, Doze মোডেও বাজে) */
object ExpiryAlarm {
    const val CHANNEL = "my_safety_expiry"
    private const val NOTIF_ID = 1001

    fun schedule(c: Context, remainingMs: Long) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fire = PendingIntent.getBroadcast(c, 1, Intent(c, ExpiryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val show = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // setAlarmClock = সঠিক সময়ে বাজে, কোনো বাড়তি পারমিশন লাগে না
        am.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + remainingMs, show), fire)
    }

    fun cancel(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fire = PendingIntent.getBroadcast(c, 1, Intent(c, ExpiryReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(fire)
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
    }

    fun notifyNow(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ch = NotificationChannel(CHANNEL, "লক মেয়াদ শেষ", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "৩০ দিনের লক শেষ হলে অ্যালার্ম"
            setSound(sound, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(ch)
        val open = PendingIntent.getActivity(c, 3, Intent(c, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("🎉 লক শেষ হয়েছে!")
            .setContentText("৩০ দিন সফলভাবে পূর্ণ হয়েছে। অ্যাপ খুলে মুক্ত করুন।")
            .setStyle(Notification.BigTextStyle().bigText("৩০ দিন সফলভাবে পূর্ণ হয়েছে। অ্যাপ খুলে চাইলে মুক্ত করুন, অথবা আবার নতুন করে লক করুন।"))
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT  // বন্ধ না করা পর্যন্ত বাজতে থাকবে
        nm.notify(NOTIF_ID, n)
    }
}
