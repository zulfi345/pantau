package com.pantau.hp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

object GraphRenderer {
    private val pGrid = Paint().apply { color = 0x33FFFFFF }
    private val pDown = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4CAF50.toInt(); style = Paint.Style.STROKE
    }
    private val pUp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9800.toInt(); style = Paint.Style.STROKE
    }
    private val pFill = Paint().apply { color = 0x334CAF50; style = Paint.Style.FILL }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD
    }
    private val pSub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9AA5B1.toInt() }

    fun draw(c: Canvas, w: Float, h: Float, idx: Int, s: Float) {
        c.drawColor(0xFF101820.toInt())
        val n = Stats.N
        var max = 1024L
        for (k in 0 until n) max = maxOf(max, Stats.down[idx][k], Stats.up[idx][k])
        val mx = (max * 1.15).toFloat()

        val top = 36f * s
        val gh = h - top
        pDown.strokeWidth = 2f * s
        pUp.strokeWidth = 2f * s
        pGrid.strokeWidth = 1f

        for (i in 1..3) {
            val gy = top + gh * i / 4f
            c.drawLine(0f, gy, w, gy, pGrid)
        }

        val step = w / (n - 1)
        val pathD = Path()
        val pathU = Path()
        for (k in 0 until n) {
            val x = k * step
            val yd = top + gh - (Stats.down[idx][k] / mx) * gh
            val yu = top + gh - (Stats.up[idx][k] / mx) * gh
            if (k == 0) { pathD.moveTo(x, yd); pathU.moveTo(x, yu) }
            else { pathD.lineTo(x, yd); pathU.lineTo(x, yu) }
        }
        val fill = Path(pathD)
        fill.lineTo(w, top + gh)
        fill.lineTo(0f, top + gh)
        fill.close()
        c.drawPath(fill, pFill)
        c.drawPath(pathD, pDown)
        c.drawPath(pathU, pUp)

        // Kecepatan di pojok kiri atas
        pText.textSize = 15f * s
        c.drawText(
            "↓ " + Fmt.speed(Stats.curDown(idx)) + "   ↑ " + Fmt.speed(Stats.curUp(idx)),
            8f * s, 22f * s, pText
        )
        pSub.textSize = 10f * s
        val label = "maks " + Fmt.speed(mx.toLong())
        c.drawText(label, w - pSub.measureText(label) - 6f * s, top + 12f * s, pSub)
    }
}
