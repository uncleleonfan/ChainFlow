package com.leon.chainflow.core.common.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 基类 ViewModel
 *
 * 统一实现「单向数据流」骨架，各页面 ViewModel 继承后只需：
 * 1. 定义自己的 State / Intent / Event 类型；
 * 2. 在 [onIntent] 中处理意图（纯函数式地更新 State）；
 * 3. 需要副作用时调用 [sendEvent]。
 *
 * 数据流模型：
 * ```
 * UI --Intent--> onIntent() --reduce/update--> StateFlow<UiState> --render--> UI
 *                                └--sendEvent--> Channel<UiEvent> --collect--> UI
 * ```
 *
 * @param S 该页面的界面状态类型（不可变）
 * @param I 该页面接受的用户意图类型
 * @param E 该页面的一次性事件类型
 * @property initialState 初始状态。构造时立刻赋值，避免 StateFlow 出现 null 态
 */
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEvent>(
    initialState: S,
) : ViewModel() {

    /** 内部可变状态容器：仅 ViewModel 内部可写 */
    private val _state = MutableStateFlow(initialState)

    /** 对外暴露的只读状态流：UI 层通过 collect 订阅渲染 */
    val state: StateFlow<S> = _state.asStateFlow()

    /**
     * 一次性事件通道：
     * - Channel(capacity = UNLIMITED)：先到先得，保证事件不丢失；
     * - onBufferOverflow = DROP_OLDEST：缓冲满时丢弃最旧事件（极端场景下的兜底）。
     */
    private val _events = Channel<E>(Channel.UNLIMITED)

    /** 对外暴露的只读事件流：UI 层通常在 LaunchedEffect 中 collect */
    val events: Flow<E> = _events.receiveAsFlow()

    /**
     * 更新状态的唯一入口（原子操作）。
     *
     * 使用 `MutableStateFlow.update {}` 保证「读取-修改-写入」的原子性，
     * 避免并发处理多个 Intent 时出现丢失更新（lost update）问题。
     */
    protected fun updateState(reducer: S.() -> S) {
        _state.update(reducer)
    }

    /** 获取当前状态快照（少数需要“读当前值再决定”的场景使用） */
    protected val currentState: S get() = _state.value

    /**
     * 发送一次性事件（Toast / 导航等副作用）。
     * 使用 trySend：Channel 为 UNLIMITED，发送不会挂起，无需 suspend 上下文。
     */
    protected fun sendEvent(event: E) {
        _events.trySend(event)
    }

    /**
     * Intent 分发入口 —— View 层收集到用户交互后调用此方法。
     * 在 viewModelScope 中启动协程处理，保证页面销毁时自动取消。
     */
    fun dispatch(intent: I) {
        viewModelScope.launch {
            onIntent(intent)
        }
    }

    /** 子类实现：将 Intent 映射为状态变更 / 副作用（MVI 的核心 reduce 逻辑） */
    protected abstract suspend fun onIntent(intent: I)
}
