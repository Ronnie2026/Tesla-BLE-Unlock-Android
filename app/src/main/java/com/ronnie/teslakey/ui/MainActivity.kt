package com.ronnie.teslakey.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.ronnie.teslakey.R
import com.ronnie.teslakey.data.VehicleController
import com.ronnie.teslakey.protocol.TeslaProtocol
import com.ronnie.teslakey.protocol.VehicleCommand

/**
 * Single-activity UI. Mirrors the HTML prototype: a scrollable screen, a two-tab bottom
 * navigation, a bottom-sheet overlay and a toast. All state comes from [VehicleController];
 * every controller change triggers a full re-render, exactly like the prototype's render().
 */
class MainActivity : android.app.Activity() {

    enum class Page { CAR, ME, SKIN, SETTINGS, KEYS, ADD }

    internal lateinit var controller: VehicleController
    private val handler = Handler(Looper.getMainLooper())

    internal lateinit var root: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var overlay: FrameLayout
    private lateinit var toastView: TextView
    private lateinit var navCarIcon: android.widget.ImageView
    private lateinit var navCarLabel: TextView
    private lateinit var navCarDot: View
    private lateinit var navMeIcon: android.widget.ImageView
    private lateinit var navMeLabel: TextView
    private lateinit var navMeDot: View

    internal var page = Page.CAR
    // Appearance / preference state (persisted locally, UI-only like the prototype).
    internal var skin: String = "cockpit"
    internal var confirmFrunk: Boolean = true

    // Pairing wizard state.
    internal var pairStep = 0            // 0 VIN, 1 scan/found, 2 connect+card, 3 success
    internal var pairingFlow = false     // true when adding a brand-new key (vs reconnect)
    internal var requestedPair = false   // guards a single pairRequest() send
    internal var vinDraft = ""
    internal var scanError = ""

    private var pendingAfterPermission: (() -> Unit)? = null
    private var toastHide: Runnable? = null

    // ---- derived state -------------------------------------------------------
    internal val hasKey: Boolean get() = controller.paired || controller.demo
    internal val isConnected: Boolean
        get() = if (controller.demo) controller.demoConnected else controller.connected
    internal val controlsEnabled: Boolean get() = controller.canControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("ui", MODE_PRIVATE)
        skin = prefs.getString("skin", "cockpit") ?: "cockpit"
        confirmFrunk = prefs.getBoolean("confirmFrunk", true)

        controller = VehicleController(this)
        controller.onChange = { render() }
        controller.onNotice = { toast(it) }
        vinDraft = controller.vin

