package com.leon.chainflow.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.leon.chainflow.core.ui.theme.LocalChainFlowColors
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 价格文本组件
 *
 * 统一价格展示格式（等宽字体 + 货币符号），
 * 避免各页面各自格式化导致风格不一致。
 *
 * @param price 价格数值（Double），例如 4620.32
 * @param modifier Compose 修饰符
 */
@Composable
fun PriceText(
    price: Double,
    modifier: Modifier = Modifier,
) {
    Text(
        text = formatPrice(price),
        style = MaterialTheme.typography.labelLarge, // 等宽价格样式
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * 涨跌幅文本组件
 *
 * Crypto 行业惯例：涨绿跌红（0 或 null 显示为中性色 "0.00%"）。
 * 自动带上 + / - 前缀，直观区分方向。
 *
 * @param changePercent 24 小时涨跌幅（百分比数值，如 2.3 表示 +2.3%）
 */
@Composable
fun PriceChangeText(
    changePercent: Double?,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalChainFlowColors.current
    val (text, color) = when {
        changePercent == null -> "0.00%" to semantic.textTertiary // 无数据
        changePercent > 0 -> "+${DecimalFormat("#.##", DecimalFormatSymbols(Locale.US)).format(changePercent)}%" to semantic.priceUp
        changePercent < 0 -> "${DecimalFormat("#.##", DecimalFormatSymbols(Locale.US)).format(changePercent)}%" to semantic.priceDown
        else -> "0.00%" to semantic.textTertiary
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium, // 等宽涨跌样式
        color = color,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

/**
 * BUY / SELL 标签（交易列表使用，Phase 3 Token 详情页也会复用）
 *
 * @param isBuy true = BUY（绿），false = SELL（红）
 */
@Composable
fun TradeSideTag(
    isBuy: Boolean,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalChainFlowColors.current
    val (label, color) = if (isBuy) "BUY" to semantic.buy else "SELL" to semantic.sell
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp)) // 半透明底色标签
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** 格式化价格：千分位 + 最多 2 位小数（$1,234.56） */
private fun formatPrice(price: Double): String {
    val format = when {
        price >= 1.0 -> DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
        price > 0.0 -> DecimalFormat("0.00000000", DecimalFormatSymbols(Locale.US)) // 小币种保留 8 位小数
        else -> DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))
    }
    return "$${format.format(price)}"
}
