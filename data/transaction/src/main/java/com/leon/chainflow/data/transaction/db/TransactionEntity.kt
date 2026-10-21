package com.leon.chainflow.data.transaction.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 交易历史实体（PRD §22 Activity 页数据源）
 *
 * 记录本地发起的每笔交易（Send / Swap），
 * 状态由 TransactionMonitor 轮询 RPC 后更新。
 *
 * @param type 交易类型：SEND（转账）/ SWAP（兑换）
 * @param status 上链状态：PENDING / CONFIRMED / FAILED（PRD §20）
 */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val txHash: String,
    val type: String,            // SEND / SWAP
    val chainId: Long,           // 发起链
    val status: String,          // PENDING / CONFIRMED / FAILED
    /** 发送的 Token 符号与数量（人类可读，展示用） */
    val fromSymbol: String,
    val fromAmount: String,
    /** 收到的 Token 符号与数量（Swap 有值，Send 为空） */
    val toSymbol: String? = null,
    val toAmount: String? = null,
    /** 交易对手（Send 收款地址 / Swap 路由合约） */
    val counterparty: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
