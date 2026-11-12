package com.pantau.hp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.RemoteViews

class SpeedWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, build(ctx))
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val w = 600
            val h = 300
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            GraphRenderer.draw(c, w.toFloat(), h.toFloat(), 0, 2f)

            val bar = Paint().apply { color = 0xAA101820.toInt() }
            c.drawRect(0f, h - 72f, w.toFloat(), h.toFloat(), bar)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 24f }
            c.drawText(
                "WiFi  ↓ " + Fmt.speed(Stats.curDown(1)) + "  ↑ " + Fmt.speed(Stats.curUp(1)),
                10f, h - 44f, p
            )
            c.drawText(
                "SIM   ↓ " + Fmt.speed(Stats.curDown(2)) + "  ↑ " + Fmt.speed(Stats.curUp(2)),
                10f, h - 14f, p
            )

            val rv = RemoteViews(ctx.packageName, R.layout.widget)
            rv.setImageViewBitmap(R.id.img, bmp)
            val pi = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.img, pi)
            return rv
        }

        fun updateAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, SpeedWidget::class.java))
            if (ids.isEmpty()) return
            m.updateAppWidget(ids, build(ctx))
        }
    }
}
