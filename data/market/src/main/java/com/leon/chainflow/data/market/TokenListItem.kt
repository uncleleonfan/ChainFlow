package com.leon.chainflow.data.market

/**
 * 统一 Token 列表项（UI 层唯一依赖的行情模型 —— 屏蔽底层数据源差异）
 *
 * 设计动机：行情来源从 CoinGecko 切到 Binance vision，UI 不应感知数据源；
 * 未来若再换源（OKX 等），只需改 Repository 映射，UI 不动。
 *
 * @param symbol 交易对（如 BTCUSDT —— Binance 语义）
 * @param baseAsset 基础币（如 BTC）
 * @param price 最新价（USDT 计价）
 * @param changePercent 24h 涨跌幅
 * @param quoteVolume24h 24h 成交额（USDT 计价）
 */
data class TokenListItem(
    val symbol: String,
    val baseAsset: String,
    val price: Double,
    val changePercent: Double,
    val quoteVolume24h: Double,
)
