package com.ronnie.teslakey.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ronnie.teslakey.protocol.VehicleCommand
import com.ronnie.teslakey.protocol.TeslaProtocol
import com.ronnie.teslakey.R
import com.ronnie.teslakey.ble.TeslaBle
import com.ronnie.teslakey.ui.MainActivity.Page

// ---------------------------------------------------------------------------
// Shared building blocks
// ---------------------------------------------------------------------------

internal fun MainActivity.header(title: String, subtitle: String = "", back: Boolean = false): View {
    val bar = hBox()
    bar.gravity = Gravity.CENTER_VERTICAL
    bar.pad(25f, 25f, 25f, 0f)
    if (back) {
        val b = icon(R.drawable.ic_arrow_left, 20, Pal.fg)
        val wrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36f), dp(36f))
            addView(b, FrameLayout.LayoutParams(dp(20f), dp(20f), Gravity.CENTER))
            background = clickableBg(8f, Color.TRANSPARENT)
            setOnClickListener { navigate(Page.ME) }
        }
        bar.addView(wrap)
        bar.addView(titleBlock(title, subtitle).apply { weight(1f); margins(left = 15f) })
    } else {
        bar.addView(titleBlock(title, subtitle).apply { weight(1f) })
        if (page == Page.CAR) {
            bar.addView(vBox(matchWidth = false).apply {
                layoutParams = LinearLayout.LayoutParams(dp(72f), dp(56f))
                gravity = Gravity.CENTER
                val color = if (isConnected) Pal.blue else Pal.muted
                addView(icon(R.drawable.ic_bluetooth, 25, color))
                addView(text(if (isConnected) "已连接" else "未连接", 11f, color).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                background = clickableBg(12f, if (isConnected) Pal.badgeBlueBg else Color.TRANSPARENT)
                contentDescription = if (isConnected) "蓝牙已连接" else "蓝牙未连接，点击连接车辆"
                setOnClickListener { onConnectionTap() }
            })
        }
    }
    return bar
}

private fun MainActivity.titleBlock(title: String, subtitle: String): View {
    val box = vBox().apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }
    box.addView(text(title, if (subtitle.isEmpty()) 27f else 24f, Pal.fg, bold = true))
    if (subtitle.isNotEmpty()) box.addView(text(subtitle, 13f, Pal.subFg).apply { margins(top = 4f) })
    return box
}

internal fun MainActivity.sectionLabel(label: String): TextView =
    text(label, 12f, 0xFF94A0B2.toInt(), letterSpacing = 1f).apply { margins(top = 26f, bottom = 12f) }

internal fun MainActivity.smallNote(msg: String): TextView =
    text(msg, 12f, 0xFF93A0B2.toInt()).apply { setLineSpacing(dp(6f).toFloat(), 1f) }

private fun MainActivity.hint(msg: String): TextView =
    text(msg, 11f, Pal.muted, align = Gravity.CENTER).apply { matchWidth(); margins(top = 14f, bottom = 8f) }

internal fun MainActivity.badge(label: String, blue: Boolean = false): TextView =
    text(label, 10f, if (blue) Pal.badgeBlueFg else Pal.badgeFg).apply {
        background = roundRect(14f, if (blue) Pal.badgeBlueBg else Pal.badgeBg)
        pad(10f, 4f, 10f, 4f)
    }

/** A tappable list row with icon, title, optional subtitle and trailing label + chevron. */
internal fun MainActivity.listRow(
    iconRes: Int,
    title: String,
    sub: String = "",
    trailing: String = "",
    danger: Boolean = false,
    onClick: () -> Unit
): View {
    val container = vBox()
    val row = hBox()
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(0, dp(18f), 0, dp(18f))
    row.background = clickableBg(8f, Color.TRANSPARENT)
    if (iconRes != 0) {
        row.addView(icon(iconRes, 22, if (danger) Pal.danger else Pal.fg))
        row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(13f), 1) })
    }
    val tb = vBox().apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    tb.addView(text(title, 15f, if (danger) Pal.danger else Pal.fg))
    if (sub.isNotEmpty()) tb.addView(text(sub, 12f, Pal.muted).apply { margins(top = 3f) })
    row.addView(tb)
    val trail = hBox()
    trail.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    if (trailing.isNotEmpty()) trail.addView(text(trailing, 12f, 0xFF9AA6B7.toInt()))
    val chev = icon(R.drawable.ic_chevron_right, 14, 0xFF9AA6B7.toInt())
    chev.alpha = 0.45f
    trail.addView(chev.apply { margins(left = 6f) })
    row.addView(trail)
    row.setOnClickListener { onClick() }
    container.addView(row)
    container.addView(hairline(Pal.line, 1f))
    return container
}

internal fun MainActivity.panel(build: LinearLayout.() -> Unit): LinearLayout {
    val p = vBox()
    p.background = roundRect(18f, Pal.surface, 1f, Pal.line)
    p.pad(20f, 20f, 20f, 20f)
    p.build()
    return p
}

