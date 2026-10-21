package com.leon.chainflow.feature.settings

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.blockchain.core.model.NetworkEnvironment
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.core.common.mvi.UiEvent
import com.leon.chainflow.core.common.mvi.UiIntent
import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.data.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 设置页状态（PRD §32） */
data class SettingsUiState(
    val currentChain: Chain = Chain.BASE,
    val isMainnet: Boolean = false, // 默认 Testnet（PRD §32）
    /** Mainnet 风险确认弹窗（切换前强制展示，PRD §32 原文要求） */
    val showMainnetWarning: Boolean = false,
) : UiState

/** 设置页意图 */
sealed interface SettingsUiIntent : UiIntent {
    data object Load : SettingsUiIntent
    data class ChainSelected(val chain: Chain) : SettingsUiIntent
    /** 点击 Testnet（直接切换） */
    data object TestnetSelected : SettingsUiIntent
    /** 点击 Mainnet（先弹风险确认） */
    data object MainnetSelected : SettingsUiIntent
    /** 风险弹窗：确认切换 Mainnet */
    data object MainnetConfirmed : SettingsUiIntent
    /** 风险弹窗：取消 */
    data object MainnetCancelled : SettingsUiIntent
}

/** 设置页事件 */
sealed interface SettingsUiEvent : UiEvent {
    /** 网络已切换（提示） */
    data class EnvironmentChanged(val isMainnet: Boolean) : SettingsUiEvent
}

/**
 * 设置页 ViewModel（PRD §32）
 *
 * 切换 Mainnet 必须经过风险确认弹窗（App 是开源教育项目，须明确告知真实资产风险）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
) : MviViewModel<SettingsUiState, SettingsUiIntent, SettingsUiEvent>(
    initialState = SettingsUiState(),
) {

    override suspend fun onIntent(intent: SettingsUiIntent) {
        when (intent) {
            is SettingsUiIntent.Load -> load()
            is SettingsUiIntent.ChainSelected -> {
                walletRepository.selectChain(intent.chain)
                updateState { copy(currentChain = intent.chain) }
            }
            is SettingsUiIntent.TestnetSelected -> {
                walletRepository.setEnvironment(NetworkEnvironment.TESTNET)
                updateState { copy(isMainnet = false) }
                sendEvent(SettingsUiEvent.EnvironmentChanged(false))
            }
            is SettingsUiIntent.MainnetSelected ->
                updateState { copy(showMainnetWarning = true) } // 拉起风险弹窗
            is SettingsUiIntent.MainnetConfirmed -> {
                walletRepository.setEnvironment(NetworkEnvironment.MAINNET)
                updateState { copy(isMainnet = true, showMainnetWarning = false) }
                sendEvent(SettingsUiEvent.EnvironmentChanged(true))
            }
            is SettingsUiIntent.MainnetCancelled ->
                updateState { copy(showMainnetWarning = false) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val (chain, isMainnet) = walletRepository.currentNetwork()
            updateState { copy(currentChain = chain, isMainnet = isMainnet) }
        }
    }
}
