package com.leon.chainflow.feature.token

import com.leon.chainflow.core.common.mvi.UiEvent

/**
 * Token 详情页一次性事件
 */
sealed interface TokenDetailUiEvent : UiEvent {
    /** 跳转 Swap 页（Phase 4 接入） */
    data object NavigateToSwap : TokenDetailUiEvent
}
