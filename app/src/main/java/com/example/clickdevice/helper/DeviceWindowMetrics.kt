package com.example.clickdevice.helper

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager

data class DeviceWindowMetrics(
    val width: Int,
    val height: Int,
    val insetLeft: Int,
    val insetTop: Int,
    val insetRight: Int,
    val insetBottom: Int
) {
    val usableWidth: Int get() = (width - insetLeft - insetRight).coerceAtLeast(1)
    val usableHeight: Int get() = (height - insetTop - insetBottom).coerceAtLeast(1)
}

object DeviceWindowMetricsProvider {
    fun current(context: Context): DeviceWindowMetrics {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds: Rect = metrics.bounds
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return DeviceWindowMetrics(
                width = bounds.width(),
                height = bounds.height(),
                insetLeft = insets.left,
                insetTop = insets.top,
                insetRight = insets.right,
                insetBottom = insets.bottom
            )
        }
        val displayMetrics = context.resources.displayMetrics
        return DeviceWindowMetrics(
            width = displayMetrics.widthPixels,
            height = displayMetrics.heightPixels,
            insetLeft = 0,
            insetTop = 0,
            insetRight = 0,
            insetBottom = 0
        )
    }

    fun scaleX(x: Int, fromWidth: Int, context: Context): Int {
        if (fromWidth <= 0) return x
        return (x * current(context).width / fromWidth.toFloat()).toInt()
    }

    fun scaleY(y: Int, fromHeight: Int, context: Context): Int {
        if (fromHeight <= 0) return y
        return (y * current(context).height / fromHeight.toFloat()).toInt()
    }
}
