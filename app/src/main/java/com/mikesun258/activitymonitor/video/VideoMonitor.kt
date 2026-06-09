package com.mikesun258.activitymonitor.video

import android.content.Intent
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class VideoMonitor : IXposedHookLoadPackage {
    private val TAG = "VideoMonitor"
    private val BROADCAST_VIDEO_SWITCH = "com.mikesun258.activitymonitor.VIDEO_SWITCH"
    private val MACRODROID_PKG = "com.arlosoft.macrodroid"

    // 目标包名列表，可根据需要添加
    private val targetPackages = listOf(
        "com.bytedance.douyin",
        "com.dragon.read",
        "com.kylin.read"
    )

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in targetPackages) return
        Log.d(TAG, "模块已加载，当前包名：${lpparam.packageName}")
        hookLinearLayoutManager(lpparam)
    }

    private fun hookLinearLayoutManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "开始 Hook LinearLayoutManager")
        try {
            val lmClass = lpparam.classLoader.loadClass("androidx.recyclerview.widget.LinearLayoutManager")
            Log.d(TAG, "找到 LinearLayoutManager 类")

            // Hook findFirstCompletelyVisibleItemPosition 方法
            XposedBridge.hookAllMethods(lmClass, "findFirstCompletelyVisibleItemPosition", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val pos = param.result as? Int ?: return
                    val lm = param.thisObject as LinearLayoutManager
                    val recyclerView = lm.recyclerView ?: return

                    // 过滤无效位置
                    if (pos == -1) return
                    sendBroadcast(recyclerView, pos)
                }
            })
            Log.d(TAG, "LinearLayoutManager Hook 成功")
        } catch (e: Throwable) {
            Log.e(TAG, "LinearLayoutManager Hook Error", e)
        }
    }

    private fun sendBroadcast(view: View, position: Int) {
        val intent = Intent(BROADCAST_VIDEO_SWITCH).apply {
            putExtra("pkg_name", view.context.packageName)
            putExtra("video_position", position)
            putExtra("view_id", view.id)
            setPackage(MACRODROID_PKG)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        view.context.sendBroadcast(intent)
        Log.d(TAG, "已发送广播：pos=$position")
    }
}
