package com.leon.chainflow.feature.swap

import com.leon.chainflow.core.common.mvi.UiIntent

/**
 * Swap 页意图（PRD §17-18）
 */
sealed interface SwapUiIntent : UiIntent {
    /**
     * 指定支付 Token（从 Token 详情页「Swap」按钮进入时注入）
     * 空串 = 底部 Tab 直接进入，保持默认交易对（ETH → USDC）
     */
    data class PayTokenSelected(val symbol: String) : SwapUiIntent

    /** 支付金额输入变化（触发防抖报价） */
    data class PayAmountChanged(val amount: String) : SwapUiIntent

    /** 切换方向（支付 ⇄ 收到） */
    data object DirectionToggled : SwapUiIntent

    /** 滑点变化 */
    data class SlippageChanged(val bps: Int) : SwapUiIntent

    /** 用户点击「Swap」确认（UI 拉起生物识别后回调执行） */
    data object SwapConfirmed : SwapUiIntent
}
