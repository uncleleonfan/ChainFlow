package com.leon.chainflow.feature.token

import com.leon.chainflow.core.common.mvi.UiIntent

/**
 * Token 详情页意图（PRD §9）
 */
sealed interface TokenDetailUiIntent : UiIntent {
    /** 进入页面：加载详情 + K 线 + 启动实时流 */
    data class Load(val symbol: String) : TokenDetailUiIntent

    /** 切换 K 线周期 */
    data class PeriodSelected(val period: ChartPeriod) : TokenDetailUiIntent

    /** 点击 Swap（Phase 4 接入） */
    data object SwapClicked : TokenDetailUiIntent
}
