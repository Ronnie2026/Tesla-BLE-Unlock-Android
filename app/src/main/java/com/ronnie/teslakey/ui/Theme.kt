package com.ronnie.teslakey.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Palette mirrored from prototype/style.css. All values are opaque ARGB ints. */
object Pal {
    val bg = 0xFF11151B.toInt()
    val surface = 0xFF1B2028.toInt()
    val muted = 0xFF929BA9.toInt()
    val line = 0xFF2B333F.toInt()
    val blue = 0xFF4A9CFF.toInt()
    val fg = 0xFFF3F6FD.toInt()
    val subFg = 0xFF9AA5B5.toInt()

    val primaryBg = 0xFFEEF3FF.toInt()
    val primaryFg = 0xFF111722.toInt()
    val outlineBorder = 0xFF505B6C.toInt()

    val badgeBg = 0xFF222B38.toInt()
    val badgeFg = 0xFFA8B6CA.toInt()
    val badgeBlueBg = 0xFF183253.toInt()
    val badgeBlueFg = 0xFF77B7FF.toInt()

    val statusBg = 0xFF191E26.toInt()
    val dotOn = 0xFF28D778.toInt()
    val dotOff = 0xFF9AA4B2.toInt()

    val gridBg = 0xFF202834.toInt()
    val gridBorder = 0xFF303B4A.toInt()

    val blueBtn = 0xFF297EE9.toInt()
    val secondaryBg = 0xFF252E3C.toInt()
    val secondaryFg = 0xFFDCE7F8.toInt()

    val danger = 0xFFFF969A.toInt()
    val sheetBg = 0xFF1B222C.toInt()
    val inputBg = 0xFF111822.toInt()
    val inputBorder = 0xFF424F62.toInt()
    val toastBg = 0xFFE6EDFA.toInt()
    val toastFg = 0xFF152033.toInt()
    val successBg = 0xFF173D31.toInt()

    val hotspotBg = 0xED101D30.toInt()
    val hotspotFg = 0xFF67ADFF.toInt()
    val modeSelectedBg = 0xFF18283F.toInt()
    val navBg = 0xF511151B.toInt()
    val navFg = 0xFF909BAA.toInt()
    val dim = 0x99000000.toInt()
}

fun Context.dp(v: Float): Int =
    Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics))

fun Context.sp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

/** Rounded rectangle background. Radii and stroke are given in dp. */
fun Context.roundRect(radiusDp: Float, fill: Int, strokeDp: Float = 0f, stroke: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fill)
        if (strokeDp > 0f) setStroke(dp(strokeDp), stroke)
    }

/** Wrap a background drawable in a bounded ripple so taps give feedback. */
fun ripple(bg: GradientDrawable): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, bg)

fun Context.clickableBg(radiusDp: Float, fill: Int, strokeDp: Float = 0f, stroke: Int = 0) =
    ripple(roundRect(radiusDp, fill, strokeDp, stroke))

/** Tinted vector icon as an ImageView (source vectors are white-filled). */
fun Context.icon(res: Int, sizeDp: Int, tint: Int): ImageView {
    val iv = ImageView(this)
    val d = getDrawable(res)?.mutate()
    d?.setTint(tint)
    iv.setImageDrawable(d)
    iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp.toFloat()), dp(sizeDp.toFloat()))
    iv.isFocusable = false
    return iv
}

fun Context.text(
    value: String,
    sizeSp: Float,
    color: Int = Pal.fg,
    bold: Boolean = false,
    align: Int = Gravity.START,
    letterSpacing: Float? = null
): TextView = TextView(this).apply {
    text = value
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
    setTextColor(color)
    gravity = align
    typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.DEFAULT
    if (letterSpacing != null) this.letterSpacing = letterSpacing / sizeSp
    includeFontPadding = false
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

fun Context.vBox(matchWidth: Boolean = true): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(
        if (matchWidth) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )
}

fun Context.hBox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    gravity = Gravity.CENTER_VERTICAL
}

/** 1dp hairline divider used between list rows. */
fun Context.hairline(color: Int = Pal.line, thicknessDp: Float = 1f, vertical: Boolean = false): View {
    val v = View(this)
    v.setBackgroundColor(color)
    v.layoutParams = if (vertical)
        LinearLayout.LayoutParams(dp(thicknessDp), ViewGroup.LayoutParams.MATCH_PARENT)
    else
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(thicknessDp))
    return v
}

fun Context.space(sizeDp: Float): View = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(sizeDp))
}

fun View.margins(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    val c = context
    lp.leftMargin = c.dp(left)
    lp.topMargin = c.dp(top)
    lp.rightMargin = c.dp(right)
    lp.bottomMargin = c.dp(bottom)
    layoutParams = lp
}

fun View.pad(left: Float = 0f, top: Float = 0f, right: Float = 0f, bottom: Float = 0f) {
    val c = context
    setPadding(c.dp(left), c.dp(top), c.dp(right), c.dp(bottom))
}

/** Give this view a horizontal weight, zeroing its width so weights distribute correctly. */
fun View.weight(w: Float) {
    val lp = layoutParams as? LinearLayout.LayoutParams ?: return
    lp.width = 0
    lp.weight = w
    layoutParams = lp
}

/** Force a view to span the full width of its (vertical) LinearLayout parent. */
fun View.matchWidth() {
    val lp = layoutParams as? LinearLayout.LayoutParams ?: return
    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
    layoutParams = lp
}

fun View.gone() { visibility = View.GONE }
fun View.show() { visibility = View.VISIBLE }

/** Frame container used for overlays/heroes. */
fun Context.frame(): FrameLayout = FrameLayout(this)

/** Full-width pill button matching prototype `.wide` / `.wide.blue` / `.wide.secondary`. */
fun Context.wideButton(
    label: String,
    kind: String = "light",
    enabled: Boolean = true,
    minHeightDp: Float = 49f,
    onClick: (() -> Unit)? = null
): TextView {
    val fill = when (kind) {
        "blue" -> Pal.blueBtn
        "secondary" -> Pal.secondaryBg
        else -> Pal.primaryBg
    }
    val fg = when (kind) {
        "blue" -> Color.WHITE
        "secondary" -> Pal.secondaryFg
        else -> Pal.primaryFg
    }
    val tv = text(label, 15f, fg, bold = true, align = Gravity.CENTER)
    tv.background = clickableBg(12f, if (enabled) fill else Pal.secondaryBg)
    tv.minHeight = dp(minHeightDp)
    tv.gravity = Gravity.CENTER
    tv.pad(16f, 12f, 16f, 12f)
    tv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    tv.alpha = if (enabled) 1f else 0.35f
    tv.isEnabled = enabled
    tv.isClickable = enabled && onClick != null
    if (enabled && onClick != null) tv.setOnClickListener { onClick() }
    return tv
}

/** Small circular status dot (green = connected, grey = off). */
fun Context.dot(on: Boolean, sizeDp: Float = 8f): View {
    val v = View(this)
    v.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(if (on) Pal.dotOn else Pal.dotOff)
    }
    v.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return v
}

/** Background with only the top two corners rounded (bottom sheets). */
fun Context.topRound(radiusDp: Float, fill: Int): GradientDrawable {
    val r = dp(radiusDp).toFloat()
    return GradientDrawable().apply {
        setColor(fill)
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }
}
