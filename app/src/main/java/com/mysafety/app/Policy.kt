package com.mysafety.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object Policy {
    fun admin(c: Context) = ComponentName(c, MyDeviceAdminReceiver::class.java)
    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isAdminActive(c: Context) = dpm(c).isAdminActive(admin(c))

    fun isAccessibilityOn(c: Context): Boolean {
        val me = ComponentName(c, GuardService::class.java).flattenToString()
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return list.split(":").any { it.equals(me, true) }
    }

    /** লকের মেয়াদ শেষ হলে Device Admin সরিয়ে নেয়, যাতে অ্যাপ আনইন্সটল করা যায়। */
    fun release(c: Context) {
        val d = dpm(c); val a = admin(c)
        try { if (d.isAdminActive(a)) d.removeActiveAdmin(a) } catch (_: Throwable) { }
    }
}
