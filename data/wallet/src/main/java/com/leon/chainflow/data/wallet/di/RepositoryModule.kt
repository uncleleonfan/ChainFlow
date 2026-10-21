package com.leon.chainflow.data.wallet.di

import com.leon.chainflow.core.common.coroutines.DefaultDispatcherProvider
import com.leon.chainflow.core.common.coroutines.DispatcherProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层 Hilt 模块：接口 → 实现的绑定
 *
 * 为什么需要显式 @Binds：
 * DefaultDispatcherProvider 虽有 @Inject 构造，但注入点要求的是
 * DispatcherProvider 接口类型 —— Hilt 无法自动完成「实现 → 接口」的向上绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /** 调度器提供者：生产实现绑定（测试中用 @TestInstallIn 替换为测试调度器） */
    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider
}