private fun MainActivity.emptyState(iconRes: Int, title: String, body: String, actionLabel: String, action: () -> Unit): View {
    val box = vBox()
    box.gravity = Gravity.CENTER_HORIZONTAL
    box.pad(25f, 70f, 25f, 40f)
    val ic = icon(iconRes, 52, Pal.fg)
    ic.alpha = 0.5f
    box.addView(ic.apply { margins(bottom = 18f) })
    box.addView(text(title, 21f, Pal.fg, bold = true, align = Gravity.CENTER))
    box.addView(text(body, 13f, Pal.muted, align = Gravity.CENTER).apply { setLineSpacing(dp(6f).toFloat(), 1f); margins(top = 8f, bottom = 30f) })
    box.addView(wideButton(actionLabel, "blue") { action() })
    return box
}

private fun MainActivity.lockState(): Boolean = controller.observation?.lock != 0
private fun MainActivity.trunkOpen(): Boolean = controller.observation?.trunk in listOf(1, 2)

// ---------------------------------------------------------------------------
// Car screen
// ---------------------------------------------------------------------------

internal fun MainActivity.carScreen(): View {
    val root = vBox()
    if (skin == "official") {
        root.addView(header("控制"))
        root.addView(officialHero())
        root.addView(climateControls())
        if (controller.error.isNotEmpty()) root.addView(text(controller.error, 12f, Pal.danger).apply { pad(25f, 4f, 25f, 12f) })
        else if (controller.busy) root.addView(text(controller.status, 12f, Pal.muted).apply { pad(25f, 4f, 25f, 12f) })
        return root
    }
    root.addView(header("我的 Model Y", "特斯拉蓝牙钥匙"))
    root.addView(hero())
    root.addView(statusRow())

    if (skin == "classic") root.addView(classicGrid())
    else {
        root.addView(cockpitActions())
        root.addView(hint(if (isConnected) "点击车身标记进行操作" else "连接车辆后可使用控制功能"))
    }

    root.addView(climateControls())

    val note = when {
        controller.error.isNotEmpty() -> text(controller.error, 12f, Pal.danger, align = Gravity.CENTER)
        controller.busy -> text(controller.status, 12f, Pal.muted, align = Gravity.CENTER)
        else -> null
    }
    if (note != null) root.addView(note.apply { matchWidth(); margins(left = 25f, top = 6f, right = 25f, bottom = 16f) })
    return root
}

private fun MainActivity.hero(): View {
    val h = resources.displayMetrics.heightPixels
    val heroHeight = (if (skin == "classic") h * 0.32f else h * 0.44f).toInt()
    val hero = frame()
    hero.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heroHeight)
    hero.margins(left = 24f, right = 24f)

    val car = ImageView(this)
    car.setImageDrawable(getDrawable(R.drawable.car_top))
    car.scaleType = ImageView.ScaleType.FIT_CENTER
    hero.addView(car, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

    if (skin != "classic") {
        hero.addView(hotspot("前备箱", com.ronnie.teslakey.protocol.VehicleObservation.closureLabel(if(isConnected)controller.observation?.frunk else null), Gravity.TOP or Gravity.CENTER_HORIZONTAL, 9f) { onFrunk() })
        hero.addView(hotspot("后备箱", com.ronnie.teslakey.protocol.VehicleObservation.closureLabel(if(isConnected)controller.observation?.trunk else null), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 5f) { onTrunk() })
    }
    return hero
}

private fun MainActivity.hotspot(label: String, span: String, gravity: Int, edgeDp: Float, onClick: () -> Unit): View {
    val box = vBox(matchWidth = false)
    box.gravity = Gravity.CENTER_HORIZONTAL
    box.background = roundRect(10f, Pal.hotspotBg, 1f, Pal.blue)
    box.pad(13f, 6f, 13f, 6f)
    box.addView(text(label, 13f, Pal.fg, align = Gravity.CENTER))
    box.addView(text(span, 12f, Pal.hotspotFg, align = Gravity.CENTER))
    val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, gravity)
    if (gravity and Gravity.TOP == Gravity.TOP) lp.topMargin = dp(edgeDp) else lp.bottomMargin = dp(edgeDp)
    box.layoutParams = lp
    box.alpha = if (controlsEnabled) 1f else 0.35f
    box.isEnabled = controlsEnabled
    box.isClickable = controlsEnabled
    if (controlsEnabled) box.setOnClickListener { onClick() }
    return box
}

private fun MainActivity.statusRow(): View {
    val row = hBox()
    row.gravity = Gravity.CENTER
    row.background = clickableBg(16f, Pal.statusBg, 1f, Pal.line)
    row.minimumHeight = dp(43f)
    row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    row.margins(left = 39f, top = 9f, right = 39f, bottom = 17f)
    row.pad(12f, 8f, 12f, 8f)

    row.addView(dot(isConnected))
    row.addView(text(if (isConnected) "蓝牙已连接" else "蓝牙未连接", 13f, Pal.fg).apply { margins(left = 7f) })
    row.addView(View(this).apply {
        setBackgroundColor(Pal.line)
        layoutParams = LinearLayout.LayoutParams(dp(1f), dp(15f)).apply { leftMargin = dp(15f); rightMargin = dp(15f) }
    })
    row.addView(icon(if (lockState()) R.drawable.ic_lock else R.drawable.ic_unlock, 15, Pal.fg))
    val lockLabel = if (!isConnected) "连接车辆" else com.ronnie.teslakey.protocol.VehicleObservation.lockLabel(controller.observation?.lock)
    row.addView(text(lockLabel, 13f, Pal.fg).apply { margins(left = 5f) })
    row.setOnClickListener { onConnectionTap() }
    return row
}

