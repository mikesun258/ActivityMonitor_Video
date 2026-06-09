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
    // MacroDroid 真实包名
    private val MACRODROID_PKG = "com.arlosoft.macrodroid"

    private val targetPackages = listOf(
        "com.bytedance.douyin",
        "com.bytedance.douyin.lite",
        "com.bytedance.douyin.extreme",
        "com.bytedance.douyin3",
        "com.bytedance.douyin2",
        "com.bytedance.douyinselected",
        "com.ik.mang",
        "com.ik.shortdrama",
        "com.hippo.drama",
        "com.kuaishou.nebula",
        "com.huolong.mangju",
        "com.kylin.read"
    )

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName !in targetPackages) return
        Log.d(TAG, "模块已加载 -> ${lpparam.packageName}")
        hookLinearLayoutManager(lpparam)
    }

    private fun hookLinearLayoutManager(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val lmClass = lpparam.classLoader.loadClass("androidx.recyclerview.widget.LinearLayoutManager")
            Log.d(TAG, "找到 LinearLayoutManager")

            XposedBridge.hookAllMethods(lmClass, "findFirstCompletelyVisibleItemPosition", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val pos = param.result as? Int ?: return
                    if (pos == -1) return

                    val lm = param.thisObject as LinearLayoutManager
                    val rv = lm.recyclerView ?: return
                    sendBroadcast(rv, pos)
                }
            })
            Log.d(TAG, "LinearLayoutManager Hook 完成")
        } catch (e: Throwable) {
            Log.e(TAG, "Hook 异常", e)
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
        Log.d(TAG, "发送广播 pos=$position")
    }
}
