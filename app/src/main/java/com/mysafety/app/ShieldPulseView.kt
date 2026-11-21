package com.mysafety.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * লক/ভেরিফাই স্ক্রিনের উপরে "ব্রাউজার সুরক্ষা সক্রিয়" বোঝানোর অ্যানিমেশন:
 * কেন্দ্রের শিল্ড আইকনকে ঘিরে ধাপে ধাপে ছড়িয়ে পড়া ও মিলিয়ে যাওয়া রিং (স্ক্যানিং পালস)।
 */
class ShieldPulseView(ctx: Context) : View(ctx) {

    private var phase = 0f
    private val ringCount = 3

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#9BFFC5")
    }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1AFFFFFF")
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1FFFFFFF")
    }
    private val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#66FFFFFF")
    }

    private val icon: Drawable? = ctx.getDrawable(R.drawable.ic_shield)?.mutate()?.apply {
        setTint(Color.WHITE)
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: android.graphics.Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val maxR = s * 0.5f
        val badgeR = s * 0.26f

        // ছড়িয়ে পড়া স্ক্যানিং রিং
        ringPaint.strokeWidth = s * 0.012f
        for (i in 0 until ringCount) {
            val p = (phase + i.toFloat() / ringCount) % 1f
            val r = badgeR + p * (maxR - badgeR)
            ringPaint.alpha = ((1f - p) * 160).toInt().coerceIn(0, 255)
            c.drawCircle(cx, cy, r, ringPaint)
        }

        // নরম হ্যালো
        c.drawCircle(cx, cy, badgeR * 1.55f, haloPaint)

        // কেন্দ্রের ব্যাজ
        c.drawCircle(cx, cy, badgeR, badgePaint)
        badgeStroke.strokeWidth = s * 0.012f
        c.drawCircle(cx, cy, badgeR, badgeStroke)

        icon?.let {
            val iR = (badgeR * 0.95f).toInt()
            it.setBounds((cx - iR).toInt(), (cy - iR).toInt(), (cx + iR).toInt(), (cy + iR).toInt())
            it.draw(c)
        }
    }
}
