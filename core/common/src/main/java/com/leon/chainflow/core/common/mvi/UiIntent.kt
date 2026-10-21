package com.leon.chainflow.core.common.mvi

/**
 * MVI —— UiIntent（用户意图）
 *
 * 定义：用户在界面上的所有交互都会被抽象成一个 Intent（意图），
 * 例如「点击刷新」「输入金额」「切换周期」。
 *
 * 设计要点：
 * - View 层唯一职责就是收集用户操作并发送 Intent，**不包含任何业务逻辑**。
 * - ViewModel 是唯一处理器：接收 Intent → 执行业务 → 更新 State / 发送 Event。
 * - Intent 应命名为动词短语（RefreshClicked / AmountChanged / TabSelected）。
 */
interface UiIntent
