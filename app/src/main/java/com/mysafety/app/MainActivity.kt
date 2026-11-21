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
import android.os.PowerManager
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.core.content.res.ResourcesCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private companion object {
        const val DNS_HOST = "high.kahfguard.com"
        const val TG_URL = "https://t.me/rishadhossainmahib"
    }

    /**
     * একবার OTP মিললে এটা ফোনে সেভ হয়ে যায় (SharedPreferences), তাই অ্যাপ বন্ধ করে আবার খুললেও
     * ফের OTP চাইবে না। শুধু অ্যাপের ডেটা Clear করলে বা আনইন্সটল করলে এই সেভ মুছে যাবে।
     */
    private fun gateSp() = getSharedPreferences("my_safety_gate", MODE_PRIVATE)
    private fun isGatePassed() = gateSp().getBoolean("passed", false)
    private fun setGatePassed(v: Boolean) = gateSp().edit().putBoolean("passed", v).apply()

    private val h = Handler(Looper.getMainLooper())
    private var ring: CountdownView? = null
    private var slotView: TextView? = null
    private var lastState = ""

    /**
     * Siliguri বাংলা ফন্ট। app/src/main/res/font/ এ hind_siliguri.ttf (ও চাইলে hind_siliguri_bold.ttf)
     * রাখলেই এটি স্বয়ংক্রিয়ভাবে পাওয়া যাবে — ফাইল না থাকলে অ্যাপ স্বাভাবিক ডিফল্ট ফন্টেই চলবে, ক্র্যাশ করবে না।
     */
    private val siliguriFont: Typeface? by lazy { loadFont("hind_siliguri") }
    private val siliguriBold: Typeface? by lazy { loadFont("hind_siliguri_bold") }

    private fun loadFont(name: String): Typeface? = try {
        val id = resources.getIdentifier(name, "font", packageName)
        if (id != 0) ResourcesCompat.getFont(this, id) else null
    } catch (_: Throwable) { null }

    private fun fontFor(bold: Boolean): Typeface? = when {
        bold && siliguriBold != null -> siliguriBold
        bold && siliguriFont != null -> Typeface.create(siliguriFont, Typeface.BOLD)
        else -> siliguriFont
    }

    /** টাইপরাইটারের মতো অক্ষরে অক্ষরে টেক্সট ফুটিয়ে তোলে। */
    private fun typeText(view: TextView, full: String, delayMs: Long = 45L) {
        view.text = ""
        var i = 0
        val r = object : Runnable {
            override fun run() {
                if (view.isAttachedToWindow && i <= full.length) {
                    view.text = full.substring(0, i)
                    i++
                    if (i <= full.length) h.postDelayed(this, delayMs)
                }
            }
        }
        h.post(r)
    }

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

    private fun batteryOk() =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun askBattery() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:$packageName")))
        } catch (_: Throwable) {
            try { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            catch (_: Throwable) {
                Toast.makeText(this, "Settings → Apps → My Safety → Battery → Unrestricted করুন", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun stateKey() = "${LockStore.isLocked(this)}|${LockStore.isExpired(this)}|" +
        "${Policy.isAdminActive(this)}|${Policy.isAccessibilityOn(this)}|${notifOn()}|${batteryOk()}"

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
            typeface = fontFor(bold) ?: Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
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
        text = t; textSize = 12.5f; setTextColor(gray)
        typeface = fontFor(true) ?: Typeface.create(typeface, Typeface.BOLD)
        letterSpacing = 0.04f; setPadding(dp(6), dp(8), 0, dp(8))
    }

    /** ১,২,৩ নম্বরের বদলে সবুজ ডট আর সংযোগ-রেখা দিয়ে ধাপে ধাপে নির্দেশনা দেখায় (উপর থেকে নিচে)। */
    private fun stepper(steps: List<String>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        steps.forEachIndexed { i, text ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(FrameLayout(context).apply {
                    if (i < steps.lastIndex) {
                        addView(View(context).apply { setBackgroundColor(Color.parseColor("#BFE6CE")) },
                            FrameLayout.LayoutParams(dp(2), -1, Gravity.CENTER_HORIZONTAL))
                    }
                    addView(View(context).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL; setColor(green); setStroke(dp(2), white)
                        }
                    }, FrameLayout.LayoutParams(dp(17), dp(17), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(2) })
                }, LinearLayout.LayoutParams(dp(26), -1))
                addView(TextView(context).apply {
                    this.text = text; textSize = 13f; setTextColor(gray); setLineSpacing(0f, 1.2f)
                    typeface = fontFor(false) ?: typeface
                    setPadding(dp(10), 0, 0, if (i < steps.lastIndex) dp(16) else 0)
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }, LinearLayout.LayoutParams(-1, -2))
        }
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
            addView(TextView(context).apply {
                text = title; textSize = 16f; setTextColor(dark)
                typeface = fontFor(true) ?: Typeface.create(typeface, Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = sub; textSize = 12.5f; setTextColor(gray); setPadding(0, dp(2), 0, 0)
                typeface = fontFor(false) ?: typeface
            })
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
        this.text = text; textSize = 12f
        typeface = fontFor(true) ?: Typeface.create(typeface, Typeface.BOLD)
        setTextColor(if (on) green else gray)
        background = GradientDrawable().apply {
            setColor(if (on) Color.parseColor("#DDF3E4") else Color.parseColor("#ECEFED")); cornerRadius = dp(14).toFloat()
        }
        setPadding(dp(12), dp(5), dp(12), dp(5))
    }

    private fun button(text: String, res: Int, bg: Int, matchWidth: Boolean = true, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; isClickable = true
        background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(16).toFloat() }
        if (!matchWidth) setPadding(dp(22), 0, dp(22), 0)
        addView(icon(res, white, 20))
        addView(TextView(context).apply {
            this.text = text; textSize = 16f; setTextColor(white)
            typeface = fontFor(true) ?: Typeface.create(typeface, Typeface.BOLD)
            setPadding(dp(10), 0, 0, 0)
        })
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(if (matchWidth) -1 else -2, dp(54)).apply { topMargin = dp(12) }
    }

    private fun chip(ok: Boolean, label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(7), dp(14), dp(7))
        background = GradientDrawable().apply {
            setColor(if (ok) Color.parseColor("#33FFFFFF") else Color.parseColor("#88C62828")); cornerRadius = dp(20).toFloat()
        }
        addView(icon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_error, white, 16))
        addView(TextView(context).apply {
            text = label; textSize = 13f; setTextColor(white); setPadding(dp(6), 0, 0, 0)
            typeface = fontFor(true) ?: Typeface.create(typeface, Typeface.BOLD)
        })
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    }

    private fun openTelegram() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(TG_URL)))
        } catch (_: Throwable) {
            Toast.makeText(this, "টেলিগ্রাম বা ব্রাউজার খোলা যায়নি", Toast.LENGTH_LONG).show()
        }
    }

    /** কাস্টমার সাপোর্ট / মেসেজ আইকন (উপরের ডানে)। */
    private fun supportIcon(tint: Int, bg: Int) = FrameLayout(this).apply {
        background = GradientDrawable().apply { setColor(bg); shape = GradientDrawable.OVAL }
        isClickable = true
        contentDescription = "Customer support"
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_message); setColorFilter(tint) },
            FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        setOnClickListener { openTelegram() }
    }

    /** পেজের উপরে ডানদিকে সাপোর্ট আইকন বসায়। */
    private fun withSupport(content: View, tint: Int, bg: Int) = FrameLayout(this).apply {
        addView(content, FrameLayout.LayoutParams(-1, -1))
        addView(supportIcon(tint, bg), FrameLayout.LayoutParams(dp(46), dp(46), Gravity.TOP or Gravity.END).apply {
            topMargin = dp(12); rightMargin = dp(14)
        })
    }

    // ---------------------------------------------------------------- screens
    private fun build() {
        lastState = stateKey(); ring = null; slotView = null
        val locked = LockStore.isLocked(this)
        val expired = LockStore.isExpired(this)
        val scroll = ScrollView(this).apply { isFillViewport = true; overScrollMode = View.OVER_SCROLL_NEVER }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)
        window.statusBarColor = dark
        val showGate = !locked && !isGatePassed()
        when {
            locked -> buildLocked(scroll, root)
            showGate -> buildGate(scroll, root)
            expired -> buildExpired(scroll, root)
            else -> buildSetup(scroll, root)
        }
        if (showGate) {
            setContentView(scroll)
        } else {
            val light = !locked
            setContentView(withSupport(scroll,
                if (light) green else white,
                if (light) Color.parseColor("#E3F2E8") else Color.parseColor("#33FFFFFF")))
        }
    }

    /** অ্যাপ খুললেই প্রথমে এই ভেরিফাই-স্ক্রিন দেখায়; কোড মিললে তবেই মূল/ফিচার পেইজে যাওয়া যায়। */
    /** ৬টি আলাদা OTP বক্স বানায়: চিকন সবুজ বর্ডার, টাইপ করলেই পরের বক্সে ফোকাস যায়। */
    private fun otpBoxes(row: LinearLayout): List<EditText> {
        val boxes = mutableListOf<EditText>()
        for (i in 0 until 6) {
            val box = EditText(this).apply {
                textSize = 20f; gravity = Gravity.CENTER; isSingleLine = true
                inputType = InputType.TYPE_CLASS_NUMBER
                setTextColor(dark)
                typeface = fontFor(true) ?: Typeface.create(typeface, Typeface.BOLD)
                background = GradientDrawable().apply {
                    setColor(white); cornerRadius = dp(10).toFloat(); setStroke(dp(1), green)
                }
            }
            row.addView(box, LinearLayout.LayoutParams(0, dp(52), 1f).apply { if (i > 0) marginStart = dp(8) })
            boxes += box
        }

        // setText() দিয়ে প্রোগ্রাম্যাটিকভাবে বসানো টেক্সট যেন আবার এই লিসেনারকে ট্রিগার না করে
        var programmatic = false
        fun setBoxText(box: EditText, value: String) {
            programmatic = true
            box.setText(value)
            box.setSelection(value.length)
            programmatic = false
        }

        boxes.forEachIndexed { i, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (programmatic) return
                    val digits = s?.toString().orEmpty().filter { it.isDigit() }
                    when {
                        // একসাথে একাধিক সংখ্যা এলে (ক্লিপবোর্ড থেকে পেস্ট) — বাকি বক্সগুলোতে ছড়িয়ে দাও
                        digits.length > 1 -> {
                            var idx = i
                            for (ch in digits) {
                                if (idx > boxes.lastIndex) break
                                setBoxText(boxes[idx], ch.toString())
                                idx++
                            }
                            boxes[(i + digits.length).coerceAtMost(boxes.lastIndex)].requestFocus()
                        }
                        digits.isNotEmpty() -> {
                            if (digits != s?.toString()) setBoxText(box, digits)
                            if (i < boxes.lastIndex) boxes[i + 1].requestFocus()
                        }
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
            box.setOnKeyListener { _, keyCode, e ->
                if (keyCode == KeyEvent.KEYCODE_DEL && e.action == KeyEvent.ACTION_DOWN && box.text.isEmpty() && i > 0) {
                    boxes[i - 1].text?.clear(); boxes[i - 1].requestFocus()
                }
                false
            }
        }
        return boxes
    }

    /** অ্যাপ খুললেই প্রথমে এই সাদা-থিমের ভেরিফাই/OTP স্ক্রিন দেখায়। */
    private fun buildGate(scroll: ScrollView, root: LinearLayout) {
        scroll.setBackgroundColor(white)
        root.gravity = Gravity.CENTER; root.setPadding(dp(24), dp(16), dp(24), dp(48))
        window.navigationBarColor = white
        window.statusBarColor = white

        root.addView(ShieldPulseView(this, lightBg = true), LinearLayout.LayoutParams(dp(190), dp(190)).apply { bottomMargin = dp(14) })

        val heading = tv("", 19f, dark, true, true)
        root.addView(heading)
        typeText(heading, "Please confirm your verified user")

        val otpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        root.addView(otpRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        val boxes = otpBoxes(otpRow)

        root.addView(button("Verify Access", R.drawable.ic_verified, green, matchWidth = false) {
            val code = boxes.joinToString("") { it.text.toString() }
            if (code.length != 6) {
                Toast.makeText(this, "৬ সংখ্যার কোড দিন", Toast.LENGTH_SHORT).show()
                return@button
            }
            when (UnlockCodes.verify(this, code)) {
                UnlockCodes.Result.OK -> { setGatePassed(true); build() }
                UnlockCodes.Result.WRONG -> {
                    Toast.makeText(this, "ভুল কোড। আর ${bn(UnlockCodes.failsLeft(this).toString())} বার চেষ্টা করা যাবে।", Toast.LENGTH_LONG).show()
                    boxes.forEach { it.text?.clear() }; boxes.first().requestFocus()
                }
                UnlockCodes.Result.CLOCK_CHANGED ->
                    Toast.makeText(this, "ফোনের ঘড়ি বদলানো শনাক্ত হয়েছে। সময় Automatic করে আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show()
                UnlockCodes.Result.COOLDOWN -> {
                    val m = (UnlockCodes.cooldownLeftMs(this) + 59_999) / 60_000
                    Toast.makeText(this, "অনেকবার ভুল হয়েছে। ${bn(m.toString())} মিনিট পরে চেষ্টা করুন।", Toast.LENGTH_LONG).show()
                }
            }
        })

        root.addView(tv("Contact admin on Telegram for OTP", 11.5f, gray, false, true),
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(26) })
        root.addView(button("Contact on Telegram", R.drawable.ic_telegram, green, matchWidth = false) { openTelegram() })
    }

    private fun buildSetup(scroll: ScrollView, root: LinearLayout) {
        val admin = Policy.isAdminActive(this)
        val a11y = Policy.isAccessibilityOn(this)
        val notif = notifOn()
        val batt = batteryOk()
        val done = listOf(admin, a11y, notif, batt).count { it }
        val ready = admin && a11y && batt

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
                text = "${bn("$done")}/${bn("4")} ধাপ সম্পন্ন"; textSize = 13f; setTextColor(white); setPadding(dp(6), 0, 0, 0)
                typeface = fontFor(false) ?: typeface
            })
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(12) })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
        root.addView(body, LinearLayout.LayoutParams(-1, -2))

        // DNS কুইক-কপি কার্ড (পারমিশনের উপরে): DNS আইকন + অ্যাড্রেস + কপি বাটন
        val dnsQuick = card()
        dnsQuick.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
            addView(badge(R.drawable.ic_dns))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(10), 0)
                addView(TextView(context).apply {
                    text = "Private DNS"; textSize = 12f; setTextColor(gray)
                    typeface = fontFor(false) ?: typeface
                })
                addView(TextView(context).apply {
                    text = DNS_HOST; textSize = 15.5f; typeface = Typeface.MONOSPACE
                    setTypeface(typeface, Typeface.BOLD); setTextColor(dark); setTextIsSelectable(true)
                    setPadding(0, dp(2), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(FrameLayout(context).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor("#E3F2E8")); cornerRadius = dp(14).toFloat() }
                isClickable = true
                contentDescription = "Copy DNS"
                addView(ImageView(context).apply { setImageResource(R.drawable.ic_copy); setColorFilter(green) },
                    FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("dns", DNS_HOST))
                    Toast.makeText(context, "কপি হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
        })
        dnsQuick.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(12) })
        dnsQuick.addView(stepper(listOf(
            "প্রথমে DNS Address টি কপি করুন (উপরের কপি বাটনে চাপ দিন)",
            "ফোনের Settings খুলুন",
            "Network & internet এ যান (Samsung: Connections → More connection settings, Xiaomi: Connection & sharing)। সহজ উপায়: সার্চে \"Private DNS\" লিখুন",
            "Private DNS চাপুন",
            "\"Private DNS provider hostname\" বেছে নিন",
            "বক্সে কপি করা অ্যাড্রেসটি পেস্ট করুন",
            "Save চাপুন — \"Connected\" দেখালে চালু হয়েছে",
            "এরপর Accessibility গার্ড চালু করুন: Settings → Accessibility (বা Installed apps) → My Safety খুঁজে বের করে চালু করুন। Android 13+ এ অপশন ধূসর থাকলে: অ্যাপ ইনফো → ⋮ → Allow restricted settings চেপে আবার চেষ্টা করুন",
            "সব সেট সম্পন্ন হলে এখন নিচের \"প্রয়োজনীয় পারমিশন\" ধাপে যান"
        )))
        dnsQuick.addView(TextView(this).apply {
            text = "⚠ লক শুরু করার আগেই এটি সেট করুন। লক চালু থাকলে DNS সেটিংস খোলা যাবে না।"
            textSize = 12f; setTextColor(gray); setLineSpacing(0f, 1.2f)
            typeface = fontFor(false) ?: typeface
            setPadding(0, dp(6), 0, dp(4))
        })
        body.addView(dnsQuick)

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
            Toast.makeText(this, "Installed apps → My Safety → চালু/বন্ধ করুন", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }))
        perm.addView(divider())
        perm.addView(row(R.drawable.ic_notifications, "নোটিফিকেশন", "মেয়াদ শেষের অ্যালার্মের জন্য", toggle(notif) { want ->
            if (want && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
            } else startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }))
        perm.addView(divider())
        perm.addView(row(R.drawable.ic_battery, "ব্যাটারি: Unrestricted", "গার্ড যেন সবসময় চালু থাকে", toggle(batt) { want ->
            if (want) askBattery()
            else startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }))
        body.addView(perm)

        // লক কার্ড (DNS কুইক-কপি কার্ডের স্টাইলে: হেডার রো + মাঝে অ্যানিমেশন ব্যাজ + নিচে ধাপে ধাপে নির্দেশনা)
        body.addView(section("লক"))
        val lock = card()
        lock.addView(row(R.drawable.ic_lock, "৩০ দিনের লক", "শুরু করলে শুধু সঠিক কোড দিয়ে খোলা যাবে",
            pill(if (ready) "প্রস্তুত" else "অপ্রস্তুত", ready)))
        lock.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(LockKeyAnimView(this@MainActivity), LinearLayout.LayoutParams(dp(104), dp(104)))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(8) })
        lock.addView(divider(), LinearLayout.LayoutParams(-1, dp(1)).apply { bottomMargin = dp(14) })
        lock.addView(stepper(listOf(
            "লক শুরু করলে ডিভাইস ৩০ দিনের জন্য লক হয়ে যাবে",
            "আনইন্সটল, DNS সেটিংস ও সুরক্ষা সেটিংস বন্ধ থাকবে",
            "মেয়াদের আগে খুলতে নির্ধারিত সময়ের ভেরিফাই কোড লাগবে",
            "মেয়াদ শেষ হলে অ্যালার্ম বাজবে এবং নিজে থেকেই খুলে যাবে"
        )))
        lock.addView(button("লক শুরু করুন", R.drawable.ic_lock, if (ready) green else Color.parseColor("#B7C4BC")) {
            if (!ready) Toast.makeText(this, "আগে Device Admin, Accessibility ও ব্যাটারি Unrestricted চালু করুন", Toast.LENGTH_LONG).show()
            else confirmLock()
        })
        if (!ready) lock.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(6))
            addView(icon(R.drawable.ic_info, gray, 16))
            addView(TextView(context).apply {
                text = "লক করতে Device Admin, Accessibility ও ব্যাটারি Unrestricted চালু থাকতে হবে"; textSize = 12f; setTextColor(gray); setPadding(dp(8), 0, 0, 0)
                typeface = fontFor(false) ?: typeface
            })
        })
        body.addView(lock)
    }

    private fun buildLocked(scroll: ScrollView, root: LinearLayout) {
        val admin = Policy.isAdminActive(this)
        val a11y = Policy.isAccessibilityOn(this)
        window.navigationBarColor = green
        scroll.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(dark, green))
        root.gravity = Gravity.CENTER; root.setPadding(dp(24), dp(28), dp(24), dp(28))

        root.addView(tv("ব্রাউজার সুরক্ষা সক্রিয় আছে", 21f, white, true, true))
        root.addView(tv("My Safety আপনার ডিভাইস ও ব্রাউজার সুরক্ষিত রাখছে", 13.5f, Color.parseColor("#CCFFFFFF"), false, true))

        val r = CountdownView(this); ring = r
        root.addView(r, LinearLayout.LayoutParams(dp(260), dp(260)).apply { topMargin = dp(18); bottomMargin = dp(10) })
        updateCountdown()

        val endText = SimpleDateFormat("d MMMM yyyy, h:mm a", Locale("bn", "BD"))
            .format(Date(System.currentTimeMillis() + LockStore.remainingMs(this)))
        root.addView(tv("শেষ হবে", 13f, Color.parseColor("#99FFFFFF"), false, true))
        root.addView(tv(bn(endText), 16f, white, true, true))
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0)
            addView(icon(R.drawable.ic_notifications, Color.parseColor("#9BFFC5"), 16))
            addView(TextView(context).apply {
                text = "মেয়াদ শেষে অ্যালার্ম বাজবে (অফলাইনেও)"; textSize = 13f; setTextColor(Color.parseColor("#9BFFC5")); setPadding(dp(6), 0, 0, 0)
                typeface = fontFor(false) ?: typeface
            })
        })
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER
            addView(chip(admin, "Admin")); addView(chip(a11y, "গার্ড"))
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })

        // Only For Emergency unlock কার্ড: টাইটেল + ৬টি বক্স + Unlock + Telegram লিংক
        val u = card()
        u.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20); bottomMargin = dp(10) }
        u.setPadding(dp(16), dp(20), dp(16), dp(18))
        u.addView(tv("Only For Emergency unlock", 22f, green, true, true))
        val otpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        u.addView(otpRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(4) })
        val boxes = otpBoxes(otpRow)
        u.addView(button("Unlock", R.drawable.ic_lock_open, green, matchWidth = false) {
            val code = boxes.joinToString("") { it.text.toString() }
            if (code.length != 6) {
                Toast.makeText(this, "৬ সংখ্যার কোড দিন", Toast.LENGTH_SHORT).show()
                return@button
            }
            when (UnlockCodes.verify(this, code)) {
                UnlockCodes.Result.OK -> {
                    ExpiryAlarm.cancel(this); LockStore.clear(this); setGatePassed(true)
                    Toast.makeText(this, "আনলক হয়েছে", Toast.LENGTH_LONG).show(); build()
                }
                UnlockCodes.Result.WRONG -> {
                    Toast.makeText(this, "ভুল কোড। আর ${bn(UnlockCodes.failsLeft(this).toString())} বার চেষ্টা করা যাবে।", Toast.LENGTH_LONG).show()
                    boxes.forEach { it.text?.clear() }; boxes.first().requestFocus()
                }
                UnlockCodes.Result.CLOCK_CHANGED ->
                    Toast.makeText(this, "ফোনের ঘড়ি বদলানো শনাক্ত হয়েছে। সময় Automatic করে আবার চেষ্টা করুন।", Toast.LENGTH_LONG).show()
                UnlockCodes.Result.COOLDOWN -> {
                    val m = (UnlockCodes.cooldownLeftMs(this) + 59_999) / 60_000
                    Toast.makeText(this, "অনেকবার ভুল হয়েছে। ${bn(m.toString())} মিনিট পরে চেষ্টা করুন।", Toast.LENGTH_LONG).show()
                }
            }
        }.also { (it.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL })
        u.addView(tv("Contact Admin on Telegram For OTP", 14f, gray, false, true).apply {
            setPadding(0, dp(14), 0, dp(2))
        })
        root.addView(u)
        updateCountdown()
        root.addView(tv("কোড ছাড়া আনইন্সটল ও DNS সেটিংস খোলা যাবে না।", 12f,
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
            .setMessage("লক করলে ৩০ দিন অ্যাপ আনইন্সটল, DNS সেটিংস ও সুরক্ষা সেটিংস বন্ধ থাকবে। মেয়াদের আগে শুধু নির্ধারিত সময়ের কোড দিয়ে খোলা যাবে। মেয়াদ শেষে অ্যালার্ম বাজবে। আপনি কি Private DNS সেট করে নিয়েছেন?$extra")
            .setNegativeButton("না", null)
            .setPositiveButton("লক করুন") { _, _ ->
                ExpiryAlarm.cancel(this)
                LockStore.start(this, 30)
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
        slotView?.text = "এখনকার সময়-স্লট (ঢাকা): " + bn(UnlockCodes.currentLabel())
    }
}
