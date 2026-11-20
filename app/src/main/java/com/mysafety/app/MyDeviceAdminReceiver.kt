package com.mysafety.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "লক চালু থাকলে My Safety বন্ধ করা যাবে না।"
}
