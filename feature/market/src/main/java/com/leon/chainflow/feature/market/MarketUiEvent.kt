package com.leon.chainflow.feature.market

import com.leon.chainflow.core.common.mvi.UiEvent

/**
 * 市场页一次性事件
 */
sealed interface MarketUiEvent : UiEvent {
    /** 跳转 Token 详情页（symbol = Binance 交易对，如 ETHUSDT） */
    data class NavigateToTokenDetail(val symbol: String) : MarketUiEvent
}