private fun MainActivity.cockpitActions(): View {
    val row = hBox()
    row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    row.margins(left = 25f, right = 25f)

    val unlock = actionButton("解锁", R.drawable.ic_unlock, primary = true) { onUnlock() }
    unlock.weight(1.7f)
    val lock = actionButton("锁车", R.drawable.ic_lock, primary = false) { onLock() }
    lock.weight(1f)
    row.addView(unlock)
    row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(10f), 1) })
    row.addView(lock)
    return row
}

private fun MainActivity.actionButton(label: String, iconRes: Int, primary: Boolean, onClick: () -> Unit): View {
    val box = hBox()
    box.gravity = Gravity.CENTER
    box.layoutParams = LinearLayout.LayoutParams(0, dp(56f))
    box.background = if (primary) clickableBg(17f, Pal.primaryBg)
    else clickableBg(17f, Color.TRANSPARENT, 1f, Pal.outlineBorder)
    val fg = if (primary) Pal.primaryFg else Pal.fg
    box.addView(icon(iconRes, 22, fg))
    box.addView(text(label, 17f, fg, bold = primary).apply { margins(left = 8f) })
    box.alpha = if (controlsEnabled) 1f else 0.35f
    box.isEnabled = controlsEnabled
    box.isClickable = controlsEnabled
    if (controlsEnabled) box.setOnClickListener { onClick() }
    return box
}

private fun MainActivity.classicGrid(): View {
    val grid = vBox()
    grid.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    grid.margins(left = 25f, top = 22f, right = 25f, bottom = 8f)

    val r1 = hBox()
    r1.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    r1.addView(gridButton("打开前备箱", R.drawable.ic_frunk) { onFrunk() }.apply { weight(1f) })
    r1.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12f), 1) })
    r1.addView(gridButton(if (trunkOpen()) "关闭后备箱" else "打开后备箱", R.drawable.ic_trunk) { onTrunk() }.apply { weight(1f) })

    val r2 = hBox()
    r2.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    r2.margins(top = 12f)
    r2.addView(gridButton("解锁", R.drawable.ic_unlock) { onUnlock() }.apply { weight(1f) })
    r2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12f), 1) })
    r2.addView(gridButton("锁车", R.drawable.ic_lock) { onLock() }.apply { weight(1f) })

    grid.addView(text("前备箱：${com.ronnie.teslakey.protocol.VehicleObservation.closureLabel(if(isConnected)controller.observation?.frunk else null)}  ·  后备箱：${com.ronnie.teslakey.protocol.VehicleObservation.closureLabel(if(isConnected)controller.observation?.trunk else null)}", 12f, Pal.muted).apply { margins(bottom = 10f) })
    grid.addView(r1)
    grid.addView(r2)
    return grid
}

private fun MainActivity.gridButton(label: String, iconRes: Int, onClick: () -> Unit): View {
    val box = vBox()
    box.gravity = Gravity.CENTER
    box.layoutParams = LinearLayout.LayoutParams(0, dp(116f))
    box.background = clickableBg(17f, Pal.gridBg, 1f, Pal.gridBorder)
    box.addView(icon(iconRes, 28, Pal.fg).apply { margins(bottom = 13f) })
    box.addView(text(label, 15f, Pal.fg, align = Gravity.CENTER))
    box.alpha = if (controlsEnabled) 1f else 0.35f
    box.isEnabled = controlsEnabled
    box.isClickable = controlsEnabled
    if (controlsEnabled) box.setOnClickListener { onClick() }
    return box
}

// ---------------------------------------------------------------------------
// Me screen
// ---------------------------------------------------------------------------

internal fun MainActivity.meScreen(): View {
    val root = vBox()
    root.addView(header("我的", "每一次出发，都从这里开始"))
    val body = vBox()
    body.pad(24f, 8f, 24f, 24f)

    body.addView(listRow(R.drawable.ic_plus_lg, "添加钥匙", "搜索并配对你的车辆") { navigate(Page.ADD) })
    body.addView(listRow(R.drawable.ic_palette, "换肤", "选择适合你的控制方式", when(skin){"official"->"官方版";"cockpit"->"车机版";else->"功能版"}) { navigate(Page.SKIN) })
    body.addView(listRow(R.drawable.ic_key, "帮助", "如何添加钥匙？") { showAddKeyHelp() })

    body.addView(text("特斯拉蓝牙钥匙 · 安卓版", 10f, 0xFF68778C.toInt(), align = Gravity.CENTER)
        .apply { matchWidth(); setLineSpacing(dp(4f).toFloat(), 1f); margins(top = 32f) })

    root.addView(body)
    return root
}

