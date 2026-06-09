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
        // 强制日志：模块一加载就输出
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

            // Hook addOnScrollListener 方法，不直接替换参数，改用代理包装
            XposedBridge.hookAllMethods(rvClass, "addOnScrollListener", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val originListener = param.args[0] ?: return
                    // 已经被我们包装过的监听器，跳过
                    if (originListener.javaClass.name.contains("WrapperListener")) {
                        Log.d(TAG, "监听器已被包装，跳过")
                        return
                    }
                    Log.d(TAG, "检测到新监听器：${originListener.javaClass.name}")
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val recyclerView = param.thisObject as RecyclerView
                    val originListener = param.args[0] as? RecyclerView.OnScrollListener ?: return

                    // 反射获取 RecyclerView 里的监听器列表（隐藏字段）
                    val listenersField = RecyclerView::class.java.getDeclaredField("mOnScrollListeners")
                    listenersField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val listenerList = listenersField.get(recyclerView) as MutableList<RecyclerView.OnScrollListener>

                    // 如果已经被包装过，跳过
                    if (originListener.javaClass.name.contains("WrapperListener")) return

                    // 移除原监听器，添加包装器
                    recyclerView.removeOnScrollListener(originListener)
                    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        private var lastPos = -1

                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            // 调用原监听器逻辑
                            originListener.onScrolled(recyclerView, dx, dy)
                            val lm = recyclerView.layoutManager
                            if (lm is androidx.recyclerview.widget.LinearLayoutManager) {
                                val pos = lm.findFirstCompletelyVisibleItemPosition()
                                if (pos != -1 && pos != lastPos) {
                                    lastPos = pos
                                    sendBroadcast(recyclerView, pos)
                                }
                            }
                        }

                        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                            originListener.onScrollStateChanged(recyclerView, newState)
                        }
                    })
                    Log.d(TAG, "监听器包装完成")
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
            // 定向发给 MacroDroid，避免被系统拦截
            setPackage(MACRODROID_PKG)
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        view.context.sendBroadcast(intent)
        Log.d(TAG, "已发送广播：pos=$position")
    }
}
