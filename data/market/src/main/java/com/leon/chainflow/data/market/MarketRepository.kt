package com.leon.chainflow.data.market

import com.leon.chainflow.core.common.coroutines.DispatcherProvider
import com.leon.chainflow.core.common.result.ChainFlowResult
import com.leon.chainflow.core.common.result.chainFlowRunCatching
import com.leon.chainflow.core.network.api.BinanceApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 行情仓库（PRD §8/§9/§10 数据门面）
 *
 * 数据源策略（2025 国内网络实测）：
 * - 列表 / K 线 / 实时价：**Binance vision**（data-api/data-stream.binance.vision）——
 *   官方公开镜像，无地域限制、免 Key、免代理直连，一条数据链路贯穿 REST+WS
 * - （CoinGecko 已弃用为主源：直连不可达；API 保留备用，未来可作聚合补充）
 *
 * K 线周期映射（PRD §9 [1H][4H][1D][1W]）：
 *   1H→interval=1h, 4H→4h, 1D→1d, 1W→1w
 */
@Singleton
class MarketRepository @Inject constructor(
    private val binanceApi: BinanceApi,
    private val priceStream: BinancePriceStream,
    private val dispatchers: DispatcherProvider,
) {

    /** 实时报价表（WS StateFlow）—— UI 直接 collect */
    val realtimeTickers get() = priceStream.tickers

    /** WS 连接状态（Connected/Connecting/Disconnected 徽章） */
    val wsState get() = priceStream.connectionState

    /**
     * 启动实时价格流（页面进入时调用）
     *
     * 注意：连接生命周期已绑定应用进程（见 BinancePriceStream 文档），
     * 页面只负责追加订阅与 collect —— 切 Tab 不会断流。
     *
     * @param symbols 需要订阅的 USDT 交易对（如 ["BTCUSDT"]）
     */
    fun startRealtime(symbols: List<String> = DEFAULT_SYMBOLS) {
        priceStream.start(symbols)
    }

    /** 停止实时流（页面退出，断开 WS） */
    fun stopRealtime() = priceStream.stop()

    /**
     * Market 页核心交易对清单（PRD §8 Token List）
     *
     * 说明：Binance ticker/24hr 全量拉取约 3000+ 对（含杠杆代币等噪音），
     * 这里维护一份精选主流清单（约 30 个，覆盖 PRD 场景），
     * 一次批量请求快照 + WS 实时覆盖 —— 后续分页/全量可平滑演进。
     */
    suspend fun getMarketList(): ChainFlowResult<List<TokenListItem>> =
        withContext(dispatchers.io) {
            chainFlowRunCatching {
                // symbols 参数需传 JSON 数组字符串（Binance 协议要求）
                val symbolsJson = CORE_SYMBOLS.joinToString(
                    ",", prefix = "[", postfix = "]",
                ) { "\"$it\"" }
                binanceApi.getTickers(symbolsJson)
                    .map { dto ->
                        TokenListItem(
                            symbol = dto.symbol,
                            baseAsset = dto.symbol.removeSuffix("USDT"),
                            price = dto.priceDouble,
                            changePercent = dto.changeDouble,
                            quoteVolume24h = dto.quoteVolumeDouble,
                        )
                    }
                    .sortedByDescending { it.quoteVolume24h } // 默认按成交额排序
            }
        }

    /**
     * 单个交易对的 24h 详情快照（详情页头部 + 统计行）
     *
     * @return null = 交易对不存在
     */
    suspend fun getTickerDetail(symbol: String): ChainFlowResult<TokenListItem?> =
        withContext(dispatchers.io) {
            chainFlowRunCatching {
                binanceApi.getTickers(symbols = "[\"$symbol\"]")
                    .firstOrNull()
                    ?.let { dto ->
                        TokenListItem(
                            symbol = dto.symbol,
                            baseAsset = dto.symbol.removeSuffix("USDT"),
                            price = dto.priceDouble,
                            changePercent = dto.changeDouble,
                            quoteVolume24h = dto.quoteVolumeDouble,
                        )
                    }
            }
        }

    /**
     * K 线数据（PRD §9 OHLC）
     *
     * @param interval Binance 粒度：1h / 4h / 1d / 1w（由 UI 周期按钮映射）
     * @return OHLC 列表（时间戳为开盘时间，ms）
     */
    suspend fun getKlines(
        symbol: String,
        interval: String,
    ): ChainFlowResult<List<Candle>> = withContext(dispatchers.io) {
        chainFlowRunCatching {
            binanceApi.getKlines(symbol = symbol, interval = interval, limit = 200)
                .map { k ->
                    // klines 返回为 JSON 数组的数组：[openTime, open, high, low, close, volume, ...]
                    Candle(
                        timestamp = (k[0] as JsonPrimitive).content.toLong(),
                        open = (k[1] as JsonPrimitive).content.toDouble(),
                        high = (k[2] as JsonPrimitive).content.toDouble(),
                        low = (k[3] as JsonPrimitive).content.toDouble(),
                        close = (k[4] as JsonPrimitive).content.toDouble(),
                    )
                }
        }
    }

    /** 精选主流交易对（PRD §8 列表 + §7 总览覆盖） */
    companion object {
        val DEFAULT_SYMBOLS = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")

        val CORE_SYMBOLS = listOf(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
            "DOGEUSDT", "ADAUSDT", "AVAXUSDT", "LINKUSDT", "TONUSDT",
            "TRXUSDT", "DOTUSDT", "MATICUSDT", "LTCUSDT", "BCHUSDT",
            "NEARUSDT", "APTUSDT", "ARBUSDT", "OPUSDT", "SUIUSDT",
            "INJUSDT", "FILUSDT", "ATOMUSDT", "ETCUSDT", "HBARUSDT",
            "UNIUSDT", "AAVEUSDT", "RUNEUSDT", "SEIUSDT", "TIAUSDT",
        )
    }
}
