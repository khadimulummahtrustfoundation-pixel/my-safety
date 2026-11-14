package com.mysafety.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings

object Policy {
    // Device Owner থাকলে এই ফ্যামিলি-ফিল্টার Private DNS জোর করে বসানো হবে (পরিবর্তন করা যাবে না)
    const val FAMILY_DNS = "family-filter-dns.cleanbrowsing.org"

    private val RESTRICTIONS = listOf(
        "no_control_apps",       // Force stop / Clear data বন্ধ
        "no_safe_boot",          // Safe mode বন্ধ
        "no_factory_reset",      // Factory reset বন্ধ
        "no_config_vpn",         // VPN দিয়ে DNS বাইপাস বন্ধ
        "no_config_date_time",   // ঘড়ি বদলে চালাকি বন্ধ
        "no_debugging_features"  // Developer/ADB বন্ধ
    )

    fun admin(c: Context) = ComponentName(c, MyDeviceAdminReceiver::class.java)
    private fun dpm(c: Context) = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    fun isAdminActive(c: Context) = dpm(c).isAdminActive(admin(c))
    fun isDeviceOwner(c: Context) = dpm(c).isDeviceOwnerApp(c.packageName)

    fun isAccessibilityOn(c: Context): Boolean {
        val me = ComponentName(c, GuardService::class.java).flattenToString()
        val list = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return list.split(":").any { it.equals(me, true) }
    }

    /** Device Owner হলে Accessibility নিজে নিজেই চালু করে নেয়। */
    fun ensureAccessibility(c: Context) {
        if (isAccessibilityOn(c)) return
        try {
            val me = ComponentName(c, GuardService::class.java).flattenToString()
            val cur = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val nv = if (cur.isBlank()) me else "$cur:$me"
            Settings.Secure.putString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, nv)
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: Throwable) { }
    }

    fun applyLock(c: Context) {
        if (!isDeviceOwner(c)) return
        val d = dpm(c); val a = admin(c)
        try {
            d.setPermissionGrantState(a, c.packageName,
                android.Manifest.permission.WRITE_SECURE_SETTINGS,
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        } catch (_: Throwable) { }
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                d.setPermissionGrantState(a, c.packageName, "android.permission.POST_NOTIFICATIONS",
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            } catch (_: Throwable) { }
        }
        try { d.setUninstallBlocked(a, c.packageName, true) } catch (_: Throwable) { }
        RESTRICTIONS.forEach { try { d.addUserRestriction(a, it) } catch (_: Throwable) { } }
        if (Build.VERSION.SDK_INT >= 29) {
            try { d.setGlobalPrivateDnsModeSpecifiedHost(a, FAMILY_DNS) } catch (_: Throwable) { }
        }
        ensureAccessibility(c)
    }

    /** লকের মেয়াদ শেষ হলে সব ছেড়ে দেয় এবং Device Owner সরিয়ে নেয়। */
    fun release(c: Context) {
        val d = dpm(c); val a = admin(c)
        if (d.isDeviceOwnerApp(c.packageName)) {
            RESTRICTIONS.forEach { try { d.clearUserRestriction(a, it) } catch (_: Throwable) { } }
            try { d.setUninstallBlocked(a, c.packageName, false) } catch (_: Throwable) { }
            if (Build.VERSION.SDK_INT >= 29) {
                try { d.setGlobalPrivateDnsModeOpportunistic(a) } catch (_: Throwable) { }
            }
            try { d.clearDeviceOwnerApp(c.packageName) } catch (_: Throwable) { }
        }
        try { if (d.isAdminActive(a)) d.removeActiveAdmin(a) } catch (_: Throwable) { }
    }
}
