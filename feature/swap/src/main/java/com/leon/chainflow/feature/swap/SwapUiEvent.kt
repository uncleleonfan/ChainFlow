package com.leon.chainflow.feature.swap

import com.leon.chainflow.core.common.mvi.UiEvent

/**
 * Swap 页一次性事件
 */
sealed interface SwapUiEvent : UiEvent {
    /** 需要生物识别认证（通过后调 executeSwapAfterBiometric） */
    data object RequireBiometric : SwapUiEvent

    /** 交易已广播 */
    data class TransactionBroadcast(val txHash: String) : SwapUiEvent

    /** 显示提示 */
    data class ShowMessage(val message: String) : SwapUiEvent
}
