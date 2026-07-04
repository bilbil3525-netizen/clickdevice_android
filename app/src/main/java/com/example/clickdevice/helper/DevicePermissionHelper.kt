package com.example.clickdevice.helper

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import com.example.clickdevice.MyService

data class PermissionStatus(
    val title: String,
    val description: String,
    val granted: Boolean,
    val actionLabel: String,
    val statusText: String = if (granted) "已就绪" else "需要配置",
    val isWarning: Boolean = !granted
)

object DevicePermissionHelper {
    fun collectStatuses(context: Context): List<PermissionStatus> {
        return listOf(
            PermissionStatus(
                title = "无障碍服务",
                description = "用于执行你主动配置的点击、长按和滑动手势",
                granted = isAccessibilityServiceEnabled(context, MyService::class.java),
                actionLabel = "去开启"
            ),
            PermissionStatus(
                title = "悬浮窗",
                description = "用于显示开始/停止按钮和点位选择器",
                granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context),
                actionLabel = "去授权"
            ),
            PermissionStatus(
                title = "后台省电",
                description = "建议设置为不限制，降低 HyperOS 清理服务的概率",
                granted = isIgnoringBatteryOptimizations(context),
                actionLabel = "去设置"
            ),
            PermissionStatus(
                title = "自启动",
                description = "HyperOS 需在系统管家中手动允许，系统 API 无法直接读取状态",
                granted = false,
                actionLabel = "查看",
                statusText = "需手动确认",
                isWarning = false
            )
        )
    }

    fun isAccessibilityServiceEnabled(
        context: Context,
        serviceClass: Class<out AccessibilityService>
    ): Boolean {
        val expected = ComponentName(context, serviceClass).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        return splitter.any { it.equals(expected, ignoreCase = true) }
    }

    fun accessibilitySettingsIntent(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    fun overlaySettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun batteryOptimizationIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
    }

    fun appSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
