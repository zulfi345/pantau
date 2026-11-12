package com.pantau.hp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews

class SpeedWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) {
        for (id in ids) m.updateAppWidget(id, build(ctx))
    }

    companion object {
        private fun chip(c: Canvas, x: Float, y: Float, w: Float, h: Float, text: String) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
            val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0x44FFFFFF }
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 23f }
            val r = RectF(x, y, x + w, y + h)
            c.drawRoundRect(r, h / 2, h / 2, bg)
            c.drawRoundRect(r, h / 2, h / 2, st)
            c.drawText(text, x + 22f, y + h / 2 + 8f, tp)
        }

        private fun today(label: String, v: Long): String =
            if (v < 0) "$label  –" else label + "  " + Fmt.bytes(v) + " hari ini"

        fun build(ctx: Context): RemoteViews {
            val w = 700
            val h = 330
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)

            val rect = RectF(3f, 3f, w - 3f, h - 3f)
            c.drawRoundRect(rect, 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0F1A27.toInt() })
            c.drawRoundRect(rect, 46f, 46f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 3f; color = 0x55FFFFFF
            })

            val pArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C.GREEN; textSize = 56f; typeface = Typeface.DEFAULT_BOLD
            }
            val pBig = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt(); textSize = 60f; typeface = Typeface.DEFAULT_BOLD
            }
            c.drawText("↓", 32f, 96f, pArrow)
            c.drawText(Fmt.speed(Stats.curDown(0)), 88f, 96f, pBig)

            val pBrand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = C.CYAN; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT
            }
            c.drawText("PANTAU HP", w - 34f, 52f, pBrand)

            chip(c, 28f, 140f, 316f, 64f, today("WiFi", UsageRepo.todayWifi))
            chip(c, 28f, 222f, 316f, 64f, today("SIM", UsageRepo.todayMobile))

            c.save()
            c.translate(362f, 130f)
            c.clipRect(0f, 0f, 310f, 170f)
            GraphRenderer.draw(c, 310f, 170f, 0, 1.6f, true)
            c.restore()

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