        root = FrameLayout(this).apply { setBackgroundColor(Pal.bg) }
        buildScreen()
        buildNav()
        buildOverlay()
        buildToast()
        setContentView(root)
        render()
    }

    private fun buildScreen() {
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, 0, 0, dp(76f))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        content = vBox()
        scroll.addView(content)
        root.addView(scroll)
    }

    private fun buildNav() {
        val nav = vBox()
        nav.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76f), Gravity.BOTTOM)
        nav.setBackgroundColor(Pal.navBg)
        nav.addView(hairline(Pal.line, 1f))

        val tabs = hBox()
        tabs.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0).apply { weight = 1f }
        val car = navTab(R.drawable.ic_car_front, "车辆")
        val me = navTab(R.drawable.ic_person, "我的")
        navCarIcon = car.icon; navCarLabel = car.label; navCarDot = car.dot
        navMeIcon = me.icon; navMeLabel = me.label; navMeDot = me.dot
        tabs.addView(carTabRoot(car)); tabs.addView(meTabRoot(me))
        nav.addView(tabs)
        root.addView(nav)
    }

    private data class Tab(val icon: android.widget.ImageView, val label: TextView, val dot: View)

    private fun navTab(iconRes: Int, label: String): Tab {
        val icon = icon(iconRes, 22, Pal.navFg)
        icon.layoutParams = LinearLayout.LayoutParams(dp(22f), dp(22f)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(4f) }
        val text = text(label, 11f, Pal.navFg, align = Gravity.CENTER)
        val dot = View(this).apply {
            background = roundRect(2f, Pal.blue)
            layoutParams = LinearLayout.LayoutParams(dp(24f), dp(3f)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(6f) }
        }
        return Tab(icon, text, dot)
    }

    private fun carTabRoot(tab: Tab): View = tabRoot(tab) { navigate(Page.CAR) }
    private fun meTabRoot(tab: Tab): View = tabRoot(tab) { navigate(Page.ME) }

    private fun tabRoot(tab: Tab, onClick: () -> Unit): View {
        val box = vBox()
        box.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply { weight = 1f }
        box.gravity = Gravity.CENTER_HORIZONTAL
        box.setPadding(0, dp(10f), 0, dp(8f))
        box.addView(tab.icon)
        box.addView(tab.label)
        box.addView(tab.dot)
        box.setOnClickListener { onClick() }
        return box
    }

    private fun buildOverlay() {
        overlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            visibility = View.GONE
        }
        root.addView(overlay)
    }

    private fun buildToast() {
        toastView = text("", 13f, Pal.toastFg, align = Gravity.CENTER)
        toastView.background = roundRect(12f, Pal.toastBg)
        toastView.pad(16f, 12f, 16f, 12f)
        toastView.alpha = 0f
        toastView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ).apply { leftMargin = dp(24f); rightMargin = dp(24f); bottomMargin = dp(95f) }
        root.addView(toastView)
    }

    // ---- render --------------------------------------------------------------
    internal fun render() {
        content.removeAllViews()
        content.addView(
            when (page) {
                Page.CAR -> carScreen()
                Page.ME -> meScreen()
                Page.SKIN -> skinScreen()
                Page.SETTINGS -> settingsScreen()
                Page.KEYS -> keysScreen()
                Page.ADD -> addScreen()
            }
        )
        val carActive = page == Page.CAR
        tintTab(navCarIcon, navCarLabel, navCarDot, carActive)
        tintTab(navMeIcon, navMeLabel, navMeDot, !carActive)
    }

    private fun tintTab(icon: android.widget.ImageView, label: TextView, dot: View, active: Boolean) {
        val c = if (active) Pal.blue else Pal.navFg
        icon.drawable?.mutate()?.setTint(c)
        icon.setImageDrawable(icon.drawable)
        label.setTextColor(c)
        dot.visibility = if (active) View.VISIBLE else View.INVISIBLE
    }

    internal fun navigate(target: Page) {
        if (overlay.visibility == View.VISIBLE) closeSheet()
        if (target == Page.ADD && page != Page.ADD) resetWizard()
        page = target
        render()
        scroll.post { scroll.scrollTo(0, 0) }
    }

    private fun resetWizard() {
        pairStep = 0
        pairingFlow = false
        requestedPair = false
        scanError = ""
        vinDraft = controller.vin
    }

    // ---- toast & sheets ------------------------------------------------------
    internal fun toast(msg: String) {
        toastView.text = msg
        toastHide?.let { handler.removeCallbacks(it) }
        toastView.animate().cancel()
        toastView.alpha = 1f
        val hide = Runnable { toastView.animate().alpha(0f).setDuration(200).start() }
        toastHide = hide
        handler.postDelayed(hide, 2600)
    }

    internal fun closeSheet() {
        overlay.animate().cancel()
        overlay.removeAllViews()
        overlay.visibility = View.GONE
    }

    internal fun showSheet(
        title: String,
        body: String,
        confirmLabel: String,
        onConfirm: () -> Unit,
        cancelLabel: String = "取消"
    ) {
        overlay.removeAllViews()
        val cover = View(this).apply {
            setBackgroundColor(Pal.dim)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener { closeSheet() }
        }
        overlay.addView(cover)

        val card = vBox()
        card.background = topRound(24f, Pal.sheetBg)
        card.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        card.pad(24f, 24f, 24f, 24f)

        val close = icon(R.drawable.ic_x_lg, 18, Pal.muted)
        val closeWrap = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36f))
            addView(close, FrameLayout.LayoutParams(dp(36f), dp(36f), Gravity.END))
            setOnClickListener { closeSheet() }
        }
        card.addView(closeWrap)
        card.addView(text(title, 22f, Pal.fg, bold = true).apply { margins(top = 5f, bottom = 10f) })
        card.addView(text(body, 14f, 0xFFA8B4C4.toInt()).apply { setLineSpacing(dp(6f).toFloat(), 1f); margins(bottom = 8f) })
        card.addView(wideButton(confirmLabel, "light") { onConfirm() }.apply { margins(top = 13f) })
        card.addView(wideButton(cancelLabel, "secondary") { closeSheet() }.apply { margins(top = 10f) })
        overlay.addView(card)

        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(160).start()
    }

    // ---- permissions ---------------------------------------------------------
    private fun ensureBle(action: () -> Unit) {
        if (controller.ble.permitted()) {
            action()
        } else {
            pendingAfterPermission = action
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                REQ_BLE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode != REQ_BLE) return
        controller.log.event("permission.result", "granted=${controller.ble.permitted()}")
        val granted = results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }
        val action = pendingAfterPermission
        pendingAfterPermission = null
        if (granted) action?.invoke()
        else toast("需要「附近设备」权限才能搜索并连接车辆")
    }

    private fun bleEnabled(): Boolean {
        val manager = getSystemService(BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
        if (manager?.adapter?.isEnabled == true) return true
        toast("请先开启手机蓝牙")
        return false
    }

    // ---- wizard actions ------------------------------------------------------
    internal fun onSearchVehicle() {
        val v = vinDraft.trim().uppercase()
        if (!TeslaProtocol.validVin(v)) {
            scanError = "请输入有效的 17 位 VIN（不含 I、O、Q）。"
            render()
            return
        }
        scanError = ""
        ensureBle {
            if (!bleEnabled()) return@ensureBle
            vinDraft = v
            pairStep = 1
            requestedPair = false
            controller.scan(v)
            render()
        }
    }

    internal fun onUseDemo() {
        controller.setDemo(true)
        page = Page.CAR
        render()
    }

    internal fun onCancelScan() {
        controller.disconnect()
        pairStep = 0
        scanError = ""
        render()
    }

    internal fun selectDevice(address: String) {
        val f = controller.found.firstOrNull { it.device.address == address } ?: return
        pairStep = 2
        requestedPair = false
        pairingFlow = !controller.paired
        controller.connect(f, pairingFlow)
        render()
    }

    internal fun onRescan() {
        pairStep = 1
        requestedPair = false
        controller.found.clear()
        if (TeslaProtocol.validVin(vinDraft)) controller.scan(vinDraft.trim().uppercase()) else pairStep = 0
        render()
    }

    /** Card step confirmed by the user: run the hello/verify handshake. */
    internal fun onCardConfirmed() {
        requestedPair = true
        controller.verify()
        render()
    }

    internal fun onDiagnosticScan() {
        ensureBle {
            if (!bleEnabled()) return@ensureBle
            controller.startDiagnostic()
            render()
        }
    }

    internal fun onStopDiagnostic() {
        controller.stopDiagnostic()
        render()
    }

    // ---- vehicle command actions --------------------------------------------
    internal fun onUnlock() = controller.command(VehicleCommand.UNLOCK)
    internal fun onLock() = controller.command(VehicleCommand.LOCK)

    internal fun onFrunk() {
        if (confirmFrunk) {
            showSheet(
                "打开前备箱？",
                "请确认前备箱上方没有障碍物。前备箱打开后需要手动关闭。",
                "确认打开",
                { closeSheet(); controller.command(VehicleCommand.FRUNK) }
            )
        } else controller.command(VehicleCommand.FRUNK)
    }

    internal fun onTrunk() {
        val state = controller.observation?.trunk
        if (state != 0 && state != 1 && state != 2) {
            toast("后备箱状态尚未确认或正在开关，请稍后再操作")
            return
        }
        controller.command(if (state == 1 || state == 2) VehicleCommand.TRUNK_CLOSE else VehicleCommand.TRUNK_OPEN)
    }

    internal fun onConnectionTap() {
        if (!hasKey) { navigate(Page.ADD); return }
        if (controller.demo) {
            if (controller.demoConnected)
                showSheet("断开蓝牙连接？", "断开后可在演示模式重新连接体验。", "断开连接",
                    { controller.setDemoConnection(false); closeSheet() })
            else
                showSheet("连接演示车辆", "演示模式不会连接或控制真实车辆。", "模拟连接",
                    { controller.setDemoConnection(true); closeSheet() })
            return
        }
        if (controller.connected) {
            showSheet("断开蓝牙连接？", "断开后，靠近车辆重新连接即可继续使用控制功能。", "断开连接",
                { controller.disconnect(); closeSheet() })
        } else {
            showSheet("连接我的 Model Y", "请靠近车辆并开启手机蓝牙，然后重新搜索连接。", "去连接",
                { closeSheet(); startReconnect() })
        }
    }

    internal fun startReconnect() {
        vinDraft = controller.vin
        scanError = ""
        pairStep = 0
        page = Page.ADD
        render()
    }

    internal fun onRemoveKey() {
        showSheet(
            "移除此手机的钥匙？",
            "此操作会清除本机配对记录。车辆端的钥匙仍需在车机「车锁」中单独删除。",
            "移除记录",
            {
                if (controller.demo) controller.setDemo(false) else controller.forget()
                closeSheet()
                page = Page.ME
                render()
            }
        )
    }

    internal fun chooseSkin(value: String) {
        skin = value
        getSharedPreferences("ui", MODE_PRIVATE).edit().putString("skin", value).apply()
        render()
    }

    internal fun setConfirm(value: Boolean) {
        confirmFrunk = value
        getSharedPreferences("ui", MODE_PRIVATE).edit().putBoolean("confirmFrunk", value).apply()
        render()
    }

    // ---- lifecycle & back ----------------------------------------------------
    override fun onResume() {
        super.onResume()
        controller.foreground()
        render()
    }

    override fun onPause() {
        super.onPause()
        controller.background()
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.close()
    }

    @Deprecated("Framework back handling")
    override fun onBackPressed() {
        when {
            overlay.visibility == View.VISIBLE -> closeSheet()
            page != Page.CAR && page != Page.ME -> navigate(Page.ME)
            page == Page.ME -> navigate(Page.CAR)
            else -> @Suppress("DEPRECATION") super.onBackPressed()
        }
    }

    companion object {
        private const val REQ_BLE = 1001
    }
}
