package com.pantau.hp

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

// Firewall lokal: hanya aplikasi yang diblokir yang dialirkan ke terowongan,
// dan paketnya dibuang. Tidak ada server, tidak ada data yang keluar dari HP.
class PantauVpn : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        closeTun()
        val blocked = getSharedPreferences("cfg", MODE_PRIVATE)
            .getStringSet("blocked", emptySet()) ?: emptySet()
        if (blocked.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val b = Builder()
            .setSession("Pantau Firewall")
            .setBlocking(true)
            .addAddress("10.8.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addAddress("fd00:1:2::2", 128)
            .addRoute("::", 0)
        var n = 0
        for (p in blocked) {
            try { b.addAllowedApplication(p); n++ } catch (e: Exception) { }
        }
        if (n == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        val fd = b.establish()
        tun = fd
        if (fd != null) {
            val t = Thread {
                val ins = FileInputStream(fd.fileDescriptor)
                val buf = ByteArray(32767)
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        if (ins.read(buf) < 0) break
                    }
                } catch (e: Exception) { }
            }
            t.start()
            reader = t
        }
        return START_STICKY
    }

    private fun closeTun() {
        try { tun?.close() } catch (e: Exception) { }
        tun = null
        reader?.interrupt()
        reader = null
    }

    override fun onRevoke() {
        closeTun()
        stopSelf()
    }

    override fun onDestroy() {
        closeTun()
        super.onDestroy()
    }
}
