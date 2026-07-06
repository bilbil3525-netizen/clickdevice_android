package com.example.clickdevice.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.example.clickdevice.MyService
import com.example.clickdevice.PowerKeyObserver
import com.example.clickdevice.R
import com.example.clickdevice.SmallWindowView
import com.example.clickdevice.Util
import com.example.clickdevice.helper.DevicePermissionHelper
import com.example.clickdevice.helper.DeviceWindowMetricsProvider
import com.example.clickdevice.helper.PermissionStatus
import com.example.clickdevice.helper.setOnTouchClick
import com.example.clickdevice.helper.smallWindowManager
import com.example.clickdevice.ui.theme.ClickDeviceTheme
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivityCompose : ComponentActivity() {

    private var isRun = false
    private var stopTime = 0L
    private val singleThreadExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var powerKeyObserver: PowerKeyObserver? = null
    private var windowView: SmallWindowView? = null
    private var btnWindowView: SmallWindowView? = null
    private var tvWinB: TextView? = null
    private var wm: WindowManager? = null
    private var mLayoutParams: WindowManager.LayoutParams? = null
    private var btnLayoutParams: WindowManager.LayoutParams? = null
    private var isShow = false

    // 主线程 Handler，用于更新 UI
    private val mainHandler = Handler(Looper.getMainLooper())

    // Compose 状态：让悬浮窗按钮文字可以实时更新
    var isRunning by mutableStateOf(false)
        private set

    var clickCount by mutableStateOf("0")
        private set

    var clickInterval by mutableStateOf("1000")
        private set

    var showAccessibilityDialog by mutableStateOf(false)
    var showComplianceDialog by mutableStateOf(false)
    private var permissionStatuses by mutableStateOf<List<PermissionStatus>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionStatuses()
        setContent {
            ClickDeviceTheme {
                val barColor = MaterialTheme.colorScheme.surface
                val darkIcons = !androidx.compose.foundation.isSystemInDarkTheme()
                SideEffect {
                    window.statusBarColor = barColor.toArgb()
                    window.navigationBarColor = barColor.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = darkIcons
                        isAppearanceLightNavigationBars = darkIcons
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                isFloatingWindowShow = isShow,
                clickCount = clickCount,
                clickInterval = clickInterval,
                showAccessibilityDialog = showAccessibilityDialog,
                showComplianceDialog = showComplianceDialog,
                onDismissAccessibilityDialog = { showAccessibilityDialog = false },
                onDismissComplianceDialog = { showComplianceDialog = false },
                onOpenAccessibility = {
                    showAccessibilityDialog = false
                    openAccessibility()
                },
                onOpenCompliance = { showComplianceDialog = true },
                permissionStatuses = permissionStatuses,
                onOpenOverlaySettings = { openOverlaySettings() },
                onOpenBatterySettings = { openBatterySettings() },
                onOpenAppSettings = { openAppSettings() },
                onStartClickDevice = { startClickDevice() },
                onOpenScriptList = { startScriptList() },
                onOpenRecordScript = { startRecordScript() },
                onOpenScriptGroup = { startScriptGroup() },
                onOpenKeyBinding = { startKeyBinding() },
                onCountChange = { clickCount = it },
                onIntervalChange = { clickInterval = it }
            )
                }
            }
        }

        powerKeyObserver = PowerKeyObserver(this).apply {
            startListen()
            setHomeKeyListener {
                isRun = false
                isRunning = false
                resetBtnText()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatuses()
    }

    private fun refreshPermissionStatuses() {
        permissionStatuses = DevicePermissionHelper.collectStatuses(this)
    }

    private fun openAccessibility() {
        try {
            startActivity(DevicePermissionHelper.accessibilitySettingsIntent())
        } catch (e: Exception) {
            startActivity(Intent("android.settings.SETTINGS"))
        }
    }

    private fun openOverlaySettings() {
        try {
            startActivity(DevicePermissionHelper.overlaySettingsIntent(this))
        } catch (e: Exception) {
            startActivity(Intent("android.settings.SETTINGS"))
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(DevicePermissionHelper.batteryOptimizationIntent(this))
        } catch (e: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        try {
            startActivity(DevicePermissionHelper.appSettingsIntent(this))
        } catch (e: Exception) {
            startActivity(Intent("android.settings.SETTINGS"))
        }
    }

    private fun startClickDevice() {
        if (!isShow) {
            showFloatWindows()
        } else {
            if (isRun) {
                onBtnWinBClick()
            }
            hideFloatWindows()
        }
    }

    // region 悬浮窗初始化

    @SuppressLint("WrongConstant")
    private fun initSmallViewLayout() {
        wm = smallWindowManager()

        // 初始化位置选择悬浮窗 (window_a) - 初始位置居中
        windowView = LayoutInflater.from(this).inflate(R.layout.window_a, null) as SmallWindowView
        mLayoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, 8, PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mLayoutParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        mLayoutParams?.gravity = Gravity.NO_GRAVITY
        windowView?.setWm(wm)
        windowView?.setWmParams(mLayoutParams)
        // setWmParams 会重置 x=0,y=0，之后再设置初始位置居中
        // 注意：不能在 addView 之前调用 updateViewLayout，否则会报 not attached to window manager
        mLayoutParams?.x = 0
        mLayoutParams?.y = 0

        // 初始化启动/停止按钮悬浮窗 (window_b) - SmallWindowView 自带拖动，初始位置屏幕顶部居中
        btnWindowView =
            LayoutInflater.from(this).inflate(R.layout.window_b, null) as SmallWindowView
        btnLayoutParams = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT, 8, PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnLayoutParams?.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }
        btnWindowView?.enableMove=false
        btnWindowView?.setWm(wm)
        btnWindowView?.setWmParams(btnLayoutParams)
        btnLayoutParams?.x = 0
        btnLayoutParams?.y = -(btnWindowView?.screenHeight ?: 0) / 2

        // 设置启动/停止按钮点击事件
        tvWinB = btnWindowView?.findViewById(R.id.tv_win_b)
        tvWinB?.setOnTouchClick({ onBtnWinBClick() }, {
            if (isRun) {
                onBtnWinBClick()
                return@setOnTouchClick false
            }
            return@setOnTouchClick true
        })
    }

    @SuppressLint("WrongConstant")
    private fun alertWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
            } else {
                showWindow()
            }
        } else {
            showWindow()
        }
    }

    private fun showWindow() {

        if (windowView?.windowId == null) {
            wm?.addView(windowView, mLayoutParams)
        }
        if (btnWindowView?.windowId == null) {
            wm?.addView(btnWindowView, btnLayoutParams)
        }
        resetBtnText()
        windowView?.setwmParamsFlags(8)
    }

    private fun dismissWindow() {
        if (windowView?.windowId != null) {
            try {
                wm?.removeView(windowView)
            } catch (_: Exception) {
            }
        }
        if (btnWindowView?.windowId != null) {
            try {
                wm?.removeView(btnWindowView)
            } catch (_: Exception) {
            }
        }
    }

    private fun showFloatWindows() {
        if (!MyService.isStart()) {
            refreshPermissionStatuses()
            showAccessibilityDialog = true
            Toast.makeText(this, "请先开启辅助功能", Toast.LENGTH_LONG).show()
            return
        }
        if (windowView == null) {
            initSmallViewLayout()
        }
        alertWindow()
        isShow = true
    }

    private fun hideFloatWindows() {
        isShow = false
        isRun = false
        isRunning = false
        dismissWindow()
    }

    // endregion

    // region 悬浮窗按钮点击逻辑

    @SuppressLint("WrongConstant")
    private fun onBtnWinBClick() {
        if (!isRun) {
            if (stopTime + 2000 > System.currentTimeMillis()) {
                Toast.makeText(this, "点太快了,休息一下吧", Toast.LENGTH_SHORT).show()
                return
            }
            isRun = true
            isRunning = true

            val clickPoint = resolveQuickClickPoint()
            if (clickPoint == null) {
                isRun = false
                isRunning = false
                Toast.makeText(this, "请先拖动选择点击位置", Toast.LENGTH_SHORT).show()
                return
            }
            val (x, y) = clickPoint
            if (!MyService.isStart()) {
                isRun = false
                isRunning = false
                Toast.makeText(
                    this,
                    "请手动开启辅助功能，若已开启请重启应用再试一次",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            tvWinB?.text = "停止"

            // 使位置选择悬浮窗不可触摸，防止误操作
            windowView?.setwmParamsFlags(24)

            val count = clickCount.toIntOrNull() ?: 0
            val interval = (clickInterval.toIntOrNull() ?: 1000).coerceAtLeast(1)

            singleThreadExecutor.execute {
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                }
                if (count > 0) {
                    for (i in 0 until count) {
                        if (!isRun) break
                        mainHandler.post {
                            MyService.myService?.dispatchGestureClick(x.toFloat(), y.toFloat())
                        }
                        var elapsed = 0
                        while (elapsed < interval && isRun) {
                            try {
                                Thread.sleep(1)
                            } catch (_: InterruptedException) {
                                break
                            }
                            elapsed += 1
                        }
                    }
                } else {
                    while (isRun) {
                        mainHandler.post {
                            MyService.myService?.dispatchGestureClick(x.toFloat(), y.toFloat())
                        }
                        var elapsed = 0
                        while (elapsed < interval && isRun) {
                            try {
                                Thread.sleep(1)
                            } catch (_: InterruptedException) {
                                break
                            }
                            elapsed += 1
                        }
                    }
                }
                mainHandler.post {
                    isRunning = false
                    resetBtnText()
                    // 恢复位置选择悬浮窗触摸
                    if (isShow) {
                        windowView?.setwmParamsFlags(8)
                    }
                }
            }
        } else {
            stopTime = System.currentTimeMillis()
            isRun = false
            isRunning = false
            resetBtnText()
        }
    }

    private fun resolveQuickClickPoint(): Pair<Int, Int>? {
        val selector = windowView ?: return null
        val metrics = DeviceWindowMetricsProvider.current(this)
        val rawX = selector.actionUpX
        val rawY = selector.actionUpY
        val location = IntArray(2)
        selector.getLocationOnScreen(location)

        val x = if (rawX > 0) rawX else location[0] + selector.width / 2
        val y = if (rawY > 0) rawY else location[1] + selector.height / 2

        val minX = metrics.insetLeft
        val minY = metrics.insetTop
        val maxX = metrics.width - metrics.insetRight
        val maxY = metrics.height - metrics.insetBottom
        if (x < minX || x > maxX || y < minY || y > maxY) {
            return null
        }
        return x to y
    }

    private fun resetBtnText() {
        tvWinB?.text = "开始"
    }

    // endregion

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限被拒绝", Toast.LENGTH_SHORT).show()
            } else {
                showWindow()
            }
        }
    }

    private fun startScriptList() {
        hideFloatWindows()
        startActivity(Intent(this, ScriptListActivityCompose::class.java))
    }

    private fun startRecordScript() {
        hideFloatWindows()
        startActivity(Intent(this, RecordScriptListActivityCompose::class.java))
    }

    private fun startScriptGroup() {
        hideFloatWindows()
        startActivity(Intent(this, ScriptGroupListActivityCompose::class.java))
    }

    private fun startKeyBinding() {
        hideFloatWindows()
        startActivity(Intent(this, KeyBindingListActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        isRun = false
        hideFloatWindows()
        powerKeyObserver?.stopListen()
        singleThreadExecutor.shutdownNow()
    }

    companion object {
        private const val OVERLAY_PERMISSION_REQ_CODE = 2
    }
}

private enum class MainTab(val title: String) {
    Home("首页"),
    Script("脚本"),
    Mine("我的")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isFloatingWindowShow: Boolean,
    clickCount: String,
    clickInterval: String,
    permissionStatuses: List<PermissionStatus> = emptyList(),
    showAccessibilityDialog: Boolean = false,
    showComplianceDialog: Boolean = false,
    onDismissAccessibilityDialog: () -> Unit = {},
    onDismissComplianceDialog: () -> Unit = {},
    onOpenAccessibility: () -> Unit,
    onOpenCompliance: () -> Unit = {},
    onOpenOverlaySettings: () -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onStartClickDevice: () -> Unit,
    onOpenScriptList: () -> Unit,
    onOpenRecordScript: () -> Unit,
    onOpenScriptGroup: () -> Unit,
    onOpenKeyBinding: () -> Unit,
    onCountChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var showTutorialDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(selectedTab.title) })
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == MainTab.Home,
                    onClick = { selectedTab = MainTab.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Script,
                    onClick = { selectedTab = MainTab.Script },
                    icon = { Icon(Icons.Default.Description, contentDescription = null) },
                    label = { Text("脚本") }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.Mine,
                    onClick = { selectedTab = MainTab.Mine },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("我的") }
                )
            }
        }
    ) { padding ->
        val context = LocalContext.current
        val accessibilityReady = permissionStatuses.firstOrNull { it.title == "无障碍服务" }?.granted == true
        val overlayReady = permissionStatuses.firstOrNull { it.title == "悬浮窗" }?.granted == true

        when (selectedTab) {
            MainTab.Home -> HomeTabContent(
                modifier = Modifier.padding(padding),
                isFloatingWindowShow = isFloatingWindowShow,
                accessibilityReady = accessibilityReady,
                overlayReady = overlayReady,
                clickCount = clickCount,
                clickInterval = clickInterval,
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onStartClickDevice = onStartClickDevice,
                onCountChange = onCountChange,
                onIntervalChange = onIntervalChange,
                onOpenTutorial = { showTutorialDialog = true }
            )
            MainTab.Script -> ScriptTabContent(
                modifier = Modifier.padding(padding),
                onOpenScriptList = onOpenScriptList,
                onOpenRecordScript = onOpenRecordScript,
                onOpenScriptGroup = onOpenScriptGroup,
                onOpenKeyBinding = onOpenKeyBinding
            )
            MainTab.Mine -> MineTabContent(
                modifier = Modifier.padding(padding),
                packageName = context.packageName,
                permissionStatuses = permissionStatuses,
                onOpenAccessibility = onOpenAccessibility,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenAppSettings = onOpenAppSettings,
                onOpenCompliance = onOpenCompliance
            )
        }
    }

    if (showAccessibilityDialog) {
        AlertDialog(
            onDismissRequest = onDismissAccessibilityDialog,
            title = { Text("辅助功能") },
            text = { Text("使用连点器需要开启(无障碍)辅助功能，是否现在去开启？") },
            confirmButton = {
                TextButton(onClick = onOpenAccessibility) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissAccessibilityDialog) {
                    Text("取消")
                }
            }
        )
    }

    if (showTutorialDialog) {
        QuickStartTutorialDialog(onDismiss = { showTutorialDialog = false })
    }

    if (showComplianceDialog) {
        ComplianceDialog(onDismiss = onDismissComplianceDialog)
    }
}

