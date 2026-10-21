package com.leon.chainflow.data.market

/**
 * 统一 K 线模型（UI 绘图单元 —— 由 Repository 从各数据源映射而来）
 *
 * @param timestamp 开盘时间（epoch 毫秒）
 */
data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)
