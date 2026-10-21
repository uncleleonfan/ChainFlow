package com.leon.chainflow.core.common.result

/**
 * ChainFlow 统一结果类型
 *
 * 代替「直接抛异常」的调用方式，让错误处理成为函数签名的一部分，
 * 强迫调用方显式处理失败分支（Kotlin 惯用的密封类型 Result 模式）。
 *
 * 典型用法：
 * ```kotlin
 * suspend fun fetchTokenList(): ChainFlowResult<List<Token>> = runCatching {
 *     api.getTokens()
 * }.toChainFlowResult()  // 异常自动转换为 AppError
 * ```
 */
sealed interface ChainFlowResult<out T> {

    /** 成功：携带数据 */
    data class Success<T>(val data: T) : ChainFlowResult<T>

    /** 失败：携带统一的 [AppError]（不使用原始 Exception，避免泄漏技术细节到 UI） */
    data class Failure(val error: AppError) : ChainFlowResult<Nothing>

    companion object {
        /** 便捷构造：成功 */
        fun <T> success(data: T): ChainFlowResult<T> = Success(data)

        /** 便捷构造：失败 */
        fun failure(error: AppError): ChainFlowResult<Nothing> = Failure(error)
    }
}

/** 挂起执行 + 自动异常转换：任何 throw 都会被翻译为 [AppError] */
suspend fun <T> chainFlowRunCatching(block: suspend () -> T): ChainFlowResult<T> = try {
    ChainFlowResult.Success(block())
} catch (cancellation: kotlinx.coroutines.CancellationException) {
    // 协程取消必须原样抛出，否则会破坏结构化并发（例如页面退出时的取消传播）
    throw cancellation
} catch (t: Throwable) {
    ChainFlowResult.Failure(t.toAppError())
}

/** 非 suspend 版本：用于同步代码块（加密计算 / 本地解析） */
fun <T> chainFlowRunCatchingSync(block: () -> T): ChainFlowResult<T> = try {
    ChainFlowResult.Success(block())
} catch (t: Throwable) {
    ChainFlowResult.Failure(t.toAppError())
}

/**
 * 把任意 Throwable 映射为 [AppError]。
 * 后续 Phase 会在 data 层针对 IOException / SocketTimeoutException /
 * web3j 异常做更精细的映射，此处先提供合理默认值。
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this // 已经是 AppError 则直接透传，避免重复包装
    is java.io.IOException -> AppError.Network()
    else -> AppError.Unknown(message ?: this::class.simpleName ?: "unknown")
}

/** map 成功分支的数据；失败分支原样传递 */
inline fun <T, R> ChainFlowResult<T>.map(transform: (T) -> R): ChainFlowResult<R> = when (this) {
    is ChainFlowResult.Success -> ChainFlowResult.Success(transform(data))
    is ChainFlowResult.Failure -> this
}

/** 成功时执行副作用；失败时执行另一分支 */
inline fun <T> ChainFlowResult<T>.onSuccess(block: (T) -> Unit): ChainFlowResult<T> {
    if (this is ChainFlowResult.Success) block(data)
    return this
}

inline fun <T> ChainFlowResult<T>.onFailure(block: (AppError) -> Unit): ChainFlowResult<T> {
    if (this is ChainFlowResult.Failure) block(error)
    return this
}

/** 取出数据，失败时返回 null（适合「有则展示、无则忽略」的场景） */
fun <T> ChainFlowResult<T>.getOrNull(): T? = (this as? ChainFlowResult.Success)?.data
