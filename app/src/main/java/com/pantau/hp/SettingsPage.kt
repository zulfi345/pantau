package com.pantau.hp

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class SettingsPage(private val a: MainActivity) : Page {
    private val inner = LinearLayout(a)
    override val view: View = a.scrollPage(inner)
    private val swMon = Switch(a)
    private var binding = false
    private val thrText = a.tv("", 24f, C.TEXT, true)
    private val status = HashMap<String, TextView>()

    init { build() }

    private fun prefs() = a.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun thr() = prefs().getInt("thr_kb", 2048)
    private fun setThr(v: Int) {
        prefs().edit().putInt("thr_kb", v.coerceIn(256, 102400)).apply()
        refreshThr()
    }

    private fun open(i: Intent) {
        try { a.startActivity(i) } catch (e: Exception) { }
    }

    private fun build() {
        inner.addView(a.tv("Aturan", 22f, C.TEXT, true), a.lp(WRAP, WRAP, 0f, 0, 0, 0, 12))

        val c1 = a.card()
        c1.orientation = LinearLayout.HORIZONTAL
        c1.gravity = Gravity.CENTER_VERTICAL
        val col1 = LinearLayout(a)
        col1.orientation = LinearLayout.VERTICAL
        col1.addView(a.tv("Pemantauan real-time", 16f, C.TEXT, true))
        col1.addView(a.tv("Grafik, alarm lonjakan, dan widget", 12f, C.MUTED))
        c1.addView(col1, a.lp(0, WRAP, 1f))
        styleSwitch(swMon, C.GREEN)
        c1.addView(swMon)
        swMon.setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                if (checked) a.startForegroundService(Intent(a, MonitorService::class.java))
                else a.stopService(Intent(a, MonitorService::class.java))
            }
        }
        inner.addView(c1, a.lp(MATCH, WRAP))

        val c2 = a.card()
        c2.addView(a.tv("Batas alarm lonjakan internet", 13f, C.MUTED))
        c2.addView(thrText, a.lp(WRAP, WRAP, 0f, 0, 4, 0, 10))
        val row = LinearLayout(a)
        row.addView(a.button("−", C.CYAN, C.TRACK) { setThr(thr() - 512) }, a.lp(0, WRAP, 1f, 0, 0, 6, 0))
        row.addView(a.button("+", C.CYAN, C.TRACK) { setThr(thr() + 512) }, a.lp(0, WRAP, 1f, 6, 0, 0, 0))
        c2.addView(row)
        c2.addView(
            a.button("Matikan alarm sekarang", C.RED, 0x00000000, C.RED) { Alarm.stop() },
            a.lp(MATCH, WRAP, 0f, 0, 10, 0, 0)
        )
        c2.addView(
            a.tv("Alarm berbunyi saat kecepatan melewati batas dan jauh di atas rata-rata, juga saat suhu baterai 45°C atau lebih.", 11f, C.MUTED),
            a.lp(WRAP, WRAP, 0f, 0, 10, 0, 0)
        )
        inner.addView(c2, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        val c3 = a.card()
        c3.addView(a.tv("Izin & pengaturan", 13f, C.MUTED))
        c3.addView(permRow("usage", "Akses penggunaan", "Statistik data per hari, minggu, bulan") {
            open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        c3.addView(permRow("dnd", "Jangan Ganggu (DND)", "Agar alarm tetap bunyi saat DND aktif") {
            open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        c3.addView(permRow("battery", "Penghemat baterai", "Pilih Pantau HP: tanpa batasan") {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        c3.addView(permRow("files", "Akses semua file", "Untuk memindai file APK") { a.requestStorage() })
        c3.addView(permRow("notif", "Notifikasi", "Kecepatan dan peringatan") {
            open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, a.packageName))
        })
        inner.addView(c3, a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0))

        inner.addView(
            a.tv("Pantau HP v1.0", 12f, C.MUTED),
            a.lp(WRAP, WRAP, 0f, 0, 16, 0, 0)
        )
    }

    private fun permRow(key: String, title: String, sub: String, click: () -> Unit): View {
        val r = LinearLayout(a)
        r.gravity = Gravity.CENTER_VERTICAL
        r.setPadding(0, a.dp(12), 0, a.dp(4))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.addView(a.tv(title, 15f, C.TEXT, true))
        col.addView(a.tv(sub, 12f, C.MUTED))
        r.addView(col, a.lp(0, WRAP, 1f))
        val st = a.pill("", C.MUTED, 0x22FFFFFF)
        status[key] = st
        r.addView(st)
        r.setOnClickListener { click() }
        return r
    }

    private fun refreshThr() {
        thrText.text = Fmt.speed(thr() * 1024L)
    }

    private fun setStatus(key: String, ok: Boolean) {
        val t = status[key] ?: return
        t.text = if (ok) "Aktif" else "Belum"
        val col = if (ok) C.GREEN else C.ORANGE
        t.setTextColor(col)
        t.background = roundBg((col and 0x00FFFFFF) or 0x22000000, a.dpf(20f), col, a.dp(1))
    }

    private fun refreshStatus() {
        setStatus("usage", UsageRepo.hasAccess(a))
        setStatus("dnd", a.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted)
        setStatus("battery", a.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(a.packageName))
        setStatus("files", AppScanner.canScanFiles(a))
        setStatus("notif", a.getSystemService(NotificationManager::class.java).areNotificationsEnabled())
    }

    private fun syncSwitch() {
        if (swMon.isChecked != Stats.running) {
            binding = true
            swMon.isChecked = Stats.running
            binding = false
        }
    }

    override fun onShow() {
        syncSwitch()
        refreshThr()
        refreshStatus()
    }

    override fun tick() {
        syncSwitch()
    }
}
