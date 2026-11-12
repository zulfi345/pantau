package com.pantau.hp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class ScanActivity : Activity() {
    private val items = ArrayList<AppScanner.Finding>()
    private lateinit var adapter: ArrayAdapter<AppScanner.Finding>
    private lateinit var status: TextView

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

        root.addView(btn("Mulai scan sekarang") { scan() })
        root.addView(btn("Izinkan akses semua file (scan file APK)") {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName))
                )
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
            }
        })

        status = TextView(this)
        status.setTextColor(Color.WHITE)
        status.setPadding(0, pad, 0, pad)
        status.text = "Tekan 'Mulai scan'. Ini pemindai heuristik (izin, sumber pemasangan, layanan sensitif, file APK), bukan database virus."
        root.addView(status)

        val list = ListView(this)
        adapter = object : ArrayAdapter<AppScanner.Finding>(
            this, android.R.layout.simple_list_item_2, android.R.id.text1, items
        ) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup): View {
                val v = super.getView(pos, cv, parent)
                val f = items[pos]
                val t1 = v.findViewById<TextView>(android.R.id.text1)
                val t2 = v.findViewById<TextView>(android.R.id.text2)
                val lvl = if (f.score >= 6) "🔴 Berisiko" else "🟠 Waspada"
                t1.text = lvl + " (" + f.score + ")  " + f.label
                t1.setTextColor(Color.WHITE)
                t2.text = f.reasons.joinToString("; ")
                t2.setTextColor(0xFF9AA5B1.toInt())
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val f = items[pos]
            if (f.pkg.isNotEmpty()) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + f.pkg)))
            } else {
                Toast.makeText(this, f.path, Toast.LENGTH_LONG).show()
            }
        }
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun scan() {
        status.text = "Memindai..."
        Thread {
            val r = AppScanner.scan(this)
            runOnUiThread {
                items.clear()
                items.addAll(r.findings)
                adapter.notifyDataSetChanged()
                var s = "${r.apps} aplikasi dan ${r.files} file diperiksa.\n${r.findings.size} temuan mencurigakan. Ketuk untuk membuka info aplikasi (di sana bisa dicopot)."
                if (!r.filesScanned) s += "\nFile APK belum dipindai: akses semua file belum diizinkan."
                status.text = s
            }
        }.start()
    }
}
