package com.pantau.hp

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper

// Bunyi lewat jalur ALARM, jadi tetap berbunyi walau nada dering dimatikan.
object Alarm {
    private var ringtone: Ringtone? = null
    private val h = Handler(Looper.getMainLooper())

    fun start(ctx: Context) {
        stop()
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val r = RingtoneManager.getRingtone(ctx.applicationContext, uri) ?: return
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
        r.play()
        ringtone = r
        h.postDelayed({ stop() }, 15000)
    }

    fun stop() {
        h.removeCallbacksAndMessages(null)
        try { ringtone?.stop() } catch (e: Exception) { }
        ringtone = null
    }
}
