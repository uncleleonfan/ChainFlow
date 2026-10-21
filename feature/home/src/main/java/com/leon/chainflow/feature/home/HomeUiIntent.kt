package com.leon.chainflow.feature.home

import com.leon.chainflow.core.common.mvi.UiIntent

/**
 * 首页意图（PRD §7）
 */
sealed interface HomeUiIntent : UiIntent {
    /** 进入页面：启动实时流 + 加载 Top Movers */
    data object Load : HomeUiIntent

    /** 点击右上角设置入口 */
    data object SettingsClicked : HomeUiIntent

    /** 点击币种（总览 / Movers）→ 跳转详情 */
    data class TokenClicked(val symbol: String) : HomeUiIntent
}
