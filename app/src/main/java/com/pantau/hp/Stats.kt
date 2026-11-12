package com.pantau.hp

// Indeks seri: 0 = Total, 1 = WiFi, 2 = SIM
object Stats {
    const val N = 120
    val down = Array(3) { LongArray(N) }
    val up = Array(3) { LongArray(N) }
    var running = false

    fun push(i: Int, d: Long, u: Long) {
        System.arraycopy(down[i], 1, down[i], 0, N - 1)
        System.arraycopy(up[i], 1, up[i], 0, N - 1)
        down[i][N - 1] = d
        up[i][N - 1] = u
    }

    fun curDown(i: Int) = down[i][N - 1]
    fun curUp(i: Int) = up[i][N - 1]
}
