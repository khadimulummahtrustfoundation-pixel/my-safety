package com.mysafety.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private var ring: CountdownView? = null
    private var lastState = ""
    private val green = Color.parseColor("#12803F")
    private val dark = Color.parseColor("#0B3D20")

    private val ticker = object : Runnable {
        override fun run() {
            if (stateKey() != lastState) build() else updateCountdown()
            h.postDelayed(this, 1000)
        }
    }

    private fun stateKey() = "${LockStore.isLocked(this)}|${LockStore.isExpired(this)}|" +
        "${Policy.isAdminActive(this)}|${Policy.isAccessibilityOn(this)}|${Policy.isDeviceOwner(this)}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    override fun onResume() {
        super.onResume()
        if (LockStore.isLocked(this)) Policy.applyLock(this)
        build()
        h.postDelayed(ticker, 1000)
    }

    override fun onPause() { super.onPause(); h.removeCallbacks(ticker) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bn(s: String) = s.map { if (it in '0'..'9') ('\u09E6' + (it - '0')) else it }.joinToString("")

    private fun tv(t: String, size: Float, color: Int = dark, bold: Boolean = false, center: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(6))
        }

    private fun btn(t: String, bg: Int, onClick: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.WHITE); textSize = 16f; isAllCaps = false
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(28).toFloat() }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) }
    }

    private fun chip(ok: Boolean, label: String) = TextView(this).apply {
        text = (if (ok) "✓ " else "✗ ") + label
        textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = GradientDrawable().apply {
            setColor(if (ok) Color.parseColor("#33FFFFFF") else Color.parseColor("#66C62828"))
            cornerRadius = dp(20).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    private fun build() {
        lastState = stateKey()
        ring = null
        val locked = LockStore.isLocked(this)
        val expired = LockStore.isExpired(this)
        val admin = Policy.isAdminActive(this)
        val a11y = Policy.isAccessibilityOn(this)
        val owner = Policy.isDeviceOwner(this)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(32))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(col) }

        if (locked) {
            // ---------- লক চালু: বড় কাউন্টডাউন ----------
            window.statusBarColor = dark; window.navigationBarColor = Color.parseColor("#12803F")
            scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#0B3D20"), Color.parseColor("#12803F")))
            col.gravity = Gravity.CENTER
            col.addView(tv("🔒  লক চালু আছে", 22f, Color.WHITE, true, true))
            col.addView(tv("My Safety আপনাকে সুরক্ষিত রাখছে", 14f, Color.parseColor("#CCFFFFFF"), false, true))

            val r = CountdownView(this); ring = r
            col.addView(r, LinearLayout.LayoutParams(dp(300), dp(300)).apply { topMargin = dp(20); bottomMargin = dp(12) })
            updateCountdown()

            val endText = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale("bn", "BD"))
                .format(Date(System.currentTimeMillis() + LockStore.remainingMs(this)))
            col.addView(tv("শেষ হবে", 13f, Color.parseColor("#99FFFFFF"), false, true))
            col.addView(tv(bn(endText), 16f, Color.WHITE, true, true))
            col.addView(tv("মেয়াদ শেষে অ্যালার্ম বাজবে (অফলাইনেও)", 13f, Color.parseColor("#9BFFC5"), false, true))

            val chips = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
            chips.addView(chip(admin, "Admin")); chips.addView(chip(a11y, "গার্ড")); chips.addView(chip(owner, "Owner"))
            col.addView(chips, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
            col.addView(tv("এই সময়ে আনইন্সটল ও DNS সেটিংস খোলা যাবে না। কোনো আনলক অপশন নেই।", 12f,
                Color.parseColor("#B3FFFFFF"), false, true))
        } else {
            window.statusBarColor = dark; window.navigationBarColor = Color.parseColor("#F4FAF6")
            scroll.setBackgroundColor(Color.parseColor("#F4FAF6"))
            col.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo) },
                LinearLayout.LayoutParams(dp(96), dp(96)))
            col.addView(tv("My Safety", 28f, green, true, true))

            fun row(ok: Boolean, label: String) =
                col.addView(tv((if (ok) "✅  " else "❌  ") + label, 16f), LinearLayout.LayoutParams(-1, -2))

            if (expired) {
                col.addView(tv("🎉 ৩০ দিন শেষ!", 24f, green, true, true))
                col.addView(tv("এখন চাইলে সব বন্ধন ছেড়ে দিতে পারেন। এরপর অ্যাপ আনইন্সটল করা যাবে।", 15f, dark, false, true))
                col.addView(btn("মুক্ত করুন (Release)", green) {
                    ExpiryAlarm.cancel(this); Policy.release(this); LockStore.clear(this)
                    Toast.makeText(this, "মুক্ত করা হয়েছে", Toast.LENGTH_LONG).show(); build()
                })
                col.addView(btn("আবার ৩০ দিন লক করুন", Color.parseColor("#C62828")) { confirmLock() })
            } else {
                col.addView(tv("সেটআপ", 20f, dark, true), LinearLayout.LayoutParams(-1, -2))
                row(admin, "Device Admin"); row(a11y, "Accessibility গার্ড"); row(owner, "Device Owner (ঐচ্ছিক, সবচেয়ে শক্তিশালী)")

                if (!admin) col.addView(btn("Device Admin পারমিশন দিন", green) {
                    startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Policy.admin(this@MainActivity))
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "লক চালু থাকলে আনইন্সটল ঠেকাতে এটি দরকার।")
                    })
                })
                if (!a11y) col.addView(btn("Accessibility চালু করুন", green) {
                    Toast.makeText(this, "Installed apps → My Safety → চালু করুন", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                if (!owner) {
                    col.addView(tv("Device Owner সেট করতে (কম্পিউটার থেকে একবার):", 13f, Color.GRAY),
                        LinearLayout.LayoutParams(-1, -2))
                    col.addView(TextView(this).apply {
                        text = "adb shell dpm set-device-owner com.mysafety.app/.MyDeviceAdminReceiver"
                        textSize = 12f; setTextIsSelectable(true); setTextColor(dark)
                        setBackgroundColor(Color.parseColor("#E3F2E8")); setPadding(dp(10), dp(10), dp(10), dp(10))
                    }, LinearLayout.LayoutParams(-1, -2))
                }
                val ready = admin && a11y
                col.addView(btn("🔒 ৩০ দিনের জন্য লক করুন", if (ready) Color.parseColor("#C62828") else Color.GRAY) {
                    if (!ready) Toast.makeText(this, "আগে Device Admin ও Accessibility চালু করুন", Toast.LENGTH_LONG).show()
                    else confirmLock()
                })
            }
        }
        setContentView(scroll)
    }

    private fun confirmLock() {
        AlertDialog.Builder(this)
            .setTitle("নিশ্চিত?")
            .setMessage("লক করলে ৩০ দিন কোনোভাবেই খোলা যাবে না। অ্যাপ আনইন্সটল, DNS সেটিংস ও সুরক্ষা সেটিংস বন্ধ থাকবে। মেয়াদ শেষে অ্যালার্ম বাজবে।")
            .setNegativeButton("না", null)
            .setPositiveButton("লক করুন") { _, _ ->
                ExpiryAlarm.cancel(this)
                LockStore.start(this, 30)
                Policy.applyLock(this)
                ExpiryAlarm.schedule(this, LockStore.remainingMs(this))
                build()
            }.show()
    }

    private fun updateCountdown() {
        val ms = LockStore.remainingMs(this)
        val s = ms / 1000
        val d = s / 86400
        val t = "%02d:%02d:%02d".format((s % 86400) / 3600, (s % 3600) / 60, s % 60)
        ring?.set(ms.toFloat() / (30 * LockStore.DAY_MS), bn(d.toString()), bn(t))
    }
}
