package com.mysafety.app

import android.accessibilityservice.AccessibilityService
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
 * লক চালু থাকলে Settings-এ DNS / My Safety / Device admin ইত্যাদি পেজ খুললেই
 * ফুল-স্ক্রিন পপআপ দেখায়। একমাত্র বাটন চাপলে হোম স্ক্রিনে পাঠিয়ে দেয়।
 */
class GuardService : AccessibilityService() {

    private var overlay: View? = null

    private val keywords = listOf(
        "private dns", "dns", "ডিএনএস", "প্রাইভেট ডিএনএস",
        "my safety",
        "device admin", "ডিভাইস অ্যাডমিন", "device administrator"
    )

    private fun isWatched(pkg: String) =
        pkg.contains("settings") || pkg.contains("packageinstaller") ||
        pkg.contains("permissioncontroller") || pkg.contains("securitycenter") ||
        pkg.contains("deviceadmin") || pkg.contains("devicecare")

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || overlay != null) return
        if (!LockStore.isLocked(this)) return
        val pkg = e.packageName?.toString() ?: return
        if (!isWatched(pkg)) return
        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collect(root, sb, 0)
        val text = sb.toString().lowercase()
        if (keywords.any { text.contains(it) }) showBlock()
    }

    private fun collect(n: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        if (n == null || depth > 25) return
        n.text?.let { sb.append(it).append('|') }
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
        box.addView(tv("🛡️", 56f, false))
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
    override fun onDestroy() { hide(); super.onDestroy() }
}
