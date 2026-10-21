package com.leon.chainflow.feature.market

import com.leon.chainflow.core.common.mvi.UiIntent

/**
 * 市场页意图（PRD §8：Search / Sort / Pull to Refresh）
 */
sealed interface MarketUiIntent : UiIntent {
    /** 进入页面：首次加载 + 启动实时价订阅 */
    data object Load : MarketUiIntent

    /** 搜索框输入变化 */
    data class SearchQueryChanged(val query: String) : MarketUiIntent

    /** 切换排序 */
    data class SortSelected(val sort: MarketSort) : MarketUiIntent

    /** 下拉刷新 */
    data object Refresh : MarketUiIntent

    /** 点击 Token 行 → 跳转详情（symbol 即 Binance 交易对，详情页直接复用） */
    data class TokenClicked(val symbol: String) : MarketUiIntent
}
