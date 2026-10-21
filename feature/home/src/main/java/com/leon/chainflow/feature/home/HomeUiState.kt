package com.leon.chainflow.feature.home

import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.data.market.BinancePriceStream
import com.leon.chainflow.data.market.TokenListItem

/**
 * 首页状态（PRD §7：Market Overview + Trending Tokens）
 *
 * - 总览卡片：BTC/ETH/SOL 实时价（WS）
 * - Top Movers：24h 涨幅榜（Binance 快照，MVP 用公开数据代替 Smart Money 算法）
 */
data class HomeUiState(
    /** 全量实时报价（key = symbol，如 "BTCUSDT"） */
    val tickers: Map<String, BinancePriceStream.Ticker> = emptyMap(),
    /** Top Movers（24h 涨幅前 10，PRD §7 Trending） */
    val topMovers: List<TokenListItem> = emptyList(),
    /** WS 连接状态（顶部徽章） */
    val wsConnected: Boolean = false,
    /** 是否加载中 */
    val isLoading: Boolean = false,
    /** 错误（只阻断 Top Movers 区块） */
    val error: AppError? = null,
) : UiState
