package com.leon.chainflow.feature.token

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.core.common.result.getOrNull
import com.leon.chainflow.core.common.result.onFailure
import com.leon.chainflow.core.common.result.onSuccess
import com.leon.chainflow.data.market.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Token 详情页 ViewModel（PRD §9）
 *
 * 数据组合策略（全部走 Binance vision，单一直连数据链路）：
 * 1. 进入页面：REST 24h 快照（价格/涨跌/成交额/高低价）
 * 2. 同时加载当前周期 K 线（klines）
 * 3. 订阅该 symbol 的 WS ticker，实时覆盖价格
 * 4. onCleared() 断开 WS —— 生命周期感知（PRD §10.2）
 */
@HiltViewModel
class TokenDetailViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
) : MviViewModel<TokenDetailUiState, TokenDetailUiIntent, TokenDetailUiEvent>(
    initialState = TokenDetailUiState(),
) {

    private var tickerJob: Job? = null

    override suspend fun onIntent(intent: TokenDetailUiIntent) {
        when (intent) {
            is TokenDetailUiIntent.Load -> load(intent.symbol)
            is TokenDetailUiIntent.PeriodSelected -> loadChart(intent.period)
            is TokenDetailUiIntent.SwapClicked -> sendEvent(TokenDetailUiEvent.NavigateToSwap)
        }
    }

    /** 首次加载：快照 + K 线 + 实时流 */
    private fun load(symbol: String) {
        viewModelScope.launch {
            updateState {
                copy(
                    isLoading = true,
                    symbol = symbol,
                    baseAsset = symbol.removeSuffix("USDT"),
                    error = null,
                )
            }

            // 1. 24h 单 symbol 快照（详情页专用，含高低价）
            marketRepository.getTickerDetail(symbol)
                .onSuccess { t ->
                    updateState {
                        copy(
                            isLoading = false,
                            basePrice = t?.price,
                            changePercent = t?.changePercent,
                            quoteVolume24h = t?.quoteVolume24h,
                            // 高低价从 K 线日图首尾推导也可以，这里直接用 24h ticker 的 hidden 字段不可得时留空
                        )
                    }
                }
                .onFailure { error -> updateState { copy(isLoading = false, error = error) } }

            // 2. K 线（默认周期）
            loadChart(currentState.chartPeriod)

            // 3. WS 实时价
            startTicker(symbol)
        }
    }

    /** 加载指定周期 K 线 */
    private fun loadChart(period: ChartPeriod) {
        viewModelScope.launch {
            updateState { copy(chartPeriod = period, isChartLoading = true) }
            marketRepository.getKlines(currentState.symbol, period.interval)
                .onSuccess { candles -> updateState { copy(isChartLoading = false, candles = candles) } }
                .onFailure { updateState { copy(isChartLoading = false) } }
        }
    }

    /** 订阅 WS ticker 实时覆盖价格（连接进程级持有，页面退出只取消 collect） */
    private fun startTicker(symbol: String) {
        marketRepository.startRealtime(listOf(symbol))
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            marketRepository.realtimeTickers.collect { tickers ->
                tickers[symbol]?.let { t ->
                    updateState { copy(livePrice = t.price, changePercent = t.changePercent) }
                }
            }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        marketRepository.stopRealtime()
        super.onCleared()
    }
}
