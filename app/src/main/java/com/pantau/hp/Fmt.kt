package com.pantau.hp

import java.util.Locale

object Fmt {
    private val id = Locale("id", "ID")

    fun speed(b: Long): String = when {
        b < 1024 -> "$b B/d"
        b < 1024 * 1024 -> {
            val k = b / 1024.0
            if (k < 10) String.format(id, "%.1f KB/d", k) else String.format(id, "%.0f KB/d", k)
        }
        else -> String.format(id, "%.1f MB/d", b / 1048576.0)
    }

    fun size(b: Long): String =
        if (b >= 1073741824L) String.format(id, "%.1f GB", b / 1073741824.0)
        else String.format(id, "%.0f MB", b / 1048576.0)

    fun gb(b: Long, dec: Int): String = String.format(id, "%." + dec + "f GB", b / 1073741824.0)

    fun bytes(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1048576 -> String.format(id, "%.0f KB", b / 1024.0)
        b < 1073741824L -> String.format(id, "%.1f MB", b / 1048576.0)
        else -> String.format(id, "%.2f GB", b / 1073741824.0)
    }
}
