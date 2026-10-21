package com.leon.chainflow.data.transaction.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import android.content.Context
import javax.inject.Singleton

/**
 * ChainFlow Room 数据库（PRD §25 Local Database）
 *
 * Phase 4 仅含交易历史表；后续 Phase 可扩展 Token 元数据 / 缓存价格表。
 */
@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = false, // MVP 阶段不做 schema 导出（Phase 6 工程化时补充）
)
abstract class ChainFlowDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
}

/** 数据库 Hilt 模块 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ChainFlowDatabase =
        androidx.room.Room.databaseBuilder(
            context,
            ChainFlowDatabase::class.java,
            "chainflow.db",
        ).build()

    @Provides
    @Singleton
    fun provideTransactionDao(db: ChainFlowDatabase): TransactionDao = db.transactionDao()
}
