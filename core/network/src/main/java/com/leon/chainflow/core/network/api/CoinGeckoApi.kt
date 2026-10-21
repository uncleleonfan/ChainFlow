package com.leon.chainflow.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * CoinGecko 公共行情 API（免 Key，PRD §8/§9 数据源）
 *
 * 端点说明：
 * - /markets        ：Token 列表（价格 / 24h 涨跌 / 成交量 / 市值，PRD §8）
 * - /coins/{id}/ohlc：K 线数据（1H/4H/1D/1W，PRD §9）
 * - /search         ：搜索（列表页搜索框）
 */
interface CoinGeckoApi {

    /**
     * 获取市场列表（按市值排序）
     *
     * @param vsCurrency 计价货币（usd）
     * @param perPage 每页数量（列表页一次拉 100，滚动加载更多）
     * @param page 页码（从 1 开始）
     */
    @GET("coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = false,
    ): List<MarketDto>

    /** 搜索单个 Token 详情（复用 markets 端点 + 精确 id） */
    @GET("coins/markets")
    suspend fun getMarket(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("ids") ids: String,
    ): List<MarketDto>

    /**
     * OHLC K 线数据（PRD §9 Candlestick Chart）
     *
     * @param days 天数：1 = 30 分钟粒度（≈1H 视图），7/14/30 = 4 小时粒度，
     *              90/180/365 = 4 天粒度 —— 由 UI 层映射到 [1H][4H][1D][1W] 按钮
     */
    @GET("coins/{id}/ohlc")
    suspend fun getOhlc(
        @Path("id") coinId: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String,
    ): List<OhlcDto>

    /** 趋势搜索（Home 页 Trending Tokens，PRD §7） */
    @GET("search/trending")
    suspend fun getTrending(): TrendingDto
}

// ================= DTO 定义（与 CoinGecko 响应 JSON 字段一一对应） =================

/** 市场列表行（PRD §8：Logo/Symbol/Price/24H Change/Volume/Market Cap） */
@Serializable
data class MarketDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("market_cap") val marketCap: Double? = null,
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    @SerialName("total_volume") val totalVolume: Double? = null,
    @SerialName("price_change_percentage_24h") val priceChangePercentage24h: Double? = null,
)

/** OHLC K 线：[时间戳(秒), open, high, low, close] */
@Serializable
data class OhlcDto(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

/** 趋势币响应（只取我们需要的字段） */
@Serializable
data class TrendingDto(
    val coins: List<TrendingCoinItem> = emptyList(),
)

@Serializable
data class TrendingCoinItem(
    val item: TrendingCoin = TrendingCoin(),
)

@Serializable
data class TrendingCoin(
    val id: String = "",
    val name: String = "",
    val symbol: String = "",
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    @SerialName("data") val data: TrendingPriceData? = null,
)

@Serializable
data class TrendingPriceData(
    val price: Double? = null,
    @SerialName("price_change_percentage_24h") val priceChangePercentage24h: Double? = null,
)
