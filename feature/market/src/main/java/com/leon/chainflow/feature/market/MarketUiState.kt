package com.leon.chainflow.feature.market

import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.data.market.TokenListItem

/** 列表排序方式（PRD §8 Sort） */
enum class MarketSort(val label: String) {
    VOLUME("成交额"),
    PRICE_CHANGE("24h 涨跌"),
    PRICE("价格"),
}

/**
 * 市场页状态（PRD §8）
 *
 * 数据流组合：REST 快照（Binance vision）+ WS 实时价覆盖（同 symbol 行实时跳动）
 */
data class MarketUiState(
    /** REST 拉取的 Token 列表 */
    val tokens: List<TokenListItem> = emptyList(),
    /** WS 实时报价（symbol → 报价）—— 渲染时覆盖同名行 */
    val livePrices: Map<String, com.leon.chainflow.data.market.BinancePriceStream.Ticker> = emptyMap(),
    /** 搜索关键字（对 baseAsset 前端过滤） */
    val searchQuery: String = "",
    /** 当前排序 */
    val sort: MarketSort = MarketSort.VOLUME,
    /** 是否下拉刷新中 */
    val isRefreshing: Boolean = false,
    /** 初次加载中 */
    val isLoading: Boolean = false,
    /** 错误 */
    val error: AppError? = null,
) : UiState {

    /** 展示列表 = 过滤 + 排序 + 实时价覆盖（纯函数，Compose 直接读取） */
    val displayList: List<TokenListItem>
        get() {
            var list = tokens
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().uppercase()
                list = list.filter { it.baseAsset.contains(q) }
            }
            list = when (sort) {
                MarketSort.VOLUME -> list.sortedByDescending { it.quoteVolume24h }
                MarketSort.PRICE_CHANGE -> list.sortedByDescending { it.changePercent }
                MarketSort.PRICE -> list.sortedByDescending { it.price }
            }
            return list
        }
}
