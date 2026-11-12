package com.pantau.hp

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface

object GraphRenderer {
    private val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22FFFFFF; style = Paint.Style.STROKE }
    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pDown = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.GREEN; typeface = Typeface.DEFAULT_BOLD }
    private val pUp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.ORANGE; typeface = Typeface.DEFAULT_BOLD }
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.MUTED }

    private fun niceMax(v: Long): Long {
        var unit = 1024L
        while (true) {
            for (st in longArrayOf(1, 2, 5)) {
                val cand = st * unit
                if (cand >= v) return cand
            }
            unit *= 10
        }
    }

    private fun smooth(xs: FloatArray, ys: FloatArray, p: Path) {
        p.moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) {
            val mx = (xs[i - 1] + xs[i]) / 2f
            val my = (ys[i - 1] + ys[i]) / 2f
            p.quadTo(xs[i - 1], ys[i - 1], mx, my)
        }
        p.lineTo(xs[xs.size - 1], ys[ys.size - 1])
    }

    private fun series(
        c: Canvas, data: LongArray, color: Int,
        top: Float, w: Float, gh: Float, mx: Float, s: Float
    ) {
        val n = data.size
        val xs = FloatArray(n) { it * (w / (n - 1)) }
        val ys = FloatArray(n) { top + gh - (data[it] / mx) * gh }
        val line = Path()
        smooth(xs, ys, line)
        val f = Path(line)
        f.lineTo(xs[n - 1], top + gh)
        f.lineTo(xs[0], top + gh)
        f.close()
        pFill.shader = LinearGradient(
            0f, top, 0f, top + gh,
            (color and 0x00FFFFFF) or 0x66000000, (color and 0x00FFFFFF), Shader.TileMode.CLAMP
        )
        c.drawPath(f, pFill)
        pLine.color = color
        pLine.strokeWidth = 2.4f * s
        c.drawPath(line, pLine)
    }

    // compact = true: hanya kurva (untuk widget); false: dengan angka kecepatan dan label
    fun draw(c: Canvas, w: Float, h: Float, idx: Int, s: Float, compact: Boolean) {
        val n = Stats.N
        var max = 1024L
        for (k in 0 until n) max = maxOf(max, Stats.down[idx][k], Stats.up[idx][k])
        val mx = niceMax(max).toFloat()

        val top = if (compact) 4f * s else 76f * s
        val bottom = if (compact) 4f * s else 22f * s
        val gh = h - top - bottom

        pGrid.strokeWidth = 1f
        for (i in 0..4) {
            val y = top + gh * i / 4f
            c.drawLine(0f, y, w, y, pGrid)
        }
        if (!compact) {
            for (i in 1..3) {
                val x = w * i / 4f
                c.drawLine(x, top, x, top + gh, pGrid)
            }
        }

        series(c, Stats.up[idx], C.ORANGE, top, w, gh, mx, s)
        series(c, Stats.down[idx], C.GREEN, top, w, gh, mx, s)

        if (!compact) {
            pDown.textSize = 22f * s
            pUp.textSize = 17f * s
            c.drawText("↓ " + Fmt.speed(Stats.curDown(idx)), 4f * s, 28f * s, pDown)
            c.drawText("↑ " + Fmt.speed(Stats.curUp(idx)), 4f * s, 54f * s, pUp)

            pSub.textSize = 10f * s
            pSub.textAlign = Paint.Align.RIGHT
            c.drawText(Fmt.speed(mx.toLong()), w - 3f * s, top - 4f * s, pSub)
            c.drawText(Fmt.speed((mx / 2f).toLong()), w - 3f * s, top + gh / 2f - 4f * s, pSub)
            pSub.textAlign = Paint.Align.LEFT
            c.drawText("2m", 2f * s, h - 5f * s, pSub)
            pSub.textAlign = Paint.Align.CENTER
            c.drawText("1m", w / 2f, h - 5f * s, pSub)
            pSub.textAlign = Paint.Align.RIGHT
            c.drawText("sekarang", w - 2f * s, h - 5f * s, pSub)
            pSub.textAlign = Paint.Align.LEFT
        }
    }
}
