package com.leon.chainflow.feature.market

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.core.common.result.onFailure
import com.leon.chainflow.core.common.result.onSuccess
import com.leon.chainflow.data.market.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 市场页 ViewModel（PRD §8：列表 / 搜索 / 排序 / 下拉刷新）
 *
 * 实时价策略：列表加载后对全部 symbol 订阅 Binance ticker（一条组合流连接），
 * 渲染时 WS 价覆盖 REST 快照价 —— 价格实时跳动（PRD §10 数据流闭环）。
 */
@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
) : MviViewModel<MarketUiState, MarketUiIntent, MarketUiEvent>(
    initialState = MarketUiState(),
) {

    /** WS 订阅任务（onCleared 取消） */
    private var tickerJob: Job? = null

    override suspend fun onIntent(intent: MarketUiIntent) {
        when (intent) {
            is MarketUiIntent.Load -> load()
            is MarketUiIntent.SearchQueryChanged -> updateState { copy(searchQuery = intent.query) }
            is MarketUiIntent.SortSelected -> updateState { copy(sort = intent.sort) }
            is MarketUiIntent.Refresh -> refresh()
            is MarketUiIntent.TokenClicked ->
                sendEvent(MarketUiEvent.NavigateToTokenDetail(intent.symbol))
        }
    }

    /** 首次加载：快照 + 实时流 */
    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            marketRepository.getMarketList()
                .onSuccess { tokens ->
                    updateState { copy(isLoading = false, tokens = tokens) }
                    // 全部 symbol 订阅实时价（组合流单连接，30 个 symbol 无压力）
                    startTickerFlow(tokens.map { it.symbol })
                }
                .onFailure { error -> updateState { copy(isLoading = false, error = error) } }
        }
    }

    /** 下拉刷新：重拉快照 */
    private fun refresh() {
        viewModelScope.launch {
            updateState { copy(isRefreshing = true, error = null) }
            marketRepository.getMarketList()
                .onSuccess { tokens -> updateState { copy(isRefreshing = false, tokens = tokens) } }
                .onFailure { error -> updateState { copy(isRefreshing = false, error = error) } }
        }
    }

    /** 订阅实时报价：WS 流 collect 进 UiState（连接由进程级单例持有，切 Tab 不断流） */
    private fun startTickerFlow(symbols: List<String>) {
        marketRepository.startRealtime(symbols)
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            marketRepository.realtimeTickers.collect { tickers ->
                updateState { copy(livePrices = tickers) }
            }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        marketRepository.stopRealtime()
        super.onCleared()
    }
}
