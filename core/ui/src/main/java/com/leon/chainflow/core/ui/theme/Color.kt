package com.leon.chainflow.core.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ChainFlow 配色方案 —— Dark Trading Terminal 风格（对应 PRD §35）
 *
 * 设计原则：
 * - 高信息密度、深色背景、低饱和度中性色 + 少量品牌色点缀；
 * - 涨跌语义色遵循 Crypto 行业惯例：**涨绿跌红**（区别于股票市场）；
 * - 所有颜色集中定义，禁止在业务 Composable 中硬编码 Color(0x...)。
 */
object ChainFlowColors {

    // ---- 背景层级（由深到浅，构建空间层次感） ----
    /** 最底层背景：接近纯黑，作为 App 主背景 */
    val Background = Color(0xFF0A0E14)

    /** 卡片 / Surface 背景：比背景略亮，用于列表项、卡片 */
    val Surface = Color(0xFF111722)

    /** 二级 Surface：悬浮卡片、输入框背景 */
    val SurfaceVariant = Color(0xFF1A2230)

    // ---- 品牌色 ----
    /** 品牌主色：科技感蓝绿（用于按钮、选中态、图表强调） */
    val Primary = Color(0xFF2DD4BF)

    /** 主色上的内容色：深色（保证对比度） */
    val OnPrimary = Color(0xFF042F2E)

    /** 次级色：用于进度、次要强调 */
    val Secondary = Color(0xFF60A5FA)

    // ---- 文字层级 ----
    /** 主文字：近白，价格 / 金额等核心信息 */
    val TextPrimary = Color(0xFFF1F5F9)

    /** 次级文字：Token 名称、说明文字 */
    val TextSecondary = Color(0xFF94A3B8)

    /** 弱化文字：时间戳、占位符 */
    val TextTertiary = Color(0xFF64748B)

    // ---- 语义色 ----
    /** 上涨（Crypto 惯例：绿色代表涨） */
    val PriceUp = Color(0xFF34D399)

    /** 下跌（Crypto 惯例：红色代表跌） */
    val PriceDown = Color(0xFFF87171)

    /** 交易 BUY 标签 */
    val BuyGreen = Color(0xFF10B981)

    /** 交易 SELL 标签 */
    val SellRed = Color(0xFFEF4444)

    /** 警告（Mainnet 切换风险提示等） */
    val Warning = Color(0xFFFBBF24)

    /** 错误 / 失败状态 */
    val Error = Color(0xFFEF4444)

    /** 分割线 / 描边 */
    val Outline = Color(0xFF1E293B)
}
