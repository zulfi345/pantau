package com.pantau.hp

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var graph: GraphView
    private lateinit var info: TextView
    private lateinit var thr: TextView
    private lateinit var toggle: Button
    private var thermalTxt = ""
    private var n = 0

    private val refresh = object : Runnable {
        override fun run() {
            if (n++ % 3 == 0) thermalTxt = thermal()
            graph.invalidate()
            info.text = buildInfo()
            thr.text = "Batas alarm: " + Fmt.speed(thrKb() * 1024L)
            toggle.text = if (Stats.running) "Hentikan pemantauan" else "Mulai pemantauan"
            h.postDelayed(this, 1000)
        }
    }

    private fun prefs() = getSharedPreferences("cfg", MODE_PRIVATE)
    private fun thrKb() = prefs().getInt("thr_kb", 2048)
    private fun setThr(v: Int) {
        prefs().edit().putInt("thr_kb", v.coerceIn(256, 102400)).apply()
    }

    private fun startMonitor() {
        startForegroundService(Intent(this, MonitorService::class.java))
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val mp = ViewGroup.LayoutParams.MATCH_PARENT
        val wc = ViewGroup.LayoutParams.WRAP_CONTENT

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(12), dp(12), dp(12))

        fun tv(size: Float): TextView {
            val t = TextView(this)
            t.setTextColor(Color.WHITE)
            t.textSize = size
            return t
        }

        fun btn(label: String, click: () -> Unit): Button {
            val bt = Button(this)
            bt.text = label
            bt.isAllCaps = false
            bt.setOnClickListener { click() }
            return bt
        }

        graph = GraphView(this)
        root.addView(graph, LinearLayout.LayoutParams(mp, dp(220)))

        val tabs = LinearLayout(this)
        listOf("Total", "WiFi", "SIM").forEachIndexed { i, t ->
            tabs.addView(btn(t) { graph.idx = i }, LinearLayout.LayoutParams(0, wc, 1f))
        }
        root.addView(tabs)

        info = tv(14f)
        info.setPadding(0, dp(8), 0, dp(8))
        root.addView(info)

        thr = tv(15f)
        root.addView(thr)

        val row = LinearLayout(this)
        row.addView(btn("−") { setThr(thrKb() - 512) }, LinearLayout.LayoutParams(0, wc, 1f))
        row.addView(btn("+") { setThr(thrKb() + 512) }, LinearLayout.LayoutParams(0, wc, 1f))
        root.addView(row)

        root.addView(btn("Matikan alarm") { Alarm.stop() })
        root.addView(btn("Pemakaian & kelola aplikasi (blokir internet)") {
            startActivity(Intent(this, AppsActivity::class.java))
        })
        root.addView(btn("Scan keamanan (virus/malware)") {
            startActivity(Intent(this, ScanActivity::class.java))
        })
        toggle = btn("") {
            if (Stats.running) {
                stopService(Intent(this, MonitorService::class.java))
            } else {
                startMonitor()
            }
        }
        root.addView(toggle)
        root.addView(btn("Izin Jangan Ganggu (DND)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        })
        root.addView(btn("Pengaturan penghemat baterai") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        val sv = ScrollView(this)
        sv.setBackgroundColor(0xFF0B1118.toInt())
        sv.addView(root)
        setContentView(sv)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!Stats.running) startMonitor()
    }

    override fun onResume() {
        super.onResume()
        Alarm.stop()
        h.post(refresh)
    }

    override fun onPause() {
        h.removeCallbacks(refresh)
        super.onPause()
    }

    private fun buildInfo(): String {
        val sb = StringBuilder()

        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val lv = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = maxOf(bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100, 1)
        val tempTxt = if (t > 0) String.format(Locale.US, "%.1f°C", t / 10.0) else "-"
        sb.append("Baterai: ${if (lv >= 0) lv * 100 / sc else 0}%   Suhu: $tempTxt\n\n")

        val names = listOf("Total", "WiFi", "SIM")
        for (i in 0..2) {
            sb.append(names[i] + "  ↓ " + Fmt.speed(Stats.curDown(i)) + "  ↑ " + Fmt.speed(Stats.curUp(i)) + "\n")
        }

        val am = getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        sb.append("\nRAM: " + Fmt.size(mi.totalMem - mi.availMem) + " / " + Fmt.size(mi.totalMem) + "\n")

        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        sb.append("Penyimpanan: " + Fmt.size(total - free) + " / " + Fmt.size(total) + "\n")

        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL else Build.HARDWARE
        sb.append("Chip: $soc (${Build.HARDWARE}), ${Runtime.getRuntime().availableProcessors()} inti, ${Build.SUPPORTED_ABIS[0]}\n\n")
        sb.append(thermalTxt)
        return sb.toString()
    }

    private fun thermal(): String {
        val none = "Suhu chip: tidak bisa dibaca di HP ini"
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return none
        val re = Regex("cpu|gpu|soc|skin|tsens|battery|cluster", RegexOption.IGNORE_CASE)
        val all = ArrayList<String>()
        val pick = ArrayList<String>()
        for (z in zones.sortedBy { it.name }) {
            try {
                val type = File(z, "type").readText().trim()
                val raw = File(z, "temp").readText().trim().toFloat()
                val c = if (raw > 1000f) raw / 1000f else raw
                if (c < 1f || c > 150f) continue
                val line = String.format(Locale.US, "%s: %.1f°C", type, c)
                all.add(line)
                if (re.containsMatchIn(type)) pick.add(line)
            } catch (e: Exception) {
            }
        }
        val use = if (pick.isNotEmpty()) pick else all
        return if (use.isEmpty()) none else "Suhu chip:\n" + use.take(6).joinToString("\n")
    }
}
