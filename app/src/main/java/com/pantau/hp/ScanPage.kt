package com.pantau.hp

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class ScanPage(private val a: MainActivity) : Page {
    private val inner = LinearLayout(a)
    override val view: View = a.scrollPage(inner)
    private val ui = Handler(Looper.getMainLooper())
    private val btn = ScanButtonView(a)
    private val sub = a.tv("Ketuk lingkaran untuk memindai semua aplikasi", 14f, C.CYAN)
    private val vSafe = a.tv("-", 30f, C.GREEN, true)
    private val vWarn = a.tv("-", 30f, C.ORANGE, true)
    private val vRisk = a.tv("-", 30f, C.RED, true)
    private val listCount = a.tv("", 13f, C.MUTED)
    private val listBox = LinearLayout(a)
    private val fileNote = a.card()

    init { build() }

    private fun statCard(icon: String, label: String, value: TextView, color: Int): LinearLayout {
        val c = LinearLayout(a)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.setPadding(a.dp(8), a.dp(14), a.dp(8), a.dp(14))
        c.background = roundBg((color and 0x00FFFFFF) or 0x22000000, a.dpf(20f), color, a.dp(1))
        c.addView(a.tv(icon, 20f))
        c.addView(a.tv(label, 13f, C.TEXT), a.lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
        c.addView(value)
        return c
    }

    private fun build() {
        inner.addView(a.tv("Keamanan", 22f, C.TEXT, true))

        val bp = a.lp(a.dp(260), a.dp(260), 0f, 0, 8, 0, 0)
        bp.gravity = Gravity.CENTER_HORIZONTAL
        btn.setOnClickListener { if (!btn.scanning) scan() }
        inner.addView(btn, bp)

        sub.gravity = Gravity.CENTER
        inner.addView(sub, a.lp(MATCH, WRAP, 0f, 0, 4, 0, 0))

        val row = LinearLayout(a)
        row.addView(statCard("✅", "Aman", vSafe, C.GREEN), a.lp(0, WRAP, 1f, 0, 0, 5, 0))
        row.addView(statCard("⚠️", "Waspada", vWarn, C.ORANGE), a.lp(0, WRAP, 1f, 5, 0, 5, 0))
        row.addView(statCard("🚨", "Berisiko", vRisk, C.RED), a.lp(0, WRAP, 1f, 5, 0, 0, 0))
        inner.addView(row, a.lp(MATCH, WRAP, 0f, 0, 18, 0, 0))

        fileNote.addView(a.tv("File APK di penyimpanan belum ikut dipindai. Izinkan akses semua file agar APK tersembunyi/menyamar ikut terdeteksi.", 13f, C.TEXT))
        fileNote.addView(
            a.button("Izinkan akses semua file", C.CYAN, 0x00000000, C.CYAN) { a.requestStorage() },
            a.lp(MATCH, WRAP, 0f, 0, 10, 0, 0)
        )
        inner.addView(fileNote, a.lp(MATCH, WRAP, 0f, 0, 14, 0, 0))

        val head = LinearLayout(a)
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(a.tv("Aplikasi Mencurigakan", 18f, C.TEXT, true), a.lp(0, WRAP, 1f))
        head.addView(listCount)
        inner.addView(head, a.lp(MATCH, WRAP, 0f, 0, 18, 0, 8))

        listBox.orientation = LinearLayout.VERTICAL
        inner.addView(listBox)
        inner.addView(
            a.tv("Pemindai heuristik: menilai izin, sumber pemasangan, dan layanan sensitif. Bukan database virus, jadi hasilnya bisa meleset.", 11f, C.MUTED),
            a.lp(WRAP, WRAP, 0f, 0, 12, 0, 0)
        )
    }

    private fun scan() {
        btn.scanning = true
        btn.caption = "Memindai..."
        sub.text = "Mohon tunggu, jangan tutup aplikasi"
        Thread {
            val r = try { AppScanner.scan(a) } catch (e: Exception) { null }
            ui.post {
                btn.scanning = false
                btn.caption = "Scan lagi"
                if (r == null) sub.text = "Scan gagal, coba lagi" else show(r)
            }
        }.start()
    }

    private fun defaultIcon(): Drawable? = a.getDrawable(android.R.drawable.sym_def_app_icon)

    private fun iconOf(pkg: String): Drawable? = try {
        if (pkg.isNotEmpty()) a.packageManager.getApplicationIcon(pkg) else defaultIcon()
    } catch (e: Exception) {
        defaultIcon()
    }

    private fun show(r: AppScanner.Result) {
        val appF = r.findings.filter { it.pkg.isNotEmpty() }
        val risky = r.findings.count { it.score >= 6 }
        val warn = r.findings.size - risky
        vSafe.text = maxOf(r.apps - appF.size, 0).toString()
        vWarn.text = warn.toString()
        vRisk.text = risky.toString()
        sub.text = "${r.apps} aplikasi" + (if (r.filesScanned) ", ${r.files} file" else "") + " diperiksa"
        fileNote.visibility = if (r.filesScanned) View.GONE else View.VISIBLE
        listCount.text = "${r.findings.size} ditemukan"

        listBox.removeAllViews()
        if (r.findings.isEmpty()) {
            listBox.addView(a.tv("Tidak ada yang mencurigakan. 🎉", 14f, C.GREEN))
            return
        }
        for (f in r.findings.take(100)) listBox.addView(row(f), a.lp(MATCH, WRAP, 0f, 0, 0, 0, 10))
    }

    private fun row(f: AppScanner.Finding): View {
        val c = a.card()
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12))
        val ic = ImageView(a)
        ic.setImageDrawable(iconOf(f.pkg))
        c.addView(ic, a.lp(a.dp(44), a.dp(44), 0f, 0, 0, 12, 0))

        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.addView(a.tv(f.label, 15f, C.TEXT, true))
        val lvl = if (f.score >= 6) C.RED else C.ORANGE
        for (reason in f.reasons.take(2)) {
            col.addView(
                a.pill(reason, lvl, (lvl and 0x00FFFFFF) or 0x22000000, lvl),
                a.lp(WRAP, WRAP, 0f, 0, 4, 0, 0)
            )
        }
        if (f.reasons.size > 2) col.addView(a.tv("+${f.reasons.size - 2} lainnya", 11f, C.MUTED), a.lp(WRAP, WRAP, 0f, 0, 4, 0, 0))
        c.addView(col, a.lp(0, WRAP, 1f))

        if (f.pkg.isNotEmpty()) {
            c.addView(
                a.button("Copot", Color.WHITE, 0xFFE5534B.toInt()) {
                    try { a.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + f.pkg))) } catch (e: Exception) { }
                },
                a.lp(WRAP, WRAP, 0f, 8, 0, 0, 0)
            )
            c.setOnClickListener {
                try {
                    a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + f.pkg)))
                } catch (e: Exception) { }
            }
        } else {
            c.setOnClickListener { Toast.makeText(a, f.path, Toast.LENGTH_LONG).show() }
        }
        return c
    }

    override fun onShow() {
        fileNote.visibility = if (AppScanner.canScanFiles(a)) View.GONE else View.VISIBLE
    }
}
