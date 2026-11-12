package com.pantau.hp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator

class RingView(ctx: Context) : View(ctx) {
    var progress = 0f
        set(v) { field = v; invalidate() }
    var color = C.CYAN
        set(v) { field = v; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; this.color = C.TRACK
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(c: Canvas) {
        val sw = width * 0.14f
        track.strokeWidth = sw
        arc.strokeWidth = sw
        arc.color = color
        val r = RectF(sw / 2, sw / 2, width - sw / 2, height - sw / 2)
        c.drawArc(r, -90f, 360f, false, track)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) c.drawArc(r, -90f, 360f * p, false, arc)
    }
}

class BarChartView(ctx: Context) : View(ctx) {
    var values = LongArray(0)
    var labels: List<String> = emptyList()

    private val pTrack = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.TRACK }
    private val pBar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val pVal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT; color = C.CYAN; typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(c: Canvas) {
        val n = values.size
        if (n == 0) return
        val d = resources.displayMetrics.density
        val labelH = 24f * d
        val valH = 20f * d
        val gap = 10f * d
        val bw = (width - gap * (n - 1)) / n
        val chartTop = valH
        val chartBottom = height - labelH
        val ch = chartBottom - chartTop
        val max = maxOf(values.maxOrNull() ?: 1L, 1L)
        pLabel.textSize = 12f * d
        pVal.textSize = 12f * d
        for (i in 0 until n) {
            val x = i * (bw + gap)
            val r = bw / 2f
            val last = i == n - 1
            c.drawRoundRect(RectF(x, chartTop, x + bw, chartBottom), r, r, pTrack)
            if (values[i] > 0) {
                val fh = maxOf(values[i].toFloat() / max * ch, bw)
                pBar.color = if (last) C.CYAN else 0xFF2A93A8.toInt()
                c.drawRoundRect(RectF(x, chartBottom - fh, x + bw, chartBottom), r, r, pBar)
            }
            pLabel.color = if (last) C.CYAN else C.MUTED
            val lb = if (i < labels.size) labels[i] else ""
            c.drawText(lb, x + bw / 2f, height - 6f * d, pLabel)
            if (last && values[i] > 0) c.drawText(Fmt.bytes(values[i]), x + bw, chartTop - 6f * d, pVal)
        }
    }
}

class ScanButtonView(ctx: Context) : View(ctx) {
    private var angle = 0f
    private val anim: ValueAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { angle = it.animatedValue as Float; invalidate() }
    }
    var scanning = false
        set(v) {
            field = v
            if (v) {
                anim.start()
            } else {
                anim.cancel()
                angle = 0f
                invalidate()
            }
        }
    var caption = "Scan sekarang"
        set(v) { field = v; invalidate() }

    private val pGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0E2A36.toInt() }
    private val pRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = C.CYAN }
    private val pArc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        val big = minOf(width, height) / 2f - 4f * d

        pGlow.shader = RadialGradient(
            cx, cy, big, intArrayOf(0x5522D3EE, 0x0022D3EE), null, Shader.TileMode.CLAMP
        )
        c.drawCircle(cx, cy, big, pGlow)
        c.drawCircle(cx, cy, big * 0.6f, pFill)
        pRing.strokeWidth = 3f * d
        c.drawCircle(cx, cy, big * 0.6f, pRing)

        pArc.color = C.CYAN
        pArc.strokeWidth = 4f * d
        c.drawArc(RectF(cx - big * 0.95f, cy - big * 0.95f, cx + big * 0.95f, cy + big * 0.95f), -60f + angle, 90f, false, pArc)
        pArc.color = 0x6622D3EE
        pArc.strokeWidth = 3f * d
        c.drawArc(RectF(cx - big * 0.85f, cy - big * 0.85f, cx + big * 0.85f, cy + big * 0.85f), 120f - angle * 1.4f, 140f, false, pArc)
        pArc.color = 0x9922D3EE.toInt()
        pArc.strokeWidth = 3f * d
        c.drawArc(RectF(cx - big * 0.75f, cy - big * 0.75f, cx + big * 0.75f, cy + big * 0.75f), 30f + angle * 0.7f, 70f, false, pArc)

        pText.textSize = 17f * d
        c.drawText(caption, cx, cy + 6f * d, pText)
    }
}
