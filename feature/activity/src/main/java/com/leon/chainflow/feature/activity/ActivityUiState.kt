package com.leon.chainflow.feature.activity

import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.data.transaction.db.TransactionEntity

/**
 * 活动页状态（PRD §22）
 */
data class ActivityUiState(
    /** 交易历史（Room Flow，倒序） */
    val transactions: List<TransactionEntity> = emptyList(),
    /** 加载中 */
    val isLoading: Boolean = false,
) : UiState
