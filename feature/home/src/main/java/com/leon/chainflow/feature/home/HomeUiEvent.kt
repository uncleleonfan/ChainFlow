package com.leon.chainflow.feature.home

import com.leon.chainflow.core.common.mvi.UiEvent

/**
 * 首页一次性事件
 */
sealed interface HomeUiEvent : UiEvent {
    /** 跳转到设置页 */
    data object NavigateToSettings : HomeUiEvent

    /** 跳转 Token 详情（symbol = Binance 交易对） */
    data class NavigateToTokenDetail(val symbol: String) : HomeUiEvent
}
