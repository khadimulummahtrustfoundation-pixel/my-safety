package com.mysafety.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * খোলা তালা নড়াচড়া (wobble) করতে থাকে, তারপর একটা চাবি এসে ঘুরে লক করে দেয়;
 * লক হয়ে থাকার পর আবার খোলা তালা থেকে পুরো চক্রটা পুনরাবৃত্তি হয়।
 * শুধু সেটআপ/লক-স্ক্রিনে (এখনো লক না হওয়া অবস্থায়) দেখানোর জন্য — কোনো বৃত্তাকার পালস-রিং নেই।
 */
class LockKeyAnimView(ctx: Context) : View(ctx) {

    private var phase = 0f // ০..১, পুরো চক্র জুড়ে

    // পর্যায়ের সীমা (০..১)
    private val wobbleEnd = 0.28f
    private val keyMoveEnd = 0.50f
    private val lockClickEnd = 0.58f
    private val holdEnd = 0.86f
    // ০.৮৬..১.০ = আবার খোলা তালায় ফিরে যাওয়া

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#DDF3E4")
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.parseColor("#12803F")
    }
    private val badgeStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = Color.parseColor("#0B3D20")
    }

    private val lockOpen: Drawable? = ctx.getDrawable(R.drawable.ic_lock_open)?.mutate()?.apply { setTint(Color.WHITE) }
    private val lockClosed: Drawable? = ctx.getDrawable(R.drawable.ic_lock)?.mutate()?.apply { setTint(Color.WHITE) }
    private val key: Drawable? = ctx.getDrawable(R.drawable.ic_key)?.mutate()?.apply { setTint(Color.parseColor("#0B3D20")) }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3400
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { animator.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val cx = width / 2f; val cy = height / 2f
        val badgeR = s * 0.42f
        val iconR = badgeR * 0.55f

        // নরম হ্যালো + ব্যাজ (স্থির, কোনো বৃত্তাকার পালস নেই)
        c.drawCircle(cx, cy, badgeR * 1.18f, haloPaint)
        c.drawCircle(cx, cy, badgeR, badgePaint)
        badgeStroke.strokeWidth = s * 0.02f
        c.drawCircle(cx, cy, badgeR, badgeStroke)

        // খোলা তালার wobble (দুলুনি)
        val wobbleAngle = if (phase < wobbleEnd) {
            (sin((phase / wobbleEnd) * Math.PI * 4).toFloat()) * 9f
        } else 0f

        // খোলা -> বন্ধ তালা ক্রসফেড
        val closedAlpha = when {
            phase < lockClickEnd -> 0f
            phase < holdEnd -> 255f
            else -> (255f * (1f - (phase - holdEnd) / (1f - holdEnd))).coerceIn(0f, 255f)
        }
        val openAlpha = 255f - closedAlpha

        c.save(); c.rotate(wobbleAngle, cx, cy)
        lockOpen?.let {
            it.alpha = openAlpha.toInt().coerceIn(0, 255)
            it.setBounds((cx - iconR).toInt(), (cy - iconR).toInt(), (cx + iconR).toInt(), (cy + iconR).toInt())
            it.draw(c)
        }
        c.restore()

        lockClosed?.let {
            it.alpha = closedAlpha.toInt().coerceIn(0, 255)
            it.setBounds((cx - iconR).toInt(), (cy - iconR).toInt(), (cx + iconR).toInt(), (cy + iconR).toInt())
            it.draw(c)
        }

        // চাবি: ডান পাশ থেকে এসে ঘুরে লক করে দিয়ে মিলিয়ে যায়
        if (phase in wobbleEnd..holdEnd) {
            val keyR = iconR * 0.78f
            val moveT = ((phase - wobbleEnd) / (keyMoveEnd - wobbleEnd)).coerceIn(0f, 1f)
            val startX = cx + s * 0.46f
            val endX = cx + iconR * 0.1f
            val kx = startX + (endX - startX) * moveT
            val turnT = ((phase - keyMoveEnd) / (lockClickEnd - keyMoveEnd)).coerceIn(0f, 1f)
            val rotation = turnT * 55f

            val keyAlpha = when {
                phase < lockClickEnd -> 255f
                phase < holdEnd -> (255f * (1f - ((phase - lockClickEnd) / (holdEnd - lockClickEnd) * 3f).coerceAtMost(1f)))
                else -> 0f
            }

            key?.let {
                it.alpha = keyAlpha.toInt().coerceIn(0, 255)
                c.save(); c.rotate(rotation, kx, cy)
                it.setBounds((kx - keyR).toInt(), (cy - keyR).toInt(), (kx + keyR).toInt(), (cy + keyR).toInt())
                it.draw(c)
                c.restore()
            }
        }
    }
}
