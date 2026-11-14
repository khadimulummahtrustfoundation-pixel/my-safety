package com.mysafety.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
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
    private val gray = Color.parseColor("#6B7B71")
    private val pageBg = Color.parseColor("#F4FAF6")
    private val white = Color.WHITE

    private val ticker = object : Runnable {
        override fun run() {
            if (stateKey() != lastState) build() else updateCountdown()
            h.postDelayed(this, 1000)
        }
    }

    private fun notifOn() =
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()

    private fun stateKey() = "${LockStore.isLocked(this)}|${LockStore.isExpired(this)}|" +
        "${Policy.isAdminActive(this)}|${Policy.isAccessibilityOn(this)}|${Policy.isDeviceOwner(this)}|${notifOn()}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(code, p, r); build()
    }

    override fun onResume() {
        super.onResume()
        if (LockStore.isLocked(this)) Policy.applyLock(this)
        build()
        h.postDelayed(ticker, 1000)
    }

    override fun onPause() { super.onPause(); h.removeCallbacks(ticker) }

    // ---------------------------------------------------------------- helpers
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bn(s: String) = s.map { if (it in '0'..'9') ('\u09E6' + (it - '0')) else it }.joinToString("")

    private fun tv(t: String, size: Float, color: Int = dark, bold: Boolean = false, center: Boolean = false) =
        TextView(this).apply {
            text = t; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            if (center) gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        }

    private fun icon(res: Int, tint: Int, size: Int) = ImageView(this).apply {
        setImageResource(res); setColorFilter(tint)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    private fun badge(res: Int) = FrameLayout(this).apply {
        background = GradientDrawable().apply { setColor(Color.parseColor("#E3F2E8")); cornerRadius = dp(14).toFloat() }
        addView(ImageView(context).apply { setImageResource(res); setColorFilter(green) },
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            setColor(white); cornerRadius = dp(20).toFloat(); setStroke(dp(1), Color.parseColor("#E2EEE6"))
        }
        elevation = dp(2).toFloat()
        setPadding(dp(16), dp(8), dp(16), dp(8))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
    }

    private fun section(t: String) = TextView(this).apply {
        text = t; textSize = 12.5f; setTextColor(gray); setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.04f; setPadding(dp(6), dp(8), 0, dp(8))
    }

    private fun divider() = View(this).apply {
        setBackgroundColor(Color.parseColor("#EEF3EF"))
        layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(58) }
    }

    private fun row(res: Int, title: String, sub: String, trailing: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(10))
        addView(badge(res))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(10), 0)
            addView(TextView(context).apply { text = title; textSize = 16f; setTextColor(dark); setTypeface(typeface, Typeface.BOLD) })
            addView(TextView(context).apply { text = sub; textSize = 12.5f; setTextColor(gray); setPadding(0, dp(2), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(trailing)
    }

    private fun toggle(checked: Boolean, onClick: (Boolean) -> Unit) = Switch(this).apply {
        isChecked = checked
        val st = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(st, intArrayOf(white, Color.parseColor("#F5F5F5")))
        trackTintList = ColorStateList(st, intArrayOf(green, Color.parseColor("#C5CEC8")))
        setOnClickListener { onClick(isChecked) }
    }

    private fun pill(text: String, on: Boolean) = TextView(this).apply {
        this.text = text; textSize = 12f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(if (on) green else gray)
        background = GradientDrawable().apply {
            setColor(if (on) Color.parseColor("#DDF3E4") else Color.parseColor("#ECEFED")); cornerRadius = dp(14).toFloat()
        }
        setPadding(dp(12), dp(5), dp(12), dp(5))
    }

    private fun button(text: String, res: Int, bg: Int, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; isClickable = true
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(16).toFloat() }
        addView(icon(res, white, 20))
        addView(TextView(context).apply {
            this.text = text; textSize = 16f; setTextColor(white); setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), 0, 0, 0)
        })
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) }
    }

    private fun chip(ok: Boolean, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(7), dp(14), dp(7))
        background = GradientDrawable().apply {
            setColor(if (ok) Color.parseColor("#33FFFFFF") else Color.parseColor("#88C62828")); cornerRadius = dp(20).toFloat()
        }
        addView(icon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error, white, 16))
        addView(TextView(context).apply { text = label; textSize = 13f; setTextColor(white); setPadding(dp(6), 0, 0, 0) })
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    // ---------------------------------------------------------------- screens
    private fun build() {
        lastState = stateKey(); ring = null
        val locked = LockStore.isLocked(this)
        val expired = LockStore.isExpired(this)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)
        window.statusBarColor = dark
        when {
            locked -> buildLocked(scroll, root)
            expired -> buildExpired(scroll, root)
            else -> buildSetup(scroll, root)
        }
        setContentView(scroll)
    }

    private fun buildSetup(scroll: ScrollView, root: LinearLayout) {
        val admin = Policy.isAdminActive(this)
        val a11y = Policy.isAccessibilityOn(this)
        val owner = Policy.isDeviceOwner(this)
        val notif = notifOn()
        val done = listOf(admin, a11y, notif).count { it }
        val ready = admin && a11y

        scroll.setBackgroundColor(pageBg); window.navigationBarColor = pageBg

        // হেডার
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(20), dp(24), dp(26))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(dark, green)).apply {
                val r = dp(28).toFloat(); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
            }
        }
        header.addView(ImageView(this).apply { setImageResource(R.drawable.ic_logo_inverse) }, LinearLayout.LayoutParams(dp(72), dp(72)))
        header.addView(tv("My Safety", 26f, white, true, true))
        header.addView(tv("আপনার ডিভাইসের সুরক্ষা, আপনার নিয়ন্ত্রণে", 13f, Color.parseColor("#CCFFFFFF"), false, true))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(14), dp(6))
            background = GradientDrawable().apply { setColor(Color.parseColor("#26FFFFFF")); cornerRadius = dp(20).toFloat() }
            addView(icon(R.drawable.ic_verified, Color.parseColor("#9BFFC5"), 16))
            addView(TextView(context).apply {
                text = "${bn("$done")}/${bn("3")} ধাপ সম্পন্ন"; textSize = 13f; setTextColor(white); setPadding(dp(6), 0, 0, 0)
            })
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        root.addView(body, LinearLayout.LayoutParams(-1, -2))

        // পারমিশন কার্ড
        body.addView(section("প্রয়োজনীয় পারমিশন"))
        val perm = card()
        perm.addView(row(R.drawable.ic_verified, "Device Admin", "আনইন্সটল ঠেকাতে প্রয়োজন", toggle(admin) { want ->
            if (want) startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Policy.admin(this@MainActivity))
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "লক চালু থাকলে আনইন্সটল ঠেকাতে এটি দরকার।")
            }) else {
                try { (getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager).removeActiveAdmin(Policy.admin(this)) } catch (_: Throwable) { }
                build()
            }
        }))
        perm.addView(divider())
        perm.addView(row(R.drawable.ic_accessibility, "Accessibility গার্ড", "DNS ও সেটিংস ব্লক করতে প্রয়োজন", toggle(a11y) { want ->
            if (want && owner) { Policy.ensureAccessibility(this); build() }
            else {
                Toast.makeText(this, "Installed apps → My Safety → চালু/বন্ধ করুন", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }))
        perm.addView(divider())
        perm.addView(row(R.drawable.ic_notifications, "নোটিফিকেশন", "মেয়াদ শেষের অ্যালার্মের জন্য", toggle(notif) { want ->
            if (want && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            } else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }))
        body.addView(perm)

        // উন্নত সুরক্ষা
        body.addView(section("উন্নত সুরক্ষা"))
        val adv = card()
        adv.addView(row(R.drawable.ic_phone, "Device Owner", "সবচেয়ে শক্তিশালী মোড (ঐচ্ছিক)",
            pill(if (owner) "সক্রিয়" else "বন্ধ", owner)))
        if (!owner) {
            adv.addView(tv("কম্পিউটার থেকে একবার চালান (USB debugging চালু করে):", 12f, gray))
            val cmd = "adb shell dpm set-device-owner com.mysafety.app/.MyDeviceAdminReceiver"
            adv.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(8), dp(12))
                background = GradientDrawable().apply { setColor(Color.parseColor("#0F1F16")); cornerRadius = dp(12).toFloat() }
                addView(TextView(context).apply {
                    text = cmd; textSize = 11.5f; typeface = Typeface.MONOSPACE
                    setTextColor(Color.parseColor("#9BFFC5")); setTextIsSelectable(true)
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_copy); setColorFilter(Color.parseColor("#9BFFC5"))
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    setOnClickListener {
                        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("cmd", cmd))
                        Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                    }
                }, LinearLayout.LayoutParams(dp(44), dp(44)))
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        body.addView(adv)

        // লক কার্ড
        body.addView(section("লক"))
        val lock = card()
        lock.addView(row(R.drawable.ic_lock, "৩০ দিনের লক", "শুরু করলে কোনোভাবেই খোলা যাবে না", View(this)))
        lock.addView(tv("আনইন্সটল, DNS সেটিংস ও সুরক্ষা সেটিংস ৩০ দিন বন্ধ থাকবে। মেয়াদ শেষে অ্যালার্ম বাজবে।", 13f, gray))
        lock.addView(button("লক শুরু করুন", R.drawable.ic_lock, if (ready) green else Color.parseColor("#B7C4BC")) {
            if (!ready) Toast.makeText(this, "আগে Device Admin ও Accessibility চালু করুন", Toast.LENGTH_LONG).show()
            else confirmLock()
        })
        if (!ready) lock.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(6))
            addView(icon(R.drawable.ic_info, gray, 16))
            addView(TextView(context).apply { text = "লক করতে Device Admin ও Accessibility দুটোই চালু থাকতে হবে"; textSize = 12f; setTextColor(gray); setPadding(dp(8), 0, 0, 0) })
        })
        body.addView(lock)
    }

    private fun buildLocked(scroll: ScrollView, root: LinearLayout) {
        val admin = Policy.isAdminActive(this)
        val a11y = Policy.isAccessibilityOn(this)
        val owner = Policy.isDeviceOwner(this)
        window.navigationBarColor = green
        scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(dark, green))
        root.gravity = Gravity.CENTER; root.setPadding(dp(24), dp(32), dp(24), dp(32))

        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(icon(R.drawable.ic_lock, white, 26))
            addView(TextView(context).apply {
                text = "লক চালু আছে"; textSize = 22f; setTextColor(white); setTypeface(typeface, Typeface.BOLD); setPadding(dp(10), 0, 0, 0)
            })
        })
        root.addView(tv("My Safety আপনাকে সুরক্ষিত রাখছে", 14f, Color.parseColor("#CCFFFFFF"), false, true))

        val r = CountdownView(this); ring = r
        root.addView(r, LinearLayout.LayoutParams(dp(300), dp(300)).apply { topMargin = dp(20); bottomMargin = dp(12) })
        updateCountdown()

        val endText = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale("bn", "BD"))
            .format(Date(System.currentTimeMillis() + LockStore.remainingMs(this)))
        root.addView(tv("শেষ হবে", 13f, Color.parseColor("#99FFFFFF"), false, true))
        root.addView(tv(bn(endText), 16f, white, true, true))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0)
            addView(icon(R.drawable.ic_notifications, Color.parseColor("#9BFFC5"), 16))
            addView(TextView(context).apply { text = "মেয়াদ শেষে অ্যালার্ম বাজবে (অফলাইনেও)"; textSize = 13f; setTextColor(Color.parseColor("#9BFFC5")); setPadding(dp(6), 0, 0, 0) })
        })
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(chip(admin, "Admin")); addView(chip(a11y, "গার্ড")); addView(chip(owner, "Owner"))
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
        root.addView(tv("এই সময়ে আনইন্সটল ও DNS সেটিংস খোলা যাবে না। কোনো আনলক অপশন নেই।", 12f,
            Color.parseColor("#B3FFFFFF"), false, true))
    }

    private fun buildExpired(scroll: ScrollView, root: LinearLayout) {
        scroll.setBackgroundColor(pageBg); window.navigationBarColor = pageBg
        root.gravity = Gravity.CENTER; root.setPadding(dp(24), dp(32), dp(24), dp(32))
        root.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(Color.parseColor("#DDF3E4")); shape = GradientDrawable.OVAL }
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_check_circle); setColorFilter(green) },
                FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(120), dp(120)))
        root.addView(tv("৩০ দিন সম্পন্ন!", 26f, green, true, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        root.addView(tv("অভিনন্দন! এখন চাইলে সব বন্ধন ছেড়ে দিতে পারেন। এরপর অ্যাপ আনইন্সটল করা যাবে।", 15f, gray, false, true))
        root.addView(button("মুক্ত করুন", R.drawable.ic_lock_open, green) {
            ExpiryAlarm.cancel(this); Policy.release(this); LockStore.clear(this)
            Toast.makeText(this, "মুক্ত করা হয়েছে", Toast.LENGTH_LONG).show(); build()
        })
        root.addView(button("আবার ৩০ দিন লক করুন", R.drawable.ic_lock, dark) { confirmLock() })
    }

    // ---------------------------------------------------------------- actions
    private fun confirmLock() {
        val extra = if (!notifOn()) "\n\nসতর্কতা: নোটিফিকেশন বন্ধ আছে, তাই মেয়াদ শেষের অ্যালার্ম দেখা যাবে না।" else ""
        AlertDialog.Builder(this)
            .setTitle("নিশ্চিত?")
            .setMessage("লক করলে ৩০ দিন কোনোভাবেই খোলা যাবে না। অ্যাপ আনইন্সটল, DNS সেটিংস ও সুরক্ষা সেটিংস বন্ধ থাকবে। মেয়াদ শেষে অ্যালার্ম বাজবে।$extra")
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
