package com.pantau.hp

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

object C {
    val BG = Color.parseColor("#0B1118")
    val CARD = Color.parseColor("#141E2B")
    val STROKE = Color.parseColor("#26384D")
    val TEXT = Color.WHITE
    val MUTED = Color.parseColor("#8FA3B8")
    val CYAN = Color.parseColor("#22D3EE")
    val GREEN = Color.parseColor("#34D399")
    val ORANGE = Color.parseColor("#FB923C")
    val RED = Color.parseColor("#F87171")
    val TRACK = Color.parseColor("#223244")
    val DARK_ON_CYAN = Color.parseColor("#06222B")
}

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun roundBg(fill: Int, radius: Float, stroke: Int = 0, strokeW: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.shape = GradientDrawable.RECTANGLE
    g.setColor(fill)
    g.cornerRadius = radius
    if (strokeW > 0) g.setStroke(strokeW, stroke)
    return g
}

fun Context.tv(s: CharSequence, sp: Float, color: Int = C.TEXT, bold: Boolean = false): TextView {
    val t = TextView(this)
    t.text = s
    t.textSize = sp
    t.setTextColor(color)
    if (bold) t.typeface = Typeface.DEFAULT_BOLD
    return t
}

fun Context.lp(
    w: Int, h: Int, weight: Float = 0f,
    l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
): LinearLayout.LayoutParams {
    val p = LinearLayout.LayoutParams(w, h, weight)
    p.setMargins(dp(l), dp(t), dp(r), dp(b))
    return p
}

fun Context.card(stroke: Int = C.STROKE): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.background = roundBg(C.CARD, dpf(22f), stroke, dp(1))
    l.setPadding(dp(16), dp(16), dp(16), dp(16))
    return l
}

fun Context.pill(s: String, fg: Int, bg: Int, stroke: Int = 0): TextView {
    val t = tv(s, 12f, fg, true)
    t.setPadding(dp(10), dp(4), dp(10), dp(4))
    t.background = roundBg(bg, dpf(20f), stroke, if (stroke != 0) dp(1) else 0)
    return t
}

fun Context.button(label: String, fg: Int, bg: Int, stroke: Int = 0, click: () -> Unit): TextView {
    val t = tv(label, 14f, fg, true)
    t.gravity = Gravity.CENTER
    t.setPadding(dp(16), dp(12), dp(16), dp(12))
    t.background = roundBg(bg, dpf(24f), stroke, if (stroke != 0) dp(1) else 0)
    t.setOnClickListener { click() }
    return t
}

fun Context.scrollPage(inner: LinearLayout): ScrollView {
    val sv = ScrollView(this)
    sv.setBackgroundColor(C.BG)
    sv.isVerticalScrollBarEnabled = false
    inner.orientation = LinearLayout.VERTICAL
    inner.setPadding(dp(16), dp(16), dp(16), dp(24))
    sv.addView(inner, ViewGroup.LayoutParams(MATCH, WRAP))
    return sv
}

fun styleSwitch(sw: Switch, onColor: Int) {
    val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
    sw.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, Color.parseColor("#B0BAC5")))
    sw.trackTintList = ColorStateList(states, intArrayOf(onColor, C.TRACK))
}

fun arrows(rx: Long, tx: Long): CharSequence {
    val sb = SpannableStringBuilder()
    val a = "↓ " + Fmt.bytes(rx)
    sb.append(a)
    sb.setSpan(ForegroundColorSpan(C.GREEN), 0, a.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.append("  •  ")
    val st = sb.length
    sb.append("↑ " + Fmt.bytes(tx))
    sb.setSpan(ForegroundColorSpan(C.ORANGE), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sb
}
