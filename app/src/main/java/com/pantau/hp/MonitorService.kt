package com.pantau.hp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock

class MonitorService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private var pRx = 0L
    private var pTx = 0L
    private var pMrx = 0L
    private var pMtx = 0L
    private var pT = 0L
    private var ema = 0.0
    private var hits = 0
    private var lastAlarm = 0L
    private var tickCount = 0

    private val loop = object : Runnable {
        override fun run() {
            tick()
            h.postDelayed(this, 1000)
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("pantau", "Pemantauan", NotificationManager.IMPORTANCE_LOW)
        )
        val alert = NotificationChannel("alarm", "Alarm lonjakan", NotificationManager.IMPORTANCE_HIGH)
        alert.setSound(null, null)
        alert.enableVibration(true)
        alert.setBypassDnd(true)
        nm.createNotificationChannel(alert)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notif("Memulai pemantauan...")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
        if (!Stats.running) {
            Stats.running = true
            h.post(loop)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        h.removeCallbacks(loop)
        Stats.running = false
        super.onDestroy()
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )

    private fun notif(text: String): Notification =
        Notification.Builder(this, "pantau")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Pantau HP")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .build()

    private fun tick() {
        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val mrx = TrafficStats.getMobileRxBytes().coerceAtLeast(0)
        val mtx = TrafficStats.getMobileTxBytes().coerceAtLeast(0)
        if (rx < 0 || tx < 0) return

        if (pT != 0L) {
            val dt = (now - pT) / 1000.0
            if (dt > 0) {
                val d = ((rx - pRx) / dt).toLong().coerceAtLeast(0)
                val u = ((tx - pTx) / dt).toLong().coerceAtLeast(0)
                val md = ((mrx - pMrx) / dt).toLong().coerceAtLeast(0)
                val mu = ((mtx - pMtx) / dt).toLong().coerceAtLeast(0)
                Stats.push(0, d, u)
                Stats.push(1, (d - md).coerceAtLeast(0), (u - mu).coerceAtLeast(0))
                Stats.push(2, md, mu)
                checkSpike(d + u)
                getSystemService(NotificationManager::class.java).notify(
                    1, notif("↓ " + Fmt.speed(d) + "   ↑ " + Fmt.speed(u))
                )
            }
        }
        pRx = rx; pTx = tx; pMrx = mrx; pMtx = mtx; pT = now

        tickCount++
        if (tickCount % 10 == 0) checkHeat()
        if (tickCount % 60 == 1) {
            val ctx = applicationContext
            Thread {
                UsageRepo.refreshToday(ctx)
                h.post { SpeedWidget.updateAll(ctx) }
            }.start()
        }
    }

    private var lastHeat = 0L

    private fun checkHeat() {
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val t = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0
        val now = SystemClock.elapsedRealtime()
        if (t >= 45.0 && now - lastHeat > 300000) {
            lastHeat = now
            Alarm.start(this)
            val n = Notification.Builder(this, "alarm")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("HP terlalu panas!")
                .setContentText("Suhu baterai " + String.format("%.1f", t) + "°C")
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(3, n)
        }
    }

    private fun checkSpike(s: Long) {
        val thr = getSharedPreferences("cfg", MODE_PRIVATE).getInt("thr_kb", 2048) * 1024L
        val spike = s > thr && s > ema * 4
        ema = if (ema == 0.0) s.toDouble() else ema * 0.95 + s * 0.05
        hits = if (spike) hits + 1 else 0
        val now = SystemClock.elapsedRealtime()
        if (hits >= 3 && now - lastAlarm > 60000) {
            lastAlarm = now
            hits = 0
            Alarm.start(this)
            val n = Notification.Builder(this, "alarm")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Lonjakan internet!")
                .setContentText("Kecepatan " + Fmt.speed(s) + " (batas " + Fmt.speed(thr) + ")")
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(2, n)
        }
    }
}
