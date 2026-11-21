package com.mysafety.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.max

/** লকের বাকি সময় সংরক্ষণ করে। ঘড়ি পিছিয়ে দিলেও সময় কমে না। */
object LockStore {
    const val DAY_MS = 86_400_000L
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("my_safety_lock", Context.MODE_PRIVATE)

    private fun bootCount(c: Context) =
        Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)

    @Synchronized
    fun start(c: Context, days: Int = 30) {
        sp(c).edit()
            .putBoolean("active", true)
            .putLong("remaining", days * DAY_MS)
            .putLong("lastE", SystemClock.elapsedRealtime())
            .putLong("lastW", System.currentTimeMillis())
            .putInt("boot", bootCount(c))
            .putLong("drift", System.currentTimeMillis() - SystemClock.elapsedRealtime())
            .apply()
    }

    @Synchronized
    private fun tick(c: Context) {
        val p = sp(c)
        if (!p.getBoolean("active", false)) return
        val nowE = SystemClock.elapsedRealtime()
        val nowW = System.currentTimeMillis()
        val nowB = bootCount(c)
        val lastE = p.getLong("lastE", 0)
        val lastW = p.getLong("lastW", nowW)
        val sameBoot = nowB == p.getInt("boot", -1) && nowE >= lastE
        val delta = if (sameBoot) nowE - lastE else max(0L, nowW - lastW)
        val rem = max(0L, p.getLong("remaining", 0) - delta)
        val ed = p.edit().putLong("remaining", rem).putLong("lastE", nowE)
            .putLong("lastW", nowW).putInt("boot", nowB)
        if (!sameBoot) ed.putLong("drift", nowW - nowE) // রিবুটের পর নতুন বেসলাইন
        ed.apply()
    }

    fun isActive(c: Context) = sp(c).getBoolean("active", false)

    fun remainingMs(c: Context): Long { tick(c); return sp(c).getLong("remaining", 0) }

    fun isLocked(c: Context) = isActive(c) && remainingMs(c) > 0

    fun isExpired(c: Context) = isActive(c) && remainingMs(c) <= 0

    /**
     * একই বুটে (ঘড়ি - elapsedRealtime) স্থির থাকে। এটা সরে গেলে বোঝা যায় ফোনের ঘড়ি হাতে বদলানো হয়েছে।
     * আনলক কোডের সময় যাচাইয়ে এটা কাজে লাগে।
     */
    @Synchronized
    fun clockChanged(c: Context, toleranceMs: Long): Boolean {
        tick(c)
        val p = sp(c)
        if (!p.getBoolean("active", false)) return false
        val drift = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        return abs(drift - p.getLong("drift", drift)) > toleranceMs
    }

    @Synchronized
    fun clear(c: Context) { sp(c).edit().clear().apply() }
}
