package com.mysafety.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when {
            LockStore.isLocked(context) -> {
                Policy.applyLock(context)
                ExpiryAlarm.schedule(context, LockStore.remainingMs(context)) // রিবুটের পর অ্যালার্ম আবার সেট
            }
            LockStore.isExpired(context) -> ExpiryAlarm.notifyNow(context) // বন্ধ থাকা অবস্থায় মেয়াদ গেলে
        }
    }
}
