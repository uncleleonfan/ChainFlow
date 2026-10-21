package com.leon.chainflow.data.transaction

import com.leon.chainflow.data.transaction.di.TransactionModule.ApplicationScope
import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.blockchain.core.model.NetworkEnvironment
import com.leon.chainflow.blockchain.core.model.TransactionStatus
import com.leon.chainflow.blockchain.evm.tx.EvmTransactionService
import com.leon.chainflow.core.common.coroutines.DispatcherProvider
import com.leon.chainflow.core.common.result.getOrNull
import com.leon.chainflow.data.transaction.db.TransactionDao
import com.leon.chainflow.data.transaction.db.TransactionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 交易历史仓库（PRD §22 Activity）
 *
 * 职责：
 * 1. 广播成功后记录交易（insert）
 * 2. 后台轮询回执并更新状态（RPC 为主数据源）
 * 3. 暴露 Flow 供 Activity 页订阅
 *
 * 状态流转（PRD §20）：PENDING → CONFIRMED / FAILED
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val transactionService: EvmTransactionService,
    @param:ApplicationScope private val scope: kotlinx.coroutines.CoroutineScope, // 限定符作用于构造参数（Hilt 注入点）
    private val dispatchers: DispatcherProvider,
) {

    /** Activity 页数据源：按时间倒序的全部交易 */
    fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()

    /**
     * 记录一笔已广播的交易并启动状态监控
     *
     * @param txHash 广播返回的交易哈希
     * @param type SEND / SWAP
     * @param fromSymbol/fromAmount 支付侧（人类可读）
     * @param toSymbol/toAmount 收到侧（Swap 有值）
     */
    fun recordAndMonitor(
        txHash: String,
        type: String,
        chain: Chain,
        environment: NetworkEnvironment,
        fromSymbol: String,
        fromAmount: String,
        toSymbol: String? = null,
        toAmount: String? = null,
        counterparty: String? = null,
    ) {
        // 1. 立即落库（PENDING）—— Activity 页即时可见
        // runBlocking：确保调用方拿到「已入库」语义后再继续（Broadcast 事件发出时 Activity 一定查得到）
        kotlinx.coroutines.runBlocking {
            dao.insert(
                TransactionEntity(
                    txHash = txHash,
                    type = type,
                    chainId = chain.chainId,
                    status = TransactionStatus.PENDING.name,
                    fromSymbol = fromSymbol,
                    fromAmount = fromAmount,
                    toSymbol = toSymbol,
                    toAmount = toAmount,
                    counterparty = counterparty,
                ),
            )
        }

        // 2. 后台轮询回执 → 更新状态（应用级 scope，不随页面销毁）
        scope.launch(dispatchers.io) {
            val final = transactionService.monitorTransaction(txHash, chain, environment).getOrNull()
            val status = when (final) {
                TransactionStatus.CONFIRMED -> "CONFIRMED"
                TransactionStatus.FAILED -> "FAILED"
                else -> "PENDING" // 超时保持 PENDING，用户可去浏览器查看
            }
            dao.updateStatus(txHash, status)
        }
    }
}
