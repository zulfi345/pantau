package com.pantau.hp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService

object Fw {
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun blocked(c: Context): Set<String> = prefs(c).getStringSet("blocked", emptySet()) ?: emptySet()
    fun setBlocked(c: Context, s: Set<String>) {
        prefs(c).edit().putStringSet("blocked", HashSet(s)).apply()
    }

    fun enabled(c: Context): Boolean = prefs(c).getBoolean("fw_on", false)
    fun setEnabled(c: Context, v: Boolean) {
        prefs(c).edit().putBoolean("fw_on", v).apply()
    }

    fun active(c: Context): Boolean = enabled(c) && blocked(c).isNotEmpty()

    fun apply(a: Activity) {
        if (active(a)) {
            val i = VpnService.prepare(a)
            if (i != null) a.startActivityForResult(i, 77)
            else a.startService(Intent(a, PantauVpn::class.java))
        } else {
            a.stopService(Intent(a, PantauVpn::class.java))
        }
    }
}
