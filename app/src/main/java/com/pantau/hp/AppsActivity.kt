package com.pantau.hp

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.util.Calendar

class AppsActivity : Activity() {
    class Row(val label: String, val pkg: String, val wifi: Long, val mobile: Long) {
        val total: Long get() = wifi + mobile
    }

    private val rows = ArrayList<Row>()
    private lateinit var adapter: ArrayAdapter<Row>
    private lateinit var status: TextView
    private var days = 0

    private fun prefs() = getSharedPreferences("cfg", MODE_PRIVATE)
    private fun blockedSet(): Set<String> = prefs().getStringSet("blocked", emptySet()) ?: emptySet()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        val pad = (12 * d).toInt()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(0xFF0B1118.toInt())
        root.setPadding(pad, pad, pad, pad)

        fun btn(label: String, click: () -> Unit): Button {
            val bt = Button(this)
            bt.text = label
            bt.isAllCaps = false
            bt.setOnClickListener { click() }
            return bt
        }

        val tabs = LinearLayout(this)
        listOf("Hari ini" to 0, "7 hari" to 7, "30 hari" to 30).forEach { (t, n) ->
            tabs.addView(
                btn(t) { days = n; load() },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        root.addView(tabs)
        root.addView(btn("Beri izin akses penggunaan") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })

        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.setPadding(0, pad, 0, pad)
        root.addView(status)

        val list = ListView(this)
        adapter = object : ArrayAdapter<Row>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, rows
        ) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val v = super.getView(pos, cv, parent)
                val r = rows[pos]
                val t1 = v.findViewById<TextView>(android.R.id.text1)
                val t2 = v.findViewById<TextView>(android.R.id.text2)
                t1.text = r.label + (if (blockedSet().contains(r.pkg)) "  [DIBLOKIR]" else "")
                t1.setTextColor(Color.WHITE)
                t2.text = "Total " + Fmt.bytes(r.total) + "  •  WiFi " + Fmt.bytes(r.wifi) + "  •  SIM " + Fmt.bytes(r.mobile)
                t2.setTextColor(0xFF9AA5B1.toInt())
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val r = rows[pos]
            if (r.pkg.isNotEmpty()) {
                val blocked = blockedSet().contains(r.pkg)
                AlertDialog.Builder(this)
                    .setTitle(r.label)
                    .setItems(
                        arrayOf("Info aplikasi", "Copot instalasi", if (blocked) "Buka blokir internet" else "Blokir internet")
                    ) { _, w ->
                        when (w) {
                            0 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg)))
                            1 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + r.pkg)))
                            2 -> toggleBlock(r.pkg)
                        }
                    }
                    .show()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun toggleBlock(pkg: String) {
        val set = blockedSet().toMutableSet()
        if (!set.add(pkg)) set.remove(pkg)
        prefs().edit().putStringSet("blocked", set).apply()
        adapter.notifyDataSetChanged()
        applyVpn()
    }

    private fun applyVpn() {
        val i = VpnService.prepare(this)
        if (i != null) startActivityForResult(i, 77)
        else startService(Intent(this, PantauVpn::class.java))
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77 && res == RESULT_OK) startService(Intent(this, PantauVpn::class.java))
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(AppOpsManager::class.java)
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun load() {
        if (!hasUsageAccess()) {
            status.text = "Izin 'akses penggunaan' belum diberikan. Tekan tombol di atas, pilih Pantau HP, lalu kembali."
            return
        }
        status.text = "Memuat..."
        Thread {
            val res = query()
            runOnUiThread {
                rows.clear()
                rows.addAll(res)
                adapter.notifyDataSetChanged()
                val period = if (days == 0) "hari ini" else "$days hari terakhir"
                status.text = "Pemakaian internet per aplikasi, $period. Ketuk untuk copot, info, atau blokir internet.\nDiblokir: ${blockedSet().size} aplikasi."
            }
        }.start()
    }

    private fun query(): List<Row> {
        val pm = packageManager
        val names = HashMap<Int, Pair<String, String>>()
        for (ai in pm.getInstalledApplications(0)) {
            if (!names.containsKey(ai.uid)) names[ai.uid] = Pair(pm.getApplicationLabel(ai).toString(), ai.packageName)
        }
        val end = System.currentTimeMillis()
        val start: Long
        if (days == 0) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            start = cal.timeInMillis
        } else {
            start = end - days * 86400000L
        }
        val nsm = getSystemService(NetworkStatsManager::class.java)
        val wifi = HashMap<Int, Long>()
        val mob = HashMap<Int, Long>()
        fun collect(type: Int, into: HashMap<Int, Long>) {
            try {
                val st = nsm.querySummary(type, null, start, end)
                val bk = NetworkStats.Bucket()
                while (st.hasNextBucket()) {
                    st.getNextBucket(bk)
                    into[bk.uid] = (into[bk.uid] ?: 0L) + bk.rxBytes + bk.txBytes
                }
                st.close()
            } catch (e: Exception) {
            }
        }
        collect(ConnectivityManager.TYPE_WIFI, wifi)
        collect(ConnectivityManager.TYPE_MOBILE, mob)

        val uids = HashSet<Int>()
        uids.addAll(wifi.keys)
        uids.addAll(mob.keys)
        val out = ArrayList<Row>()
        for (u in uids) {
            val nm = names[u]
            val label = nm?.first ?: when (u) {
                -4 -> "Aplikasi yang sudah dihapus"
                -5 -> "Hotspot/tethering"
                0 -> "Sistem (root)"
                1000 -> "Sistem Android"
                else -> "UID $u"
            }
            val r = Row(label, nm?.second ?: "", wifi[u] ?: 0L, mob[u] ?: 0L)
            if (r.total > 0) out.add(r)
        }
        return out.sortedByDescending { it.total }.take(300)
    }
}
