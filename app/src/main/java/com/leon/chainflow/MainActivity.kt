package com.leon.chainflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.leon.chainflow.app.ChainFlowApp
import dagger.hilt.android.AndroidEntryPoint

/**
 * 唯一 Activity —— 单 Activity + Compose Navigation 架构
 *
 * 职责刻意保持最小：
 * 1. @AndroidEntryPoint 启用 Hilt 的成员注入（Activity 级）；
 * 2. enableEdgeToEdge()：沉浸式全屏（Trading Terminal 深色风格需要），
 *    系统栏由 Compose 通过 WindowInsets 自己处理；
 * 3. 挂载 [ChainFlowApp] 根组件（导航 + 底部 Tab）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 边到边绘制：内容延伸到状态栏 / 导航栏之下
        enableEdgeToEdge()

        setContent {
            ChainFlowApp()
        }
    }
}
