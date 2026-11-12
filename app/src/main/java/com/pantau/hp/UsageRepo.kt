package com.pantau.hp

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.os.Process
import java.util.Calendar

object UsageRepo {
    @Volatile var todayWifi = -1L
    @Volatile var todayMobile = -1L

    class AppUse(val uid: Int, val label: String, val pkg: String) {
        var rx = 0L
        var tx = 0L
        var icon: Drawable? = null
        val total: Long get() = rx + tx
    }

    fun hasAccess(ctx: Context): Boolean {
        return try {
            val ops = ctx.getSystemService(AppOpsManager::class.java)
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
                AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    fun startOfDay(back: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -back)
        return c.timeInMillis
    }

    fun monthStart(back: Int): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.MONTH, -back)
        return c.timeInMillis
    }

    fun device(ctx: Context, type: Int, s: Long, en: Long): Long {
        return try {
            val b = ctx.getSystemService(NetworkStatsManager::class.java).querySummaryForDevice(type, null, s, en)
            b.rxBytes + b.txBytes
        } catch (ex: Exception) {
            0L
        }
    }

    fun refreshToday(ctx: Context) {
        if (!hasAccess(ctx)) {
            todayWifi = -1L
            todayMobile = -1L
            return
        }
        val s = startOfDay(0)
        val now = System.currentTimeMillis()
        todayWifi = device(ctx, ConnectivityManager.TYPE_WIFI, s, now)
        todayMobile = device(ctx, ConnectivityManager.TYPE_MOBILE, s, now)
    }

    fun perApp(ctx: Context, s: Long, en: Long): List<AppUse> {
        if (!hasAccess(ctx)) return emptyList()
        val pm = ctx.packageManager
        val names = HashMap<Int, Pair<String, String>>()
        for (ai in pm.getInstalledApplications(0)) {
            if (!names.containsKey(ai.uid)) names[ai.uid] = Pair(pm.getApplicationLabel(ai).toString(), ai.packageName)
        }
        val rxm = HashMap<Int, Long>()
        val txm = HashMap<Int, Long>()
        val nsm = ctx.getSystemService(NetworkStatsManager::class.java)
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val st = nsm.querySummary(type, null, s, en)
                val bk = NetworkStats.Bucket()
                while (st.hasNextBucket()) {
                    st.getNextBucket(bk)
                    rxm[bk.uid] = (rxm[bk.uid] ?: 0L) + bk.rxBytes
                    txm[bk.uid] = (txm[bk.uid] ?: 0L) + bk.txBytes
                }
                st.close()
            } catch (ex: Exception) {
            }
        }
        val uids = HashSet<Int>()
        uids.addAll(rxm.keys)
        uids.addAll(txm.keys)
        val out = ArrayList<AppUse>()
        for (u in uids) {
            val nm = names[u]
            val label = nm?.first ?: when (u) {
                -4 -> "Aplikasi yang sudah dihapus"
                -5 -> "Hotspot/tethering"
                0 -> "Sistem (root)"
                1000 -> "Sistem Android"
                else -> "UID $u"
            }
            val r = AppUse(u, label, nm?.second ?: "")
            r.rx = rxm[u] ?: 0L
            r.tx = txm[u] ?: 0L
            if (r.total > 0) out.add(r)
        }
        out.sortByDescending { it.total }
        return out
    }
}
