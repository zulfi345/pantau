package com.pantau.hp

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Calendar

class DataPage(private val a: MainActivity) : Page {
    private class Res(
        val tw: Long, val tm: Long, val values: LongArray, val labels: List<String>,
        val chartTitle: String, val totalLabel: String, val apps: List<UsageRepo.AppUse>
    )

    private val inner = LinearLayout(a)
    override val view: View = a.scrollPage(inner)
    private val ui = Handler(Looper.getMainLooper())
    private val tabs = ArrayList<TextView>()
    private var mode = 0
    private var loadId = 0
    private val permCard = a.card()
    private val totalLabel = a.tv("Total hari ini", 14f, C.MUTED)
    private val totalVal = a.tv("-", 36f, C.TEXT, true)
    private val splitBar = LinearLayout(a)
    private val barW = View(a)
    private val barM = View(a)
    private val legend = a.tv("", 13f, C.MUTED)
    private val chartTitle = a.tv("7 Hari Terakhir", 16f, C.TEXT, true)
    private val chart = BarChartView(a)
    private val appsBox = LinearLayout(a)

    init { build() }

    private fun build() {
        inner.addView(a.tv("Penggunaan Data", 22f, C.TEXT, true), a.lp(WRAP, WRAP, 0f, 0, 0, 0, 12))

        val seg = LinearLayout(a)
        seg.background = roundBg(0xFF1A2736.toInt(), a.dpf(24f))
        seg.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(4))
        listOf("Hari", "Minggu", "Bulan").forEachIndexed { i, t ->
            val x = a.tv(t, 15f, C.MUTED, true)
            x.gravity = Gravity.CENTER
            x.setPadding(0, a.dp(10), 0, a.dp(10))
            x.setOnClickListener { mode = i; styleTabs(); load() }
            tabs.add(x)
            seg.addView(x, a.lp(0, WRAP, 1f))
        }
        inner.addView(seg, a.lp(MATCH, WRAP))
        styleTabs()

