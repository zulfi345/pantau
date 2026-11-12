package com.pantau.hp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

interface Page {
    val view: View
    fun onShow() {}
    fun tick() {}
}

class MainActivity : Activity() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var pages: List<Page>
    private lateinit var firewall: FirewallPage
    private lateinit var content: FrameLayout
    private val navItems = ArrayList<LinearLayout>()
    private var cur = 0

    private val ticker = object : Runnable {
        override fun run() {
            pages[cur].tick()
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(C.BG)
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(MATCH, 0, 1f))

        firewall = FirewallPage(this)
        pages = listOf(HomePage(this), DataPage(this), ScanPage(this), firewall, SettingsPage(this))
        root.addView(buildNav(), LinearLayout.LayoutParams(MATCH, WRAP))
        setContentView(root)
        select(0)

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (!Stats.running) startForegroundService(Intent(this, MonitorService::class.java))
    }

    private fun buildNav(): LinearLayout {
        val bar = LinearLayout(this)
        bar.background = roundBg(0xFF111B28.toInt(), 0f, C.STROKE, 1)
        bar.setPadding(dp(8), dp(8), dp(8), dp(8))
        val labels = listOf("Beranda", "Data", "Scan", "Firewall", "Aturan")
        val icons = listOf("🏠", "📊", "🛡️", "🔥", "⚙️")
        for (i in labels.indices) {
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, dp(6), 0, dp(6))
            item.addView(tv(icons[i], 18f))
            item.addView(tv(labels[i], 11f, C.MUTED, true))
            item.setOnClickListener { select(i) }
            navItems.add(item)
            bar.addView(item, lp(0, WRAP, 1f, 3, 0, 3, 0))
        }
        return bar
    }

    private fun select(i: Int) {
        cur = i
        content.removeAllViews()
        content.addView(pages[i].view, FrameLayout.LayoutParams(MATCH, MATCH))
        for ((k, item) in navItems.withIndex()) {
            val label = item.getChildAt(1) as TextView
            if (k == i) {
                item.background = roundBg(C.CYAN, dpf(18f))
                label.setTextColor(C.DARK_ON_CYAN)
            } else {
                item.background = null
                label.setTextColor(C.MUTED)
            }
        }
        pages[i].onShow()
    }

    override fun onResume() {
        super.onResume()
        Alarm.stop()
        pages[cur].onShow()
        h.post(ticker)
    }

    override fun onPause() {
        h.removeCallbacks(ticker)
        super.onPause()
    }

    fun requestStorage() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + packageName))
                )
            } else {
                requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 2)
            }
        } catch (e: Exception) {
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 77) {
            if (res == RESULT_OK) {
                startService(Intent(this, PantauVpn::class.java))
            } else {
                Fw.setEnabled(this, false)
            }
            firewall.refreshHero()
        }
    }
}
