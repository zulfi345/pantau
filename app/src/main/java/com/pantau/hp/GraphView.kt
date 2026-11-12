package com.pantau.hp

import android.content.Context
import android.graphics.Canvas
import android.view.View

class GraphView(ctx: Context) : View(ctx) {
    var idx = 0

    override fun onDraw(c: Canvas) {
        GraphRenderer.draw(c, width.toFloat(), height.toFloat(), idx, resources.displayMetrics.density, false)
    }
}