        permCard.addView(a.tv("Izin 'Akses penggunaan' diperlukan untuk membaca statistik data.", 14f, C.TEXT))
        permCard.addView(
            a.button("Beri izin", C.DARK_ON_CYAN, C.CYAN) {
                try { a.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) } catch (e: Exception) { }
            },
            a.lp(MATCH, WRAP, 0f, 0, 10, 0, 0)
        )
        inner.addView(permCard, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        val tc = a.card()
        tc.addView(totalLabel)
        tc.addView(totalVal)
        splitBar.background = roundBg(C.TRACK, a.dpf(8f))
        splitBar.clipToOutline = true
        barW.setBackgroundColor(C.CYAN)
        barM.setBackgroundColor(C.ORANGE)
        splitBar.addView(barW, LinearLayout.LayoutParams(0, MATCH, 1f))
        splitBar.addView(barM, LinearLayout.LayoutParams(0, MATCH, 1f))
        tc.addView(splitBar, a.lp(MATCH, a.dp(8), 0f, 0, 10, 0, 10))
        tc.addView(legend)
        inner.addView(tc, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        val cc = a.card()
        cc.addView(chartTitle)
        cc.addView(chart, a.lp(MATCH, a.dp(180), 0f, 0, 12, 0, 0))
        inner.addView(cc, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        val ac = a.card()
        ac.addView(a.tv("Aplikasi terboros", 16f, C.TEXT, true), a.lp(WRAP, WRAP, 0f, 0, 0, 0, 12))
        appsBox.orientation = LinearLayout.VERTICAL
        ac.addView(appsBox)
        inner.addView(ac, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        inner.addView(
            a.tv("Data memakai riwayat statistik Android. Riwayat lama bisa terbatas.", 11f, C.MUTED),
            a.lp(WRAP, WRAP, 0f, 0, 12, 0, 0)
        )
    }

    private fun styleTabs() {
        for ((k, t) in tabs.withIndex()) {
            if (k == mode) {
                t.setTextColor(C.DARK_ON_CYAN)
                t.background = roundBg(C.CYAN, a.dpf(20f))
            } else {
                t.setTextColor(C.MUTED)
                t.background = null
            }
        }
    }

    private fun load() {
        val has = UsageRepo.hasAccess(a)
        permCard.visibility = if (has) View.GONE else View.VISIBLE
        if (!has) return
        val my = ++loadId
        val m = mode
        Thread {
            val res = compute(m)
            ui.post { if (my == loadId) show(res) }
        }.start()
    }

    private fun defaultIcon(): Drawable? = a.getDrawable(android.R.drawable.sym_def_app_icon)

    private fun compute(m: Int): Res {
        val now = System.currentTimeMillis()
        val dn = arrayOf("Min", "Sen", "Sel", "Rab", "Kam", "Jum", "Sab")
        val mn = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        val wt = ConnectivityManager.TYPE_WIFI
        val mt = ConnectivityManager.TYPE_MOBILE
        val labels = ArrayList<String>()
        val vals: LongArray
        val ps: Long
        val title: String
        val tl: String
        if (m == 0) {
            vals = LongArray(7)
            for (k in 0..6) {
                val i = 6 - k
                val s = UsageRepo.startOfDay(i)
                val e = if (i == 0) now else UsageRepo.startOfDay(i - 1)
                vals[k] = UsageRepo.device(a, wt, s, e) + UsageRepo.device(a, mt, s, e)
                val c = Calendar.getInstance()
                c.timeInMillis = s
                labels.add(dn[c.get(Calendar.DAY_OF_WEEK) - 1])
            }
            ps = UsageRepo.startOfDay(0)
            title = "7 Hari Terakhir"
            tl = "Total hari ini"
        } else if (m == 1) {
            vals = LongArray(4)
            for (k in 0..3) {
                val j = 3 - k
                val e = now - j * 7L * 86400000L
                val s = e - 7L * 86400000L
                vals[k] = UsageRepo.device(a, wt, s, e) + UsageRepo.device(a, mt, s, e)
                labels.add(if (j == 0) "Ini" else "-$j mgg")
            }
            ps = now - 7L * 86400000L
            title = "4 Minggu Terakhir"
            tl = "Total 7 hari terakhir"
        } else {
            vals = LongArray(6)
            for (k in 0..5) {
                val j = 5 - k
                val s = UsageRepo.monthStart(j)
                val e = if (j == 0) now else UsageRepo.monthStart(j - 1)
                vals[k] = UsageRepo.device(a, wt, s, e) + UsageRepo.device(a, mt, s, e)
                val c = Calendar.getInstance()
                c.timeInMillis = s
                labels.add(mn[c.get(Calendar.MONTH)])
            }
            ps = UsageRepo.monthStart(0)
            title = "6 Bulan Terakhir"
            tl = "Total bulan ini"
        }
        val tw = UsageRepo.device(a, wt, ps, now)
        val tm = UsageRepo.device(a, mt, ps, now)
        val apps = UsageRepo.perApp(a, ps, now).take(8)
        for (u in apps) {
            u.icon = try {
                if (u.pkg.isNotEmpty()) a.packageManager.getApplicationIcon(u.pkg) else defaultIcon()
            } catch (e: Exception) {
                defaultIcon()
            }
        }
        return Res(tw, tm, vals, labels, title, tl, apps)
    }

    private fun show(r: Res) {
        totalLabel.text = r.totalLabel
        totalVal.text = Fmt.bytes(r.tw + r.tm)
        barW.layoutParams = LinearLayout.LayoutParams(0, MATCH, r.tw.toFloat())
        barM.layoutParams = LinearLayout.LayoutParams(0, MATCH, r.tm.toFloat())

        val sb = SpannableStringBuilder()
        sb.append("● ")
        sb.setSpan(ForegroundColorSpan(C.CYAN), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("Wi-Fi: " + Fmt.bytes(r.tw) + "      ")
        val st = sb.length
        sb.append("● ")
        sb.setSpan(ForegroundColorSpan(C.ORANGE), st, st + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append("SIM: " + Fmt.bytes(r.tm))
        legend.text = sb

        chartTitle.text = r.chartTitle
        chart.values = r.values
        chart.labels = r.labels
        chart.invalidate()

        appsBox.removeAllViews()
        if (r.apps.isEmpty()) {
            appsBox.addView(a.tv("Belum ada data pemakaian.", 14f, C.MUTED))
            return
        }
        val max = maxOf(r.apps[0].total, 1L)
        for ((i, u) in r.apps.withIndex()) {
            val row = LinearLayout(a)
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(a.tv("${i + 1}.", 13f, C.MUTED), a.lp(a.dp(24), WRAP))
            val ic = ImageView(a)
            ic.setImageDrawable(u.icon)
            row.addView(ic, a.lp(a.dp(36), a.dp(36), 0f, 0, 0, 12, 0))

            val col = LinearLayout(a)
            col.orientation = LinearLayout.VERTICAL
            val line = LinearLayout(a)
            line.addView(a.tv(u.label, 15f, C.TEXT), a.lp(0, WRAP, 1f))
            line.addView(a.tv(Fmt.bytes(u.total), 14f, C.TEXT, true))
            col.addView(line)

            val frac = (u.total.toFloat() / max).coerceIn(0.03f, 1f)
            val track = LinearLayout(a)
            track.background = roundBg(C.TRACK, a.dpf(4f))
            track.clipToOutline = true
            val fill = View(a)
            fill.setBackgroundColor(C.CYAN)
            track.addView(fill, LinearLayout.LayoutParams(0, MATCH, frac))
            track.addView(View(a), LinearLayout.LayoutParams(0, MATCH, 1f - frac))
            col.addView(track, a.lp(MATCH, a.dp(6), 0f, 0, 6, 0, 0))

            row.addView(col, a.lp(0, WRAP, 1f))
            appsBox.addView(row, a.lp(MATCH, WRAP, 0f, 0, 0, 0, 14))
        }
    }

    override fun onShow() {
        load()
    }
}