// ---------------------------------------------------------------------------
// Skin screen
// ---------------------------------------------------------------------------

internal fun MainActivity.skinScreen(): View {
    val root = vBox()
    root.addView(header("换肤", back = true))
    val body = vBox()
    body.pad(24f, 8f, 24f, 24f)
    body.addView(text("选择你喜欢的车辆控制界面。", 14f, Pal.muted))
    body.addView(modeOption(R.drawable.ic_car_front, "官方版", "大幅俯视图 · 在车身上直接操作", skin == "official") { chooseSkin("official") }.apply { margins(top = 16f) })
    body.addView(modeOption(R.drawable.ic_car_front, "车机版", "车辆俯视图 · 点击部位控制", skin == "cockpit") { chooseSkin("cockpit") }.apply { margins(top = 16f) })
    body.addView(modeOption(R.drawable.ic_broadcast, "功能版", "经典大按钮 · 常用操作一目了然", skin == "classic") { chooseSkin("classic") }.apply { margins(top = 12f) })
    body.addView(smallNote("三款皮肤共用车辆状态，切换不影响钥匙和连接。").apply { margins(top = 16f) })
    body.addView(wideButton("查看车辆页面", "light") { navigate(Page.CAR) }.apply { margins(top = 24f) })
    root.addView(body)
    return root
}

private fun MainActivity.modeOption(iconRes: Int, title: String, sub: String, selected: Boolean, onClick: () -> Unit): View {
    val row = hBox()
    row.gravity = Gravity.CENTER_VERTICAL
    row.background = clickableBg(15f, if (selected) Pal.modeSelectedBg else Color.TRANSPARENT, 1f, if (selected) Pal.blue else Pal.line)
    row.pad(16f, 18f, 16f, 18f)
    row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    row.addView(icon(iconRes, 22, if (selected) Pal.blue else Pal.fg))
    val tb = vBox().apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    tb.addView(text(title, 15f, Pal.fg).apply { margins(left = 13f) })
    tb.addView(text(sub, 12f, Pal.muted).apply { margins(left = 13f, top = 5f) })
    row.addView(tb)
    val choice = View(this)
    choice.background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        if (selected) { setColor(Pal.blue); setStroke(dp(5f), 0xFF1E416F.toInt()) }
        else { setColor(Color.TRANSPARENT); setStroke(dp(1f), 0xFF627084.toInt()) }
    }
    row.addView(choice, LinearLayout.LayoutParams(dp(19f), dp(19f)))
    row.setOnClickListener { onClick() }
    return row
}

// ---------------------------------------------------------------------------
// Settings screen
// ---------------------------------------------------------------------------

internal fun MainActivity.settingsScreen(): View {
    val root = vBox()
    root.addView(header("设置", back = true))
    val body = vBox()
    body.pad(24f, 8f, 24f, 24f)

    body.addView(sectionLabel("操作偏好"))
    body.addView(switchRow(R.drawable.ic_shield_check, "打开前备箱前确认", "减少误触造成的意外开启", confirmFrunk) { setConfirm(!confirmFrunk) })
    body.addView(listRow(R.drawable.ic_palette, "换肤", "", when(skin){"official"->"官方版";"cockpit"->"车机版";else->"功能版"}) { navigate(Page.SKIN) })

    body.addView(sectionLabel("关于"))
    val about = hBox()
    about.setPadding(0, dp(18f), 0, dp(18f))
    val tb = vBox().apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    tb.addView(text("特斯拉蓝牙钥匙 · 安卓版", 15f, Pal.fg))
    tb.addView(text("蓝牙车辆控制", 12f, Pal.muted).apply { margins(top = 3f) })
    about.addView(tb)
    body.addView(about)
    body.addView(hairline(Pal.line, 1f))
    body.addView(smallNote("通过蓝牙连接并控制你的车辆，请确认车型年份、命令权限及车端反馈。").apply { margins(top = 16f) })

    root.addView(body)
    return root
}

