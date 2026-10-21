package com.leon.chainflow.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Binance 公开数据 API（data-api.binance.vision，2025 国内直连可用）
 *
 * 为什么新增：CoinGecko 直连不可达（需代理），而 binance.vision 是官方公开镜像：
 * - 无地域限制（主站 api.binance.com 返回 451）
 * - 免 API Key、免代理
 * - WS（data-stream.binance.vision）与 REST 同源，一条数据链路
 *
 * 端点说明：
 * - ticker/24hr ：行情快照（价格/涨跌幅/成交量），支持批量 symbols 查询
 * - klines      ：K 线（OHLCV），interval 粒度 1m~1M
 */
interface BinanceApi {

    /**
     * 24 小时行情快照
     *
     * @param symbols JSON 数组格式的交易对列表（URL 编码后传入），
     *                如 ["BTCUSDT","ETHUSDT"]；为空则返回全部（量大，慎用）
     */
    @GET("ticker/24hr")
    suspend fun getTickers(
        @Query("symbols") symbols: String? = null,
    ): List<BinanceTicker24hDto>

    /**
     * K 线数据（OHLCV）
     *
     * @param symbol 交易对（如 BTCUSDT）
     * @param interval 粒度：1m/5m/15m/1h/4h/1d/1w …
     * @param limit 根数（默认 100，最大 1000）
     */
    @GET("klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("limit") limit: Int = 200,
    ): List<List<kotlinx.serialization.json.JsonElement>>
}

/** 24h 行情快照（只声明需要的字段，减少解析开销） */
@Serializable
data class BinanceTicker24hDto(
    val symbol: String,
    /** 最新价（字符串数字，Binance 用字符串避免浮点精度问题） */
    @SerialName("lastPrice") val lastPrice: String,
    /** 24h 涨跌幅百分比（如 "-1.952"） */
    @SerialName("priceChangePercent") val priceChangePercent: String,
    /** 24h 成交量（以基础币计，如 BTC 个数） */
    val volume: String,
    /** 24h 成交额（以 USDT 计价）——列表页展示用这个 */
    @SerialName("quoteVolume") val quoteVolume: String,
    /** 24h 最高价 */
    @SerialName("highPrice") val highPrice: String,
    /** 24h 最低价 */
    @SerialName("lowPrice") val lowPrice: String,
) {
    /** 便捷转换：最新价 */
    val priceDouble: Double get() = lastPrice.toDoubleOrNull() ?: 0.0

    /** 便捷转换：24h 涨跌幅 */
    val changeDouble: Double get() = priceChangePercent.toDoubleOrNull() ?: 0.0

    /** 便捷转换：24h 成交额（USDT） */
    val quoteVolumeDouble: Double get() = quoteVolume.toDoubleOrNull() ?: 0.0
}
