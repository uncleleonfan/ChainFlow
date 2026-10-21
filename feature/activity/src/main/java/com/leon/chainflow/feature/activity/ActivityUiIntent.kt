package com.leon.chainflow.feature.activity

import com.leon.chainflow.core.common.mvi.UiIntent

/**
 * 活动页意图（PRD §22）
 */
sealed interface ActivityUiIntent : UiIntent {
    /** 进入页面：订阅交易历史 */
    data object Load : ActivityUiIntent

    /** 点击交易 → 打开 Block Explorer（PRD §22） */
    data class TransactionClicked(val txHash: String, val chainId: Long) : ActivityUiIntent
}
