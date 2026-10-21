package com.leon.chainflow.feature.token

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.leon.chainflow.core.ui.theme.LocalChainFlowColors
import com.leon.chainflow.data.market.Candle
import kotlin.math.max
import kotlin.math.min

/**
 * K 线图（Compose Canvas 自绘，PRD §9 Candlestick Chart）
 *
 * 绘制策略（每帧）：
 * 1. 全量 K 线 minLow/maxHigh → 归一化到画布（留 8% 上下边距）
 * 2. 每根等宽槽位：蜡烛体（open-close 矩形）+ 影线（high-low 竖线）
 * 3. 收盘 ≥ 开盘 = 阳线绿，反之为阴线红（Crypto 惯例）
 * 4. 最新收盘价虚线参考
 *
 * 性能：纯 Canvas 单帧绘制，200 根 K 线 <1ms，无三方图表库依赖。
 */
@Composable
fun CandlestickChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalChainFlowColors.current

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        if (candles.isEmpty()) return@Canvas

        // ---- 值域归一化 ----
        val minLow = candles.minOf { it.low }
        val maxHigh = candles.maxOf { it.high }
        val range = (maxHigh - minLow).takeIf { it > 0 } ?: 1.0
        val padding = size.height * 0.08f
        val usableHeight = size.height - padding * 2

        /** 价格 → y 坐标（y 轴向下，价格越高 y 越小） */
        fun yOf(price: Double): Float =
            (padding + (1f - ((price - minLow) / range).toFloat()) * usableHeight)

        // ---- 槽位：蜡烛体占 70% ----
        val slot = size.width / candles.size
        val bodyWidth = slot * 0.7f

        candles.forEachIndexed { index, candle ->
            val centerX = slot * index + slot / 2
            val isUp = candle.close >= candle.open
            val color = if (isUp) semantic.priceUp else semantic.priceDown

            // 影线
            drawLine(
                color = color,
                start = Offset(centerX, yOf(candle.high)),
                end = Offset(centerX, yOf(candle.low)),
                strokeWidth = 1.5f,
            )

            // 蜡烛体（最小 1px 高，保证极小波动可见）
            val top = yOf(max(candle.open, candle.close))
            val bottom = yOf(min(candle.open, candle.close))
            drawRect(
                color = color,
                topLeft = Offset(centerX - bodyWidth / 2, top),
                size = Size(bodyWidth, max(bottom - top, 1f)),
            )
        }

        // ---- 最新价虚线 ----
        val lastY = yOf(candles.last().close)
        drawLine(
            color = semantic.priceUp.copy(alpha = 0.4f),
            start = Offset(0f, lastY),
            end = Offset(size.width, lastY),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
    }
}
