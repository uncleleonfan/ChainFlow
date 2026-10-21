package com.leon.chainflow.feature.token

import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.data.market.Candle

/**
 * K 线周期（PRD §9：[1H] [4H] [1D] [1W]）→ Binance klines interval 映射
 */
enum class ChartPeriod(val label: String, val interval: String) {
    H1("1H", "1h"),
    H4("4H", "4h"),
    D1("1D", "1d"),
    W1("1W", "1w"),
}

/**
 * Token 详情页状态（PRD §9）
 */
data class TokenDetailUiState(
    /** Binance 交易对（如 ETHUSDT，路由参数） */
    val symbol: String = "",
    /** 基础币（如 ETH） */
    val baseAsset: String = "",
    /** REST 快照价（WS 未推送前的兜底） */
    val basePrice: Double? = null,
    /** WS 实时价 */
    val livePrice: Double? = null,
    /** 24h 涨跌幅 */
    val changePercent: Double? = null,
    /** 24h 成交额（USDT） */
    val quoteVolume24h: Double? = null,
    /** 24h 最高 / 最低 */
    val high24h: Double? = null,
    val low24h: Double? = null,
    /** 当前 K 线周期 */
    val chartPeriod: ChartPeriod = ChartPeriod.D1,
    /** 当前周期 K 线 */
    val candles: List<Candle> = emptyList(),
    /** 页面加载状态 */
    val isLoading: Boolean = false,
    /** K 线加载中 */
    val isChartLoading: Boolean = false,
    /** 错误 */
    val error: AppError? = null,
) : UiState {
    /** 展示价：WS 优先，REST 兜底 */
    val displayPrice: Double? get() = livePrice ?: basePrice
}