@Composable
private fun HomeTabContent(
    modifier: Modifier = Modifier,
    isFloatingWindowShow: Boolean,
    accessibilityReady: Boolean,
    overlayReady: Boolean,
    clickCount: String,
    clickInterval: String,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onStartClickDevice: () -> Unit,
    onCountChange: (String) -> Unit,
    onIntervalChange: (String) -> Unit,
    onOpenTutorial: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("运行状态", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HomePermissionStatusRow(
                        title = "无障碍服务",
                        ready = accessibilityReady,
                        readyText = "已开启",
                        pendingText = "未开启",
                        readyAction = "查看",
                        pendingAction = "去开启",
                        onClick = onOpenAccessibility
                    )
                    HomePermissionStatusRow(
                        title = "悬浮窗",
                        ready = overlayReady,
                        readyText = "已授权",
                        pendingText = "待授权",
                        readyAction = "查看",
                        pendingAction = "去授权",
                        onClick = onOpenOverlaySettings
                    )
                }
                Text(
                    if (isFloatingWindowShow) "悬浮控制已显示，可在屏幕上选择点击位置。" else "打开连点器后，将显示点位选择器和开始按钮。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("快速连点", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "拖动点位选择器确定位置，再用悬浮按钮开始或停止。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = clickCount,
                    onValueChange = { onCountChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("点击次数") },
                    placeholder = { Text("0为无限次") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clickInterval,
                    onValueChange = { onIntervalChange(it.filter { c -> c.isDigit() }) },
                    label = { Text("时间间隔(ms)") },
                    placeholder = { Text("最小1ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = onStartClickDevice,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isFloatingWindowShow) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ) else ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isFloatingWindowShow) "关闭悬浮控制" else "打开连点器")
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenTutorial() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("使用教程", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "查看快速连点的权限、点位和开始/停止步骤。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun HomePermissionStatusRow(
    title: String,
    ready: Boolean,
    readyText: String,
    pendingText: String,
    readyAction: String,
    pendingAction: String,
    onClick: () -> Unit
) {
    val stateColor = if (ready) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }
    val stateIcon = if (ready) Icons.Default.CheckCircle else Icons.Default.ErrorOutline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = stateIcon,
            contentDescription = null,
            tint = stateColor
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (ready) readyText else pendingText,
                style = MaterialTheme.typography.bodySmall,
                color = stateColor
            )
        }
        Text(
            if (ready) readyAction else pendingAction,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ScriptTabContent(
    modifier: Modifier = Modifier,
    onOpenScriptList: () -> Unit,
    onOpenRecordScript: () -> Unit,
    onOpenScriptGroup: () -> Unit,
    onOpenKeyBinding: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("录制脚本", style = MaterialTheme.typography.titleMedium)
                Text(
                    "录制点击、长按和滑动动作，保存后可重复播放或放到悬浮按键中使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onOpenRecordScript, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.RadioButtonChecked, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("进入录制脚本")
                }
            }
        }

        Text("更多脚本工具", style = MaterialTheme.typography.titleMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ScriptToolButton(
                icon = Icons.Default.Description,
                title = "普通脚本",
                description = "手动编辑点击、延迟和滑动命令。",
                onClick = onOpenScriptList
            )
            ScriptToolButton(
                icon = Icons.Default.PlayArrow,
                title = "自定义脚本",
                description = "把多个脚本动作组合成一组流程。",
                onClick = onOpenScriptGroup
            )
            ScriptToolButton(
                icon = Icons.Default.Settings,
                title = "按键设置",
                description = "创建悬浮按钮并绑定脚本。",
                onClick = onOpenKeyBinding
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun MineTabContent(
    modifier: Modifier = Modifier,
    packageName: String,
    permissionStatuses: List<PermissionStatus>,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenCompliance: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设备与权限", style = MaterialTheme.typography.titleMedium)

        PermissionStatusPanel(
            statuses = permissionStatuses,
            onOpenAccessibility = onOpenAccessibility,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onOpenBatterySettings = onOpenBatterySettings,
            onOpenAppSettings = onOpenAppSettings
        )

        OutlinedButton(
            onClick = onOpenCompliance,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("权限与隐私说明")
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("隐私声明", style = MaterialTheme.typography.titleMedium)
                Text(
                    "脚本与配置保存在本机，应用不上传脚本、屏幕内容、账号或支付数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("权限用途", style = MaterialTheme.typography.titleMedium)
                PermissionPurposeText("无障碍服务", "执行你主动创建或启动的点击、长按、滑动脚本。")
                PermissionPurposeText("悬浮窗", "显示点位选择器、开始/停止按钮和按键悬浮控制。")
                PermissionPurposeText("后台运行", "降低 HyperOS 清理服务导致脚本中断的概率。")
            }
        }

        Text(
            text = "ADB增强：adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    Util.copyText(
                        "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS",
                        context
                    )
                    Toast.makeText(context, "已复制命令", Toast.LENGTH_SHORT).show()
                }
        )

        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("打开应用设置")
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun ScriptToolButton(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "打开",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PermissionPurposeText(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickStartTutorialDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("快速连点使用教程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TutorialStepRow(1, "开启无障碍与悬浮窗权限。")
                TutorialStepRow(2, "点击“打开连点器”显示点位选择器和开始按钮。")
                TutorialStepRow(3, "拖动点位到目标位置。")
                TutorialStepRow(4, "设置点击次数和间隔。")
                TutorialStepRow(5, "点击悬浮“开始/停止”控制运行。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun TutorialStepRow(index: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ComplianceDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("权限与隐私说明") },
        text = {
            Text(
                "无障碍服务：仅执行你主动创建或启动的点击、长按、滑动脚本。\n\n" +
                    "悬浮窗：用于显示点位选择器、开始/停止按钮和按键悬浮控制。\n\n" +
                    "后台运行：用于降低 HyperOS 清理服务导致脚本中断的概率。\n\n" +
                    "数据处理：脚本与配置保存在本机，应用不上传脚本、屏幕内容、账号或支付数据。"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了")
            }
        }
    )
}

@Composable
private fun PermissionStatusPanel(
    statuses: List<PermissionStatus>,
    onOpenAccessibility: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    if (statuses.isEmpty()) {
        Text("正在读取权限状态...", style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        statuses.forEachIndexed { index, status ->
            val stateColor = when {
                !status.showStatus -> MaterialTheme.colorScheme.onSurfaceVariant
                status.isWarning -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }
            val stateIcon = when {
                !status.showStatus -> Icons.Default.Info
                status.isWarning -> Icons.Default.ErrorOutline
                else -> Icons.Default.CheckCircle
            }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    leadingContent = {
                        Icon(
                            imageVector = stateIcon,
                            contentDescription = null,
                            tint = stateColor
                        )
                    },
                    headlineContent = {
                        Text(status.title, style = MaterialTheme.typography.titleSmall)
                    },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(status.description)
                            if (status.showStatus) {
                                Text(
                                    status.statusText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = stateColor
                                )
                            }
                        }
                    },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                when (index) {
                                    0 -> onOpenAccessibility()
                                    1 -> onOpenOverlaySettings()
                                    2 -> onOpenBatterySettings()
                                    else -> onOpenAppSettings()
                                }
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                        ) {
                            Text(status.actionLabel)
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    ClickDeviceTheme {
        MainScreen(
            isFloatingWindowShow = false,
            clickCount = "",
            clickInterval = "1000",
            permissionStatuses = listOf(
                PermissionStatus("无障碍服务", "用于执行点击和滑动", false, "去开启"),
                PermissionStatus("悬浮窗", "用于显示控制按钮", true, "去授权")
            ),
            showComplianceDialog = false,
            onOpenAccessibility = {},
            onOpenCompliance = {},
            onOpenOverlaySettings = {},
            onOpenBatterySettings = {},
            onOpenAppSettings = {},
            onStartClickDevice = {},
            onOpenScriptList = {},
            onOpenRecordScript = {},
            onOpenScriptGroup = {},
            onOpenKeyBinding = {},
            onCountChange = {},
            onIntervalChange = {}
        )
    }
}
