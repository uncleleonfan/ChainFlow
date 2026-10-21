package com.leon.chainflow.feature.activity

import com.leon.chainflow.core.common.mvi.UiEvent

/**
 * 活动页一次性事件
 */
sealed interface ActivityUiEvent : UiEvent {
    /** 打开浏览器查看交易（UI 层执行 Intent 跳转） */
    data class OpenInExplorer(val url: String) : ActivityUiEvent
}
