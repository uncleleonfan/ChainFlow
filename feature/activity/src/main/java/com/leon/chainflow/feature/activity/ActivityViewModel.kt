package com.leon.chainflow.feature.activity

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.data.transaction.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 活动页 ViewModel（PRD §22 交易历史）
 *
 * Room Flow 订阅：交易状态轮询更新后列表自动刷新（响应式）。
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
) : MviViewModel<ActivityUiState, ActivityUiIntent, ActivityUiEvent>(
    initialState = ActivityUiState(),
) {

    private var observeJob: Job? = null

    override suspend fun onIntent(intent: ActivityUiIntent) {
        when (intent) {
            is ActivityUiIntent.Load -> observe()
            is ActivityUiIntent.TransactionClicked -> {
                // chainId → Explorer URL 模板（PRD §22 Block Explorer 跳转）
                val chain = Chain.entries.find { it.chainId == intent.chainId } ?: Chain.BASE
                val url = chain.explorerTx.format(intent.txHash)
                sendEvent(ActivityUiEvent.OpenInExplorer(url))
            }
        }
    }

    /** 订阅交易历史（Room Flow：状态更新自动推送） */
    private fun observe() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            transactionRepository.observeAll().collect { list ->
                updateState { copy(transactions = list, isLoading = false) }
            }
        }
    }
}
