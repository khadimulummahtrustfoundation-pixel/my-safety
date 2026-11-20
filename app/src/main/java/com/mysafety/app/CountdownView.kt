package com.mysafety.app

import android.content.Context
import android.graphics.*
import android.view.View

/** বড় রিং-কাউন্টডাউন: মাঝে দিন সংখ্যা, নিচে ঘণ্টা:মিনিট:সেকেন্ড */
class CountdownView(ctx: Context) : View(ctx) {
    var fraction = 1f
    var days = "০"
    var time = "০০:০০:০০"
    var label = "দিন বাকি"

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#33FFFFFF")
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#337CFFB2")
    }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCFFFFFF"); textAlign = Paint.Align.CENTER
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9BFFC5"); textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val rect = RectF()

    fun set(f: Float, d: String, t: String) {
        fraction = f.coerceIn(0f, 1f); days = d; time = t; invalidate()
    }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val sw = s * 0.06f
        val pad = sw * 1.6f
        rect.set((width - s) / 2 + pad, (height - s) / 2 + pad, (width + s) / 2 - pad, (height + s) / 2 - pad)
        track.strokeWidth = sw; arc.strokeWidth = sw; glow.strokeWidth = sw * 1.9f
        arc.shader = SweepGradient(width / 2f, height / 2f,
            intArrayOf(Color.parseColor("#2BD67B"), Color.parseColor("#B8FFD8"), Color.parseColor("#2BD67B")), null)
        c.drawArc(rect, 0f, 360f, false, track)
        val sweep = 360f * fraction
        c.save(); c.rotate(-90f, width / 2f, height / 2f)
        c.drawArc(rect, 0f, sweep, false, glow)
        c.drawArc(rect, 0f, sweep, false, arc)
        c.restore()

        big.textSize = s * 0.30f; small.textSize = s * 0.075f; timePaint.textSize = s * 0.085f
        val cy = height / 2f
        c.drawText(days, width / 2f, cy + s * 0.04f, big)
        c.drawText(label, width / 2f, cy + s * 0.04f + s * 0.11f, small)
        c.drawText(time, width / 2f, cy + s * 0.04f + s * 0.23f, timePaint)
    }
}
