package com.mikesun258.activitymonitor.video

import android.content.Intent
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class VideoMonitor : IXposedHookLoadPackage {
    private val TAG = "VideoMonitor"
    private val BROADCAST_VIDEO_SWITCH = "com.mikesun258.activitymonitor.VIDEO_SWITCH"
    private val MACRODROID_PKG = "com.joaomgcd.tasker"

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
        Log.d(TAG, "模块已加载，当前包名：${lpparam.packageName}")
        if (lpparam.packageName in targetPackages) {
            hookRecyclerView(lpparam)
        }
    }

    private fun hookRecyclerView(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "开始 Hook RecyclerView，包名：${lpparam.packageName}")
        try {
            val rvClass = lpparam.classLoader.loadClass("androidx.recyclerview.widget.RecyclerView")
            Log.d(TAG, "找到 RecyclerView 类")

            XposedBridge.hookAllConstructors(rvClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val recyclerView = param.thisObject as RecyclerView
                    Log.d(TAG, "新 RecyclerView 创建：$recyclerView")

                    val wrapperListener = object : RecyclerView.OnScrollListener() {
                        private var lastPos = -1

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            val lm = recyclerView.layoutManager
                            if (lm is androidx.recyclerview.widget.LinearLayoutManager) {
                                val pos = lm.findFirstCompletelyVisibleItemPosition()
                                if (pos != -1 && pos != lastPos && pos != lastPos) {
                                    lastPos = pos
                                    sendBroadcast(recyclerView, pos)
                                }
                            }
                        }
                    }
                    recyclerView.addOnScrollListener(wrapperListener)
                    Log.d(TAG, "监听器已注入")
                }
            })
            Log.d(TAG, "RV Hook 成功")
        } catch (e: Throwable) {
            Log.e(TAG, "RV Hook Error", e)
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
