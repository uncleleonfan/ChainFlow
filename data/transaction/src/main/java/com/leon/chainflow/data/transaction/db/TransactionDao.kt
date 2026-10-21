package com.leon.chainflow.data.transaction.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 交易历史 DAO（Room）
 *
 * Activity 页通过 [observeAll] 订阅 —— 交易确认后自动刷新列表（Flow 响应式）。
 */
@Dao
interface TransactionDao {

    /** 插入交易（广播成功时写入，txHash 冲突时忽略幂等重试） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tx: TransactionEntity)

    /** 更新状态（Monitor 轮询到确认/失败后调用） */
    @Update
    suspend fun update(tx: TransactionEntity)

    /** 按时间倒序观察全部交易（Activity 页数据源） */
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    /** 单笔查询（状态更新前读取） */
    @Query("SELECT * FROM transactions WHERE txHash = :txHash")
    suspend fun findByHash(txHash: String): TransactionEntity?

    /** 更新状态便捷方法（避免整实体回写） */
    @Query("UPDATE transactions SET status = :status, updatedAt = :updatedAt WHERE txHash = :txHash")
    suspend fun updateStatus(txHash: String, status: String, updatedAt: Long = System.currentTimeMillis())
}