private fun MainActivity.switchRow(iconRes: Int, title: String, sub: String, on: Boolean, onClick: () -> Unit): View {
    val container = vBox()
    val row = hBox()
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(0, dp(18f), 0, dp(18f))
    row.addView(icon(iconRes, 22, Pal.fg))
    val tb = vBox().apply { layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
    tb.addView(text(title, 15f, Pal.fg).apply { margins(left = 13f) })
    tb.addView(text(sub, 12f, Pal.muted).apply { margins(left = 13f, top = 3f) })
    row.addView(tb)
    row.addView(switchView(on))
    row.setOnClickListener { onClick() }
    container.addView(row)
    container.addView(hairline(Pal.line, 1f))
    return container
}

private fun MainActivity.switchView(on: Boolean): View {
    val track = FrameLayout(this)
    track.layoutParams = LinearLayout.LayoutParams(dp(40f), dp(24f))
    track.background = GradientDrawable().apply { cornerRadius = dp(16f).toFloat(); setColor(if (on) 0xFF348CFF.toInt() else 0xFF485466.toInt()) }
    val knob = View(this)
    knob.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
    val lp = FrameLayout.LayoutParams(dp(18f), dp(18f), if (on) Gravity.END or Gravity.CENTER_VERTICAL else Gravity.START or Gravity.CENTER_VERTICAL)
    lp.marginStart = dp(3f); lp.marginEnd = dp(3f)
    track.addView(knob, lp)
    return track
}

// ---------------------------------------------------------------------------
// Keys screen
// ---------------------------------------------------------------------------

internal fun MainActivity.keysScreen(): View {
    val root = vBox()
    root.addView(header("蓝牙钥匙", back = true))
    val body = vBox()
    body.pad(24f, 8f, 24f, 24f)

    if (!hasKey) {
        body.addView(emptyState(R.drawable.ic_key, "暂无蓝牙钥匙", "在车旁完成配对，即可使用蓝牙控制。", "添加蓝牙钥匙") { navigate(Page.ADD) })
        root.addView(body)
        return root
    }

    body.addView(panel {
        addView(text("我的 Model Y", 19f, Pal.fg, bold = true))
        addView(text("此手机的蓝牙钥匙", 13f, Pal.muted).apply { margins(top = 8f, bottom = 16f) })
        val line = hBox()
        line.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        line.addView(dot(isConnected))
        line.addView(text(if (isConnected) "已连接" else "未连接", 13f, Pal.fg).apply { margins(left = 7f) })
        addView(line)
    })
    body.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16f)) })
    body.addView(listRow(R.drawable.ic_bluetooth, if (isConnected) "断开连接" else "连接车辆") { onConnectionTap() })
    body.addView(listRow(R.drawable.ic_key, "重新配对", "搜索车辆并重新添加手机钥匙") { navigate(Page.ADD) })
    body.addView(listRow(0, "移除此手机的钥匙", danger = true) { onRemoveKey() })
    body.addView(smallNote("移除手机记录不会自动删除车辆端的钥匙。真实使用时，还需在车机「车锁」中删除对应钥匙。").apply { margins(top = 16f) })

    root.addView(body)
    return root
}

// ---------------------------------------------------------------------------
// Add / pairing wizard
// ---------------------------------------------------------------------------

internal fun MainActivity.addScreen(): View {
    // Advance to success as soon as the handshake completes.
    if (pairStep == 2 && controller.connected) pairStep = 3

    val root = vBox()
    root.addView(header("添加蓝牙钥匙", back = true))
    val body = vBox()
    body.pad(24f, 8f, 24f, 24f)
    body.addView(steps())

    when (pairStep) {
        0 -> body.addView(stepVin())
        1 -> body.addView(stepScan())
        2 -> body.addView(stepPair())
        else -> body.addView(stepSuccess())
    }
    root.addView(body)
    return root
}

private fun MainActivity.steps(): View {
    val row = hBox()
    row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    row.margins(bottom = 25f)
    for (i in 0..2) {
        val done = i == 0 || pairStep > i - 1
        val bar = View(this)
        bar.background = roundRect(2f, if (done) Pal.blue else Pal.gridBorder)
        bar.layoutParams = LinearLayout.LayoutParams(0, dp(3f)).apply { weight = 1f }
        row.addView(bar)
        if (i < 2) row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(8f), 1) })
    }
    return row
}

private fun MainActivity.stepVin(): View {
    val box = vBox()
    box.addView(text("先找到你的车辆", 24f, Pal.fg, bold = true))
    box.addView(text("请靠近车辆，并准备一张已授权的实体钥匙卡。", 13f, Pal.muted).apply { margins(top = 8f) })
    box.addView(text("车辆 VIN", 13f, Pal.fg).apply { margins(top = 20f, bottom = 8f) })

    val input = EditText(this)
    input.setText(vinDraft)
    input.setSelection(vinDraft.length.coerceAtMost(17))
    input.hint = "输入 17 位车辆识别码"
    input.setHintTextColor(Pal.muted)
    input.setTextColor(Color.WHITE)
    input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15f)
    input.setSingleLine(true)
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    input.filters = arrayOf(InputFilter.LengthFilter(17))
    input.background = roundRect(10f, Pal.inputBg, 1f, Pal.inputBorder)
    input.pad(14f, 14f, 14f, 14f)
    input.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    input.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { vinDraft = s?.toString() ?: "" }
    })
    box.addView(input)

    if (scanError.isNotEmpty()) box.addView(text(scanError, 12f, Pal.danger).apply { margins(top = 8f) })
    else box.addView(smallNote("VIN 可在车机「软件」页面或前挡风玻璃处查看。").apply { margins(top = 8f) })

    box.addView(wideButton("搜索车辆", "blue") { onSearchVehicle() }.apply { margins(top = 20f) })
    box.addView(smallNote("需开启手机蓝牙、定位服务，并允许「附近设备」和位置权限，否则部分手机会扫不到车辆。").apply { margins(top = 12f) })
    box.addView(diagnosticPanel())
    return box
}

// ---------------------------------------------------------------------------
// Diagnostic scan (verification only — not used for pairing)
// ---------------------------------------------------------------------------

