package com.leon.chainflow.core.common.mvi

/**
 * MVI —— UiEvent（一次性事件 / 副作用）
 *
 * 定义：不属于界面状态的“发生一次即消费”的事件，例如：
 * - 弹出 Snackbar / Toast（「交易已广播」）
 * - 页面导航（「跳转到 Token 详情」）
 * - 触发生物识别弹窗
 *
 * 设计要点：
 * - 与 UiState 的本质区别：State 是“界面当前长什么样”（可重复消费），
 *   Event 是“发生了什么”（只能被消费一次，例如旋转屏幕后不应重放 Toast）。
 * - 通过 [kotlinx.coroutines.channels.Channel] 的 Buffer_DROP_OLDEST 语义投递，
 *   确保 ViewModel 忙碌时不会阻塞发送方。
 */
interface UiEvent
