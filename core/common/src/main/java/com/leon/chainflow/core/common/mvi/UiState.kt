package com.leon.chainflow.core.common.mvi

/**
 * MVI —— UiState（视图状态）
 *
 * 定义：不可变的（immutable）界面状态对象。
 *
 * 设计要点：
 * - 所有字段均为 `val`，保证状态不可变；任何变更都会生成新的 State 实例，
 *   由 Compose 通过 `equals` 比较自动触发重组（recomposition）。
 * - 每个页面定义自己的 State 类并实现本接口，例如 `HomeUiState`。
 * - 与 UiIntent / UiEvent 共同构成 MVI 三要素（单向数据流）：
 *
 * ```
 * UiIntent（用户意图）→ ViewModel 处理 → UiState（渲染界面）
 *                                      ↘ UiEvent（一次性副作用：Toast/导航）
 * ```
 */
interface UiState