private fun MainActivity.diagnosticPanel(): View {
    val box = vBox()
    box.margins(top = 28f)
    box.addView(sectionLabel("诊断 · 不用于配对"))
    box.addView(wideButton(if (controller.diagnosing) "停止诊断扫描" else "诊断：扫描附近全部 BLE", "secondary") {
        if (controller.diagnosing) onStopDiagnostic() else onDiagnosticScan()
    })
    if (controller.diagnosing) box.addView(hint("正在监听附近 BLE 广播…"))
    if (controller.diagnosticFound.isEmpty() && !controller.diagnosing) {
        box.addView(smallNote("不过滤地监听附近所有 BLE 广播，用于确认手机射频与扫描是否正常。真车在附近时会广播以 S 开头的名称：S + 16 位识别字符 + 1 位校验字符。").apply { margins(top = 10f) })
    }
    controller.diagnosticFound.forEach { f -> box.addView(diagRow(f)) }
    return box
}

private fun MainActivity.diagRow(f: TeslaBle.Found): View {
    val row = hBox()
    row.gravity = Gravity.CENTER_VERTICAL
    row.setPadding(0, dp(8f), 0, dp(8f))
    row.addView(icon(R.drawable.ic_bluetooth, 16, Pal.muted))
    val label = if (f.name.isNotEmpty()) f.name else f.device.address
    row.addView(text(label, 13f, Pal.fg).apply { weight(1f); margins(left = 8f) })
    row.addView(text("${f.rssi} dBm", 12f, Pal.muted))
    return row
}

private fun MainActivity.stepScan(): View {
    val box = vBox()
    if (controller.error.isNotEmpty()) box.addView(text(controller.error, 12f, Pal.danger).apply { margins(bottom = 12f) })

    when {
        controller.scanning -> {
            box.addView(text("正在搜索附近车辆", 22f, Pal.fg, bold = true))
            box.addView(orbit(R.drawable.ic_bluetooth, breathing = true))
            box.addView(hint("请保持 App 在前台，靠近并唤醒车辆，搜索最长约 30 秒"))
            box.addView(wideButton("取消搜索", "secondary") { onCancelScan() })
        }
        controller.found.isNotEmpty() -> {
            box.addView(text("选择你的车辆", 22f, Pal.fg, bold = true))
            // The device whose advertised name matches the VIN-derived prefix is the user's car; show it first.
            val vin = if (TeslaProtocol.validVin(vinDraft)) vinDraft else controller.vin
            val prefix = if (TeslaProtocol.validVin(vin)) TeslaProtocol.advertisementPrefix(vin) else ""
            controller.found.sortedByDescending { f -> f.name.startsWith(prefix) }.forEach { f ->
                val mine = prefix.isNotEmpty() && f.name.startsWith(prefix)
                box.addView(listRow(R.drawable.ic_car_front, f.name,
                    if (mine) "你的车辆 · 信号强度 ${f.rssi} dBm" else "信号强度 ${f.rssi} dBm", "配对") { selectDevice(f.device.address) }
                    .apply { margins(top = 12f) })
            }
            box.addView(wideButton("重新搜索", "secondary") { onRescan() }.apply { margins(top = 20f) })
        }
        else -> {
            box.addView(text("未找到车辆", 22f, Pal.fg, bold = true))
            box.addView(orbit(R.drawable.ic_bluetooth))
            box.addView(text("请确认 VIN 正确、手机蓝牙已开启，并靠近车辆后重试。", 13f, Pal.muted, align = Gravity.CENTER).apply { matchWidth(); margins(bottom = 20f) })
            box.addView(wideButton("重新搜索", "blue") { onRescan() })
            box.addView(wideButton("返回上一步", "secondary") { pairStep = 0; render() }.apply { margins(top = 10f) })
            box.addView(diagnosticPanel())
        }
    }
    return box
}

private fun MainActivity.stepPair(): View {
    val box = vBox()

    if (controller.error.isNotEmpty()) {
        box.addView(text("连接未完成", 22f, Pal.fg, bold = true))
        box.addView(text(controller.error, 13f, Pal.danger).apply { margins(top = 12f, bottom = 20f) })
        box.addView(wideButton("重试", "blue") { pairStep = 0; controller.disconnect(); render() })
        return box
    }

    if (!controller.ble.ready) {
        box.addView(text("正在连接车辆", 22f, Pal.fg, bold = true))
        box.addView(orbit(R.drawable.ic_bluetooth))
        box.addView(hint(controller.status))
        return box
    }

    if (pairingFlow) {
        // Send the add-key request exactly once when the link becomes ready.
        if (!requestedPair) {
            requestedPair = true
            root.post { controller.requestPair() }
        }
        box.addView(text("确认添加这把钥匙", 22f, Pal.fg, bold = true))
        box.addView(text("Model Y · " + "此手机", 13f, Pal.muted).apply { margins(top = 8f) })
        box.addView(orbit(R.drawable.ic_key))
        box.addView(instruction("1", "坐进车辆，将已授权的实体钥匙卡放到车辆指定感应区。"))
        box.addView(instruction("2", "按照车机提示确认添加此手机钥匙。"))
        box.addView(smallNote("车机确认后会自动退出该界面，属正常现象；完成后回到 App 点下方按钮验证。感应位置以车型为准。").apply { margins(top = 16f) })
        box.addView(wideButton("我已在车机确认，验证钥匙", "blue", enabled = !controller.busy) { onCardConfirmed() }.apply { margins(top = 20f) })
    } else {
        box.addView(text("正在验证车辆钥匙", 22f, Pal.fg, bold = true))
        box.addView(orbit(R.drawable.ic_shield_check))
        box.addView(hint(controller.status))
    }
    return box
}

