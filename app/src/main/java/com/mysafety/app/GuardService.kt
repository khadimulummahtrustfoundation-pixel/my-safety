package com.mysafety.app

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * লক চালু থাকলে Settings-এ DNS / My Safety (App list, Search, App info) / Device admin ইত্যাদি পেজ খুললেই
 * ফুল-স্ক্রিন পপআপ দেখায়। একমাত্র বাটন চাপলে হোম স্ক্রিনে পাঠিয়ে দেয়।
 */
class GuardService : AccessibilityService() {

    private var overlay: View? = null
    private val handler = Handler(Looper.getMainLooper())

    // ব্যাকআপ: অ্যালার্ম কোনো কারণে না বাজলেও মেয়াদ শেষে এখান থেকে নোটিফিকেশন যাবে
    private val watchdog = object : Runnable {
        override fun run() {
            if (LockStore.isExpired(this@GuardService)) ExpiryAlarm.notifyOnce(this@GuardService)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        handler.removeCallbacks(watchdog); handler.post(watchdog)
    }

    // এই অ্যাপের নাম (লেবেল)। App list / search result / App info পেজে এই নামই দেখায়।
    private val ownLabels by lazy {
        listOf("my safety", "mysafety", applicationInfo.loadLabel(packageManager).toString().lowercase())
            .distinct()
    }

    private val keywords = listOf(
        "private dns", "dns", "ডিএনএস", "প্রাইভেট ডিএনএস",
        "device admin", "ডিভাইস অ্যাডমিন", "device administrator"
    )

    private fun isWatched(pkg: String) =
        pkg.contains("settings") || pkg.contains("packageinstaller") ||
        pkg.contains("permissioncontroller") || pkg.contains("securitycenter") ||
        pkg.contains("deviceadmin") || pkg.contains("devicecare")

    private fun hasOwnLabel(text: String) = ownLabels.any { text.contains(it) }

    private val scan = Runnable { scanScreen() }

    private fun scheduleScan(vararg delays: Long) {
        handler.removeCallbacks(scan)
        delays.forEach { handler.postDelayed(scan, it) }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || overlay != null) return
        if (!LockStore.isLocked(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (!isWatched(pkg)) return

        when (e.eventType) {
            // App list / search result-এ "My Safety" লেখা আইটেমে ট্যাপ করলেই সাথে সাথে বাধা
            // (App info পেজ খোলার আগেই)।
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (clickedOwnApp(e.source)) { showBlock(); return }
                scheduleScan(150)
            }
            // নতুন পেজ খুললে কন্টেন্ট লোড হতে সময় লাগে, তাই কয়েকবার স্ক্যান করা হয়।
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scheduleScan(0, 150, 400, 900)
            else -> scheduleScan(100)
        }
    }

    /** ক্লিক করা আইটেম (বা তার সন্তান/ঠিক উপরের অভিভাবক) এর লেখায় অ্যাপের নাম আছে কিনা। */
    private fun clickedOwnApp(src: AccessibilityNodeInfo?): Boolean {
        if (src == null) return false
        val sb = StringBuilder()
        collect(src, sb, 0)
        if (hasOwnLabel(sb.toString().lowercase())) return true
        // ক্লিক হয়েছে শুধু আইকন/ছোট ভিউতে হলে, ছোট অভিভাবক সারিটা (লিস্টের রো) দেখি
        var p = src.parent
        var up = 0
        while (p != null && up < 3) {
            if (p.childCount in 1..8) {
                val t = StringBuilder(); collect(p, t, 0)
                if (hasOwnLabel(t.toString().lowercase())) return true
            }
            p = p.parent; up++
        }
        return false
    }

    private fun scanScreen() {
        if (overlay != null || !LockStore.isLocked(this)) return
        val roots = ArrayList<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { roots.add(it) }
        try {
            windows?.forEach { w -> w.root?.let { r -> roots.add(r) } }
        } catch (_: Throwable) { }
        for (root in roots) {
            val pkg = root.packageName?.toString() ?: continue
            if (!isWatched(pkg)) continue
            val sb = StringBuilder()
            collect(root, sb, 0)
            val text = sb.toString().lowercase()
            if (keywords.any { text.contains(it) } || hasOwnLabel(text)) { showBlock(); return }
        }
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (n == null || depth > 25) return
        n.text?.let { sb.append(it).append('|') }
        n.hintText?.let { sb.append(it).append('|') }
        n.contentDescription?.let { sb.append(it).append('|') }
        for (i in 0 until n.childCount) collect(n.getChild(i), sb, depth + 1)
    }

    private fun showBlock() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dp = resources.displayMetrics.density

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#F20B2415"))
            setPadding((28 * dp).toInt(), 0, (28 * dp).toInt(), 0)
            isClickable = true
        }
        fun tv(t: String, size: Float, bold: Boolean) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        box.addView(android.widget.ImageView(this).apply { setImageResource(R.drawable.ic_logo_inverse) },
            LinearLayout.LayoutParams((84 * dp).toInt(), (84 * dp).toInt()))
        box.addView(tv("My Safety", 26f, true))
        box.addView(tv("লক চালু আছে।\nএই সেটিং এখন খোলা যাবে না।", 17f, false))

        val btn = Button(this).apply {
            text = "ঠিক আছে"; setTextColor(Color.WHITE); textSize = 17f
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1FAE5C")); cornerRadius = 28 * dp
            }
            setOnClickListener {
                performGlobalAction(GLOBAL_ACTION_HOME)
                hide()
            }
        }
        box.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (54 * dp).toInt())
            .apply { topMargin = (24 * dp).toInt() })

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try { wm.addView(box, lp); overlay = box } catch (_: Throwable) { }
    }

    private fun hide() {
        val v = overlay ?: return
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Throwable) { }
        overlay = null
    }

    override fun onInterrupt() {}
    override fun onDestroy() { handler.removeCallbacks(watchdog); handler.removeCallbacks(scan); hide(); super.onDestroy() }
}
