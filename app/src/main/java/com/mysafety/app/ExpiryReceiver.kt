package com.mysafety.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ExpiryAlarm.notifyNow(context)
    }
}
