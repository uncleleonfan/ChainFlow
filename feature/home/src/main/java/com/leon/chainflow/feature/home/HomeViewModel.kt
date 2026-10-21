package com.leon.chainflow.feature.home

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.core.common.result.onFailure
import com.leon.chainflow.core.common.result.onSuccess
import com.leon.chainflow.core.network.ws.WsConnectionState
import com.leon.chainflow.data.market.MarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 ViewModel（PRD §7）
 *
 * 数据流（全部 Binance vision 直连）：
 * - 总览卡片：WS ticker（BTC/ETH/SOL）→ StateFlow → Compose 实时跳动
 * - Top Movers：REST 快照按 24h 涨幅排序取前 10（代替 Smart Money 算法）
 * - 连接徽章：WS state 映射
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val marketRepository: MarketRepository,
) : MviViewModel<HomeUiState, HomeUiIntent, HomeUiEvent>(
    initialState = HomeUiState(),
) {

    private var tickerJob: Job? = null

    override suspend fun onIntent(intent: HomeUiIntent) {
        when (intent) {
            is HomeUiIntent.Load -> load()
            is HomeUiIntent.SettingsClicked -> sendEvent(HomeUiEvent.NavigateToSettings)
            is HomeUiIntent.TokenClicked ->
                sendEvent(HomeUiEvent.NavigateToTokenDetail(intent.symbol))
        }
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }

            // ---- 1. Top Movers：快照按 24h 涨幅取前 10（PRD §7 Trending） ----
            marketRepository.getMarketList()
                .onSuccess { list ->
                    updateState {
                        copy(
                            isLoading = false,
                            topMovers = list.sortedByDescending { it.changePercent }.take(10),
                        )
                    }
                }
                .onFailure { error -> updateState { copy(isLoading = false, error = error) } }

            // ---- 2. 总览实时流（订阅全量清单，Movers 行也能实时跳） ----
            marketRepository.startRealtime(MarketRepository.CORE_SYMBOLS)
            tickerJob?.cancel()
            tickerJob = viewModelScope.launch {
                launch {
                    marketRepository.realtimeTickers.collect { tickers ->
                        updateState { copy(tickers = tickers) }
                    }
                }
                launch {
                    marketRepository.wsState.collect { s ->
                        updateState { copy(wsConnected = s == WsConnectionState.CONNECTED) }
                    }
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
