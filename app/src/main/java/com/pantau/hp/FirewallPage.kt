package com.pantau.hp

import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class FirewallPage(private val a: MainActivity) : Page {
    private class AppRow(val label: String, val pkg: String, val rx: Long, val tx: Long, val icon: Drawable?)

    private val inner = LinearLayout(a)
    override val view: View = a.scrollPage(inner)
    private val ui = Handler(Looper.getMainLooper())
    private val hero = a.card()
    private val heroTitle = a.tv("Firewall mati", 20f, C.TEXT, true)
    private val heroSub = a.tv("", 14f, C.MUTED)
    private val master = Switch(a)
    private val search = EditText(a)
    private val listBox = LinearLayout(a)
    private var all: List<AppRow> = emptyList()
    private var binding = false
    private var lastLoad = 0L

    init { build() }

    private fun build() {
        inner.addView(a.tv("Firewall", 22f, C.TEXT, true), a.lp(WRAP, WRAP, 0f, 0, 0, 0, 12))

        hero.orientation = LinearLayout.HORIZONTAL
        hero.gravity = Gravity.CENTER_VERTICAL
        val shield = a.tv("🛡️", 26f)
        shield.gravity = Gravity.CENTER
        shield.background = roundBg(0xFF0F2A24.toInt(), a.dpf(16f))
        hero.addView(shield, a.lp(a.dp(56), a.dp(56), 0f, 0, 0, 14, 0))
        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.addView(heroTitle)
        col.addView(heroSub)
        hero.addView(col, a.lp(0, WRAP, 1f))
        styleSwitch(master, C.GREEN)
        hero.addView(master)
        inner.addView(hero, a.lp(MATCH, WRAP))

        master.setOnCheckedChangeListener { _, checked ->
            if (!binding) {
                if (checked) {
                    if (Fw.blocked(a).isEmpty()) {
                        Toast.makeText(a, "Pilih aplikasi yang mau diblokir dulu", Toast.LENGTH_SHORT).show()
                    } else {
                        Fw.setEnabled(a, true)
                        Fw.apply(a)
                    }
                } else {
                    Fw.setEnabled(a, false)
                    Fw.apply(a)
                }
                refreshHero()
            }
        }

        search.hint = "Cari aplikasi..."
        search.setHintTextColor(C.MUTED)
        search.setTextColor(C.TEXT)
        search.textSize = 15f
        search.setSingleLine(true)
        search.setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12))
        search.background = roundBg(C.CARD, a.dpf(16f), C.STROKE, a.dp(1))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { render() }
        })
        inner.addView(search, a.lp(MATCH, WRAP, 0f, 0, 14, 0, 0))

        inner.addView(a.tv("Akses Internet", 16f, C.TEXT, true), a.lp(WRAP, WRAP, 0f, 0, 16, 0, 8))
        listBox.orientation = LinearLayout.VERTICAL
        inner.addView(listBox)

        inner.addView(
            a.button("Buka semua blokir", C.CYAN, 0x00000000, C.CYAN) {
                Fw.setBlocked(a, emptySet())
                Fw.setEnabled(a, false)
                Fw.apply(a)
                refreshHero()
                render()
            },
            a.lp(MATCH, WRAP, 0f, 0, 12, 0, 0)
        )
        inner.addView(
            a.tv("Firewall ini berjalan di HP kamu (tanpa server). Aplikasi yang diblokir tidak bisa memakai internet. Android hanya mengizinkan satu VPN aktif.", 11f, C.MUTED),
            a.lp(WRAP, WRAP, 0f, 0, 12, 0, 0)
        )
    }

    fun refreshHero() {
        val on = Fw.active(a)
        val n = Fw.blocked(a).size
        binding = true
        master.isChecked = on
        binding = false
        heroTitle.text = if (on) "Firewall aktif" else "Firewall mati"
        heroSub.text = when {
            on -> "$n aplikasi diblokir"
            n > 0 -> "$n aplikasi dipilih"
            else -> "Belum ada aplikasi dipilih"
        }
        heroSub.setTextColor(if (on) C.GREEN else C.MUTED)
        hero.background = roundBg(C.CARD, a.dpf(22f), if (on) C.GREEN else C.STROKE, a.dp(if (on) 2 else 1))
    }

    private fun load() {
        lastLoad = System.currentTimeMillis()
        Thread {
            val pm = a.packageManager
            val byPkg = HashMap<String, UsageRepo.AppUse>()
            for (u in UsageRepo.perApp(a, UsageRepo.startOfDay(30), System.currentTimeMillis())) {
                if (u.pkg.isNotEmpty()) byPkg[u.pkg] = u
            }
            val out = ArrayList<AppRow>()
            for (ai in pm.getInstalledApplications(0)) {
                if (ai.packageName == a.packageName) continue
                val u = byPkg[ai.packageName]
                if (pm.getLaunchIntentForPackage(ai.packageName) == null && u == null) continue
                val icon = try { pm.getApplicationIcon(ai) } catch (e: Exception) { null }
                out.add(AppRow(pm.getApplicationLabel(ai).toString(), ai.packageName, u?.rx ?: 0L, u?.tx ?: 0L, icon))
            }
            out.sortByDescending { it.rx + it.tx }
            ui.post {
                all = out
                render()
            }
        }.start()
    }

    private fun render() {
        listBox.removeAllViews()
        val q = search.text.toString().trim().lowercase()
        val blocked = Fw.blocked(a)
        for (r in all) {
            if (q.isNotEmpty() && !r.label.lowercase().contains(q)) continue
            listBox.addView(rowView(r, blocked.contains(r.pkg)), a.lp(MATCH, WRAP, 0f, 0, 0, 0, 8))
        }
        if (listBox.childCount == 0) listBox.addView(a.tv("Tidak ada aplikasi.", 14f, C.MUTED))
    }

    private fun rowView(r: AppRow, isOn: Boolean): View {
        val row = LinearLayout(a)
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = roundBg(C.CARD, a.dpf(16f), C.STROKE, a.dp(1))
        row.setPadding(a.dp(12), a.dp(10), a.dp(12), a.dp(10))

        val ic = ImageView(a)
        ic.setImageDrawable(r.icon)
        row.addView(ic, a.lp(a.dp(40), a.dp(40), 0f, 0, 0, 12, 0))

        val col = LinearLayout(a)
        col.orientation = LinearLayout.VERTICAL
        col.addView(a.tv(r.label, 16f, C.TEXT, true))
        val u = a.tv("", 12f, C.MUTED)
        u.text = if (r.rx + r.tx > 0) arrows(r.rx, r.tx) else "Belum ada pemakaian (30 hari)"
        col.addView(u)
        row.addView(col, a.lp(0, WRAP, 1f))

        val lbl: TextView = a.tv("Blokir", 13f, C.RED, true)
        lbl.visibility = if (isOn) View.VISIBLE else View.GONE
        row.addView(lbl, a.lp(WRAP, WRAP, 0f, 0, 0, 8, 0))

        val sw = Switch(a)
        styleSwitch(sw, C.RED)
        sw.isChecked = isOn
        sw.setOnCheckedChangeListener { _, checked ->
            val set = Fw.blocked(a).toMutableSet()
            if (checked) set.add(r.pkg) else set.remove(r.pkg)
            Fw.setBlocked(a, set)
            if (checked) Fw.setEnabled(a, true) else if (set.isEmpty()) Fw.setEnabled(a, false)
            lbl.visibility = if (checked) View.VISIBLE else View.GONE
            Fw.apply(a)
            refreshHero()
        }
        row.addView(sw)
        return row
    }

    override fun onShow() {
        refreshHero()
        if (all.isEmpty() || System.currentTimeMillis() - lastLoad > 120000) load() else render()
    }
}
