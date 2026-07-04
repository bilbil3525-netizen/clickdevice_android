package com.example.clickdevice

import android.content.Context
import android.graphics.Path
import com.example.clickdevice.bean.Bean
import com.example.clickdevice.bean.RecordScriptCmd
import com.example.clickdevice.helper.DeviceWindowMetricsProvider

class RecordScriptExecutor {

    var delayCoefficient = 1.0
    var recordScriptInterface: RecordScriptInterface? = null


    fun run(data: List<RecordScriptCmd>) {
        try {
            repeat(data.size) {
                if (recordScriptInterface == null || !recordScriptInterface!!.isRun()) {
                    return@repeat
                }
                when (data[it].type) {
                    RecordScriptCmd.Type.Delay -> {
                        delay(data[it])
                    }

                    RecordScriptCmd.Type.Gesture -> {
                        gesture(it, data[it])
                    }

                    else -> {
                    }
                }
            }
        } catch (e: Throwable) {
            recordScriptInterface?.onError("录制脚本执行失败: ${e.message ?: "未知错误"}")
        }

    }

    private fun delay(recordScriptCmd: RecordScriptCmd) {
        delay(recordScriptCmd.delayed.toLong())
    }

    fun sleep(time: Long): Boolean {
        if (time <= 0) {
            return false
        }
        val count = time / 10
        val t = time % 10
        Thread.sleep(t)
        for (i in 0 until count) {
            if (recordScriptInterface == null || !recordScriptInterface!!.isRun()) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }

    fun delay(time: Long): Boolean {
        if (time <= 0) {
            return false
        }
        val adjustedTime = (time * delayCoefficient).toLong()
        return sleep(adjustedTime)
    }

    private fun gesture(position: Int, recordScriptCmd: RecordScriptCmd) {
        if (delay(recordScriptCmd.delayed.toLong())) {
            return
        }

        recordScriptInterface?.apply {
            if (!isRun()||recordScriptCmd.path == null || recordScriptCmd.path.isEmpty()) {
                return@apply
            }

            val scaledPathData = scalePathIfNeeded(recordScriptCmd)
            val bean = scaledPathData[0]
            preDispatchGesture(bean.x, bean.y)
            sleep(100)
            val createPath = createPath(scaledPathData)
            var duration = (recordScriptCmd.duration * delayCoefficient).toInt()
            if (duration < 10) {
                duration = 10
            }
            val repeatCount = recordScriptCmd.repeatCount.coerceAtLeast(1)
            try {
                repeat(repeatCount) { repeatIndex ->
                    if (!isRun()) {
                        return@repeat
                    }
                    dispatchGesture(position, createPath, duration)
                    sleep(duration.toLong())
                    if (repeatIndex < repeatCount - 1) {
                        sleep(80)
                    }
                }
            } catch (e: Throwable) {
                onError("手势执行失败: ${e.message ?: "未知错误"}")
            }
            sleep(100)
            endDispatchGesture()
        }

    }

    private fun createPath(data: MutableList<Bean>): Path {
        val path = Path()
        val bean = data[0]
        path.moveTo(bean.x.toFloat(), bean.y.toFloat())
        for (i in 1 until data.size) {
            val bean2 = data[i]
            path.lineTo(bean2.x.toFloat(), bean2.y.toFloat())
        }
        return path
    }

    private fun scalePathIfNeeded(recordScriptCmd: RecordScriptCmd): MutableList<Bean> {
        val sourcePath = recordScriptCmd.path
        val context = recordScriptInterface?.context()
        if (
            context == null ||
            recordScriptCmd.coordinateVersion <= 0 ||
            recordScriptCmd.recordScreenWidth <= 0 ||
            recordScriptCmd.recordScreenHeight <= 0
        ) {
            return sourcePath
        }
        return sourcePath.map { point ->
            Bean(
                DeviceWindowMetricsProvider.scaleX(point.x, recordScriptCmd.recordScreenWidth, context),
                DeviceWindowMetricsProvider.scaleY(point.y, recordScriptCmd.recordScreenHeight, context)
            )
        }.toMutableList()
    }


    interface RecordScriptInterface {

        fun isRun(): Boolean

        fun context(): Context? = null

        fun preDispatchGesture(x: Int, y: Int)

        fun dispatchGesture(position: Int, path: Path, duration: Int)

        fun endDispatchGesture()

        fun onError(message: String) {}


    }
}
