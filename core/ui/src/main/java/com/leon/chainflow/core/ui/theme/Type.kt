package com.leon.chainflow.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * ChainFlow 字体体系 —— Trading Terminal 风格
 *
 * 设计原则：
 * - 数字（价格 / 金额）使用 Monospace 等宽字体：滚动更新时宽度稳定不抖动；
 * - 标题用较大字号 + Medium 字重；正文 / 辅助信息对比明显，保证信息密度。
 */
val ChainFlowTypography = Typography(
    // 页面大标题（如「Total Assets」总资产金额）
    displayLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
    ),
    // 二级大数字（Token 详情页当前价格）
    displaySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    ),
    // 区块标题（Market Overview / Trending Tokens）
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    // 卡片标题
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    // 列表主文字（Token Symbol）
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    // 列表次级文字（Token 名称）
    bodyMedium = TextStyle(
        fontSize = 13.sp,
    ),
    // 辅助小字（标签、时间）
    bodySmall = TextStyle(
        fontSize = 11.sp,
    ),
    // 价格数字（列表行、实时报价）—— 复用 labelLarge 槽位，等宽字体保证滚动时不抖动
    labelLarge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    // 涨跌幅百分比 / BUY-SELL 标签
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
)