private fun MainActivity.stepSuccess(): View {
    val box = vBox()
    box.gravity = Gravity.CENTER_HORIZONTAL
    val circle = FrameLayout(this)
    circle.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Pal.successBg) }
    circle.layoutParams = LinearLayout.LayoutParams(dp(80f), dp(80f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(40f); bottomMargin = dp(25f) }
    circle.addView(icon(R.drawable.ic_check2, 35, 0xFF4ADE80.toInt()), FrameLayout.LayoutParams(dp(35f), dp(35f), Gravity.CENTER))
    box.addView(circle)
    box.addView(text("蓝牙钥匙已添加", 22f, Pal.fg, bold = true, align = Gravity.CENTER))
    box.addView(text("我的 Model Y · 此手机\n配对完成，可以控制车辆。", 13f, Pal.muted, align = Gravity.CENTER).apply { setLineSpacing(dp(6f).toFloat(), 1f); margins(top = 8f) })
    box.addView(wideButton("进入车辆", "blue") { navigate(Page.CAR) }.apply { margins(top = 35f) })
    return box
}

private fun MainActivity.instruction(num: String, msg: String): View {
    val row = hBox()
    row.gravity = Gravity.TOP
    row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    row.margins(top = 20f)
    val circle = TextView(this)
    circle.text = num
    circle.gravity = Gravity.CENTER
    circle.setTextColor(0xFF89BFFF.toInt())
    circle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f)
    circle.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF29384C.toInt()) }
    row.addView(circle, LinearLayout.LayoutParams(dp(24f), dp(24f)))
    row.addView(text(msg, 14f, 0xFFAAB6C7.toInt()).apply { weight(1f); margins(left = 13f) })
    return row
}

private fun MainActivity.orbit(iconRes: Int, breathing: Boolean = false): View {
    // View-owned animation: cancellation on detach prevents old renders from leaking animators.
    val outer = object : FrameLayout(this) {
        private var pulse: android.animation.ValueAnimator? = null
        private fun updatePulse() {
            pulse?.cancel()
            pulse = null
            alpha = 1f; scaleX = 1f; scaleY = 1f
            if (!breathing || !isAttachedToWindow || windowVisibility != View.VISIBLE) return
            pulse = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val fraction = it.animatedValue as Float
                    alpha = 0.45f + 0.55f * fraction
                    scaleX = 0.92f + 0.08f * fraction
                    scaleY = scaleX
                }
                start()
            }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); updatePulse() }
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            updatePulse()
        }
        override fun onDetachedFromWindow() {
            pulse?.cancel(); pulse = null
            super.onDetachedFromWindow()
        }
    }
    outer.layoutParams = LinearLayout.LayoutParams(dp(150f), dp(150f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(30f); bottomMargin = dp(20f) }
    outer.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFF1B2A3E.toInt()) }
    val inner = FrameLayout(this)
    inner.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.TRANSPARENT); setStroke(dp(1f), 0xFF2F507B.toInt()) }
    inner.addView(icon(iconRes, 40, Pal.blue), FrameLayout.LayoutParams(dp(40f), dp(40f), Gravity.CENTER))
    outer.addView(inner, FrameLayout.LayoutParams(dp(110f), dp(110f), Gravity.CENTER))
    return outer
}

// Scrollable help keeps all six steps reachable on small screens and with large text.
private fun MainActivity.showAddKeyHelp() {
    val body = vBox().apply { pad(24f, 8f, 24f, 12f) }
    val steps = listOf(
        "准备车辆和实体卡" to "坐进车辆，准备好已授权的实体钥匙卡，并开启手机蓝牙。",
        "生成本机钥匙" to "App 会自动在本机生成密钥对。私钥保存在手机中，公钥用于向车辆登记，无需手动操作。",
        "搜索并连接车辆" to "在 App 中输入正确的 VIN，点击搜索，选择你的车辆并连接。请保持 App 在前台。",
        "发送添加钥匙请求" to "连接成功后，App 会自动向车辆发送添加钥匙请求，提交本机公钥及钥匙类型、角色。",
        "使用已有实体卡授权" to "按车机提示，将已授权的实体钥匙卡放到中控钥匙感应区。具体感应位置以车型说明为准。",
        "在车机确认添加" to "在车机屏幕点击“确认”，批准添加新钥匙。随后回到 App，点击“我已在车机确认，验证钥匙”。"
    )
    steps.forEachIndexed { i, (title, description) ->
        body.addView(text("${i + 1}. $title", 16f, Pal.fg, bold = true).apply { margins(top = 16f, bottom = 6f) })
        body.addView(text(description, 14f, Pal.muted).apply { setLineSpacing(dp(4f).toFloat(), 1f) })
    }
    body.addView(text("请求发送成功不代表钥匙添加成功。完成实体卡授权和车机确认后，还需通过 App 的钥匙验证，才能确认可用。", 13f, Pal.badgeBlueFg)
        .apply { margins(top = 20f, bottom = 12f); setLineSpacing(dp(4f).toFloat(), 1f) })
    val scroll = android.widget.ScrollView(this).apply { addView(body) }
    val dialog = android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_DEVICE_DEFAULT_DARK)
        .setTitle("如何添加钥匙？")
        .setView(scroll)
        .setPositiveButton("知道了", null)
        .create()
    dialog.setOnShowListener {
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(),
            (resources.displayMetrics.heightPixels * 0.82f).toInt())
    }
    dialog.show()
}

