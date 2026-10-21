package com.leon.chainflow

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.leon.chainflow.data.market.BinancePriceStream
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * ChainFlow 应用入口
 *
 * 职责：
 * 1. @HiltAndroidApp 启用依赖注入容器
 * 2. 注册应用级生命周期观察：行情 WS 连接跟随前台/后台
 *    —— 前台保活（实时推送），后台断开（省电省流量，PRD §10.2 lifecycle awareness）
 *
 * 冷启动保持精简（PRD §30：< 1.5s），WS 懒连接由首个行情页面触发。
 */
@HiltAndroidApp
class ChainFlowApplication : Application() {

    @Inject
    lateinit var priceStream: BinancePriceStream

    override fun onCreate() {
        super.onCreate()

        // 前后台切换：实时流按需连接/断开（进程级生命周期，与页面无关）
        //
        // 注意：这里【必须同步调用】而非 appScope.launch ——
        // onStop/onStart 都在主线程串行回调，同步执行可保证顺序；
        // 若异步派发到其它 Dispatcher，快速切后台再回前台时
        // disconnect 可能晚于 resume 执行，刚重连的连接又被关掉。
        // （两个方法内部只做 close()/newWebSocket() 等非阻塞操作）
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // 回前台：已有订阅则立即重连并重放（无订阅保持懒连接）
                    Log.d(TAG, "onStart: resume realtime stream")
                    priceStream.resumeIfNeeded()
                }

                override fun onStop(owner: LifecycleOwner) {
                    // 退后台：断开 WS（订阅集合保留，回前台重连后自动重放）
                    Log.d(TAG, "onStop: disconnect realtime stream")
                    priceStream.disconnect()
                }
            },
        )
    }

    private companion object {
        const val TAG = "ChainFlowApplication"
    }
}
