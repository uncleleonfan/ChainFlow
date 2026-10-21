package com.leon.chainflow.data.transaction.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * 交易数据层 Hilt 模块
 *
 * 提供【应用级 CoroutineScope】供交易监控使用：
 * 轮询回执必须超越页面生命周期（页面退出交易仍在链上打包）。
 */
@Module
@InstallIn(SingletonComponent::class)
object TransactionModule {

    /** 应用级作用域限定符（区别于 ViewModel scope） */
    @Qualifier
    @Retention(AnnotationRetention.BINARY)
    annotation class ApplicationScope

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
