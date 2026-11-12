package com.pantau.hp

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale

object SysInfo {
    // (persen baterai, suhu baterai dalam C; 0.0 kalau tidak diketahui)
    fun battery(ctx: Context): Pair<Int, Double> {
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val t = bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val lv = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val sc = maxOf(bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100, 1)
        return Pair(if (lv >= 0) lv * 100 / sc else 0, if (t > 0) t / 10.0 else 0.0)
    }

    fun ram(ctx: Context): Pair<Long, Long> {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Pair(mi.totalMem - mi.availMem, mi.totalMem)
    }

    fun storage(): Pair<Long, Long> {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        val free = st.availableBlocksLong * st.blockSizeLong
        return Pair(total - free, total)
    }

    fun chip(): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL else Build.HARDWARE
        return soc + " • " + Runtime.getRuntime().availableProcessors() + " inti • " + Build.SUPPORTED_ABIS[0]
    }

    fun thermal(): List<String> {
        val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: return emptyList()
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
        return (if (pick.isNotEmpty()) pick else all).take(6)
    }
}
