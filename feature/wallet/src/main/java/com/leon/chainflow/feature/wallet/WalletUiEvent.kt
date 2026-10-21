package com.leon.chainflow.feature.wallet

import com.leon.chainflow.core.common.mvi.UiEvent

/**
 * 钱包页一次性事件（副作用）
 */
sealed interface WalletUiEvent : UiEvent {
    /** 地址已复制到剪贴板（Snackbar 提示来源） */
    data object AddressCopied : WalletUiEvent

    /** 需要生物识别认证（UI 层拉起 BiometricPrompt，结果经 [WalletUiIntent.SendConfirmed] 回流） */
    data object RequireBiometric : WalletUiEvent

    /** 交易已广播（展示 txHash 供跳转浏览器） */
    data class TransactionBroadcast(val txHash: String) : WalletUiEvent

    /** 交易已确认（Snackbar 提示） */
    data object TransactionConfirmed : WalletUiEvent

    /** 显示错误提示 */
    data class ShowError(val message: String) : WalletUiEvent

    /** 钱包已删除（提示用户：本地数据已清除） */
    data object WalletDeleted : WalletUiEvent
}
