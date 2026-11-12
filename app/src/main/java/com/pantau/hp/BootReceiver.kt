package com.pantau.hp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Intent.ACTION_BOOT_COMPLETED) return
        try { c.startForegroundService(Intent(c, MonitorService::class.java)) } catch (e: Exception) { }
        try {
            val blocked = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .getStringSet("blocked", emptySet()) ?: emptySet()
            if (blocked.isNotEmpty() && VpnService.prepare(c) == null) {
                c.startService(Intent(c, PantauVpn::class.java))
            }
        } catch (e: Exception) { }
    }
}