private fun MainActivity.climateControls(): View {
    val box = vBox().apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        margins(left = 25f, top = 16f, right = 25f, bottom = 16f)
    }
    val state = if (!isConnected) "未连接" else when (controller.climateOn) {
        true -> "已开启"
        false -> "已关闭"
        null -> "状态未知"
    }
    val heading = hBox().apply { gravity = Gravity.CENTER_VERTICAL }
    heading.addView(text("空调 · $state", 15f, Pal.fg, bold = true).apply { weight(1f) })
    heading.addView(text("刷新", 13f, Pal.blue).apply {
        pad(12f, 12f, 0f, 12f)
        alpha = if (controlsEnabled) 1f else 0.35f
        if (controlsEnabled) setOnClickListener { controller.command(VehicleCommand.CLIMATE_STATUS) }
    })
    box.addView(heading)
    val row = hBox()
    row.addView(wideButton("开启空调", "blue", enabled = controlsEnabled) {
        controller.command(VehicleCommand.CLIMATE_ON)
    }.apply { weight(1f) })
    row.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(dp(12f), 1) })
    row.addView(wideButton("关闭空调", "secondary", enabled = controlsEnabled) {
        controller.command(VehicleCommand.CLIMATE_OFF)
    }.apply { weight(1f) })
    box.addView(row)
    if (controller.climateError.isNotEmpty()) {
        box.addView(text(controller.climateError, 12f, Pal.danger).apply { margins(top = 10f) })
    }
    return box
}

/** Image-relative targets remain on the correct closure after any viewport resize. */
private fun MainActivity.officialHero(): View {
    val hero = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.66f).toInt())
    }
    val artwork = getDrawable(R.drawable.car_official)!!
    hero.addView(ImageView(this).apply {
        setImageDrawable(artwork)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = "车辆俯视图，车头朝上"
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    val anchors = mutableListOf<Pair<View, Float>>()
    fun control(title: String, state: String, y: Float, iconRes: Int? = null, action: () -> Unit) {
        val target = vBox(matchWidth = false).apply {
            gravity = Gravity.CENTER
            pad(10f, 6f, 10f, 6f)
            background = clickableBg(9f, 0x80000000.toInt())
            contentDescription = "$title，$state"
            isEnabled = controlsEnabled
            alpha = if (controlsEnabled) 1f else 0.5f
            if (controlsEnabled) setOnClickListener { action() }
        }
        if(iconRes != null) target.addView(icon(iconRes, 27, Pal.fg))
        target.addView(text(title, if(iconRes == null)18f else 13f, Pal.fg, bold = true, align = Gravity.CENTER))
        target.addView(text(state, 10f, Pal.subFg, align = Gravity.CENTER))
        hero.addView(target,FrameLayout.LayoutParams(dp(112f),ViewGroup.LayoutParams.WRAP_CONTENT))
        anchors.add(target to y)
    }
    val obs = if(isConnected)controller.observation else null
    control("打开前备箱", com.ronnie.teslakey.protocol.VehicleObservation.closureLabel(obs?.frunk), 0.17f) { onFrunk() }
    val lockText=when(obs?.lock){0->"锁车";1,2,3->"解锁";else->"车锁"}
    control(lockText, if(isConnected)com.ronnie.teslakey.protocol.VehicleObservation.lockLabel(obs?.lock) else "未连接",
        0.54f, if(obs?.lock==0)R.drawable.ic_unlock else R.drawable.ic_lock) {
        if(obs?.lock==0)onLock() else if(obs?.lock in listOf(1,2,3))onUnlock()
        else toast("车锁状态尚未确认，请稍后再操作")
    }
    control(if(trunkOpen())"关闭后备箱" else "打开后备箱",
        com.ronnie.teslakey.protocol.VehicleObservation.closureLabel(obs?.trunk), 0.79f) { onTrunk() }
    hero.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ ->
        val scale = minOf(hero.width.toFloat()/artwork.intrinsicWidth,hero.height.toFloat()/artwork.intrinsicHeight)
        val imageHeight=artwork.intrinsicHeight*scale
        val top=(hero.height-imageHeight)/2
        anchors.forEach { (target,y) ->
            target.x=(hero.width-target.width)/2f
            target.y=top+imageHeight*y-target.height/2f
        }
    }
    return hero
}
