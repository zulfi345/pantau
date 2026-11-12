package com.pantau.hp

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class HomePage(private val a: MainActivity) : Page {
    private class Stat(val card: LinearLayout, val value: TextView, val sub: TextView, val ring: RingView)

    private val inner = LinearLayout(a)
    override val view: View = a.scrollPage(inner)
    private val graph = GraphView(a)
    private val status = a.pill("Terlindungi ✓", C.GREEN, 0x2234D399, C.GREEN)
    private val tabs = ArrayList<TextView>()
    private val sTemp = stat("🌡 Suhu")
    private val sBat = stat("🔋 Baterai")
    private val sRam = stat("🧠 RAM")
    private val sSto = stat("💾 Penyimpanan")
    private val chipTxt = a.tv("", 14f, C.TEXT, true)
    private val thermTxt = a.tv("", 12f, C.MUTED)
    private var n = 0

    init { build() }

    private fun stat(label: String): Stat {
        val c = a.card()
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(a.dp(14), a.dp(14), a.dp(12), a.dp(14))
        val left = LinearLayout(a)
        left.orientation = LinearLayout.VERTICAL
        left.addView(a.tv(label, 12f, C.MUTED))
        val v = a.tv("-", 19f, C.TEXT, true)
        left.addView(v, a.lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
        val sub = a.tv("", 11f, C.MUTED)
        left.addView(sub)
        c.addView(left, a.lp(0, WRAP, 1f))
        val ring = RingView(a)
        c.addView(ring, a.lp(a.dp(46), a.dp(46)))
        return Stat(c, v, sub, ring)
    }

    private fun build() {
        val head = LinearLayout(a)
        head.gravity = Gravity.CENTER_VERTICAL
        val logo = ImageView(a)
        logo.setImageResource(R.drawable.logo)
        head.addView(logo, a.lp(a.dp(32), a.dp(32), 0f, 0, 0, 10, 0))
        head.addView(a.tv("Pantau HP", 22f, C.TEXT, true), a.lp(0, WRAP, 1f))
        head.addView(status)
        inner.addView(head, a.lp(MATCH, WRAP))

        val gc = a.card()
        gc.setPadding(a.dp(12), a.dp(8), a.dp(12), a.dp(8))
        gc.addView(graph, LinearLayout.LayoutParams(MATCH, a.dp(250)))
        inner.addView(gc, a.lp(MATCH, WRAP, 0f, 0, 14, 0, 0))

        val seg = LinearLayout(a)
        seg.background = roundBg(0xFF1A2736.toInt(), a.dpf(22f))
        seg.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
        listOf("Total", "WiFi", "SIM").forEachIndexed { i, t ->
            val x = a.tv(t, 14f, C.MUTED, true)
            x.setPadding(a.dp(22), a.dp(8), a.dp(22), a.dp(8))
            x.gravity = Gravity.CENTER
            x.setOnClickListener { select(i) }
            tabs.add(x)
            seg.addView(x)
        }
        val sp = a.lp(WRAP, WRAP, 0f, 0, 14, 0, 0)
        sp.gravity = Gravity.CENTER_HORIZONTAL
        inner.addView(seg, sp)
        select(0)

        val r1 = LinearLayout(a)
        r1.addView(sTemp.card, a.lp(0, WRAP, 1f, 0, 0, 6, 0))
        r1.addView(sBat.card, a.lp(0, WRAP, 1f, 6, 0, 0, 0))
        inner.addView(r1, a.lp(MATCH, WRAP, 0f, 0, 14, 0, 0))
        val r2 = LinearLayout(a)
        r2.addView(sRam.card, a.lp(0, WRAP, 1f, 0, 0, 6, 0))
        r2.addView(sSto.card, a.lp(0, WRAP, 1f, 6, 0, 0, 0))
        inner.addView(r2, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        val dc = a.card()
        dc.addView(a.tv("Detail chip", 12f, C.MUTED))
        dc.addView(chipTxt, a.lp(WRAP, WRAP, 0f, 0, 4, 0, 8))
        dc.addView(thermTxt)
        inner.addView(dc, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))
    }

    private fun select(i: Int) {
        graph.idx = i
        for ((k, t) in tabs.withIndex()) {
            if (k == i) {
                t.setTextColor(C.DARK_ON_CYAN)
                t.background = roundBg(0xFFCFEFF7.toInt(), a.dpf(18f))
            } else {
                t.setTextColor(C.MUTED)
                t.background = null
            }
        }
        graph.invalidate()
    }

    private fun update() {
        val (lv, temp) = SysInfo.battery(a)
        sTemp.value.text = if (temp > 0) String.format(Locale.US, "%.0f°C", temp) else "-"
        sTemp.sub.text = "suhu baterai"
        sTemp.ring.progress = (temp / 60.0).toFloat()
        sTemp.ring.color = if (temp >= 45) C.RED else if (temp >= 40) C.ORANGE else C.GREEN

        sBat.value.text = "$lv%"
        sBat.sub.text = "sisa daya"
        sBat.ring.progress = lv / 100f
        sBat.ring.color = if (lv < 15) C.RED else C.CYAN

        val (used, total) = SysInfo.ram(a)
        sRam.value.text = Fmt.gb(used, 1)
        sRam.sub.text = "dari " + Fmt.gb(total, 1)
        sRam.ring.progress = used.toFloat() / maxOf(total, 1L)
        sRam.ring.color = C.CYAN

        val (su, st) = SysInfo.storage()
        sSto.value.text = Fmt.gb(su, 0)
        sSto.sub.text = "dari " + Fmt.gb(st, 0)
        sSto.ring.progress = su.toFloat() / maxOf(st, 1L)
        sSto.ring.color = if (su.toFloat() / maxOf(st, 1L) > 0.9f) C.RED else C.CYAN

        if (Stats.running) {
            status.text = "Terlindungi ✓"
            status.setTextColor(C.GREEN)
            status.background = roundBg(0x2234D399, a.dpf(20f), C.GREEN, a.dp(1))
        } else {
            status.text = "Pemantauan mati"
            status.setTextColor(C.ORANGE)
            status.background = roundBg(0x22FB923C, a.dpf(20f), C.ORANGE, a.dp(1))
        }
    }

    private fun refreshChip() {
        chipTxt.text = SysInfo.chip()
        val t = SysInfo.thermal()
        thermTxt.text = if (t.isEmpty()) "Suhu chip tidak bisa dibaca di HP ini" else t.joinToString("\n")
    }

    override fun onShow() {
        update()
        refreshChip()
        graph.invalidate()
    }

    override fun tick() {
        graph.invalidate()
        update()
        if (n++ % 3 == 0) refreshChip()
    }
}
