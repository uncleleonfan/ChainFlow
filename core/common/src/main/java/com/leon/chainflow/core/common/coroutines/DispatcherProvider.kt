package com.leon.chainflow.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 协程调度器提供者（Dispatcher 注入模式）
 *
 * 为什么不直接使用 [Dispatchers]？
 * 1. **可测试性**：单元测试中通过替换为 `UnconfinedTestDispatcher` /
 *    `StandardTestDispatcher`，可以精确控制协程执行时机（runTest 场景必备）。
 * 2. **可维护性**：所有模块统一从这里取调度器，未来切换（如定制 IO 池）
 *    只需改一处。
 *
 * 典型用法：
 * ```kotlin
 * class WalletRepository @Inject constructor(
 *     private val dispatchers: DispatcherProvider,
 * ) {
 *     suspend fun loadBalance() = withContext(dispatchers.io) { ... }
 * }
 * ```
 */
interface DispatcherProvider {
    /** 主线程调度器：所有 UI 相关操作 */
    val main: CoroutineDispatcher

    /** IO 调度器：网络请求（RPC / REST / WebSocket）、磁盘读写、Room 查询 */
    val io: CoroutineDispatcher

    /** 默认调度器：CPU 密集型任务（加密计算、大列表排序、图表数据换算） */
    val default: CoroutineDispatcher
}

/**
 * 生产环境实现：直接映射到系统 [Dispatchers]。
 * 使用 @Singleton + @Inject 让 Hilt 自动绑定（接口与实现同模块时无需 Module）。
 */
@Singleton
class DefaultDispatcherProvider @Inject constructor() : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Main
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
