package com.leon.chainflow.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 暗色主题 ColorScheme（Material3）
 *
 * PRD §35 明确要求 Dark Trading Terminal 风格 —— 因此 App 强制使用暗色方案，
 * 不跟随系统亮色（isSystemInDarkTheme 参数仅为将来支持亮色主题预留）。
 */
private val DarkColorScheme = darkColorScheme(
    primary = ChainFlowColors.Primary,
    onPrimary = ChainFlowColors.OnPrimary,
    secondary = ChainFlowColors.Secondary,
    background = ChainFlowColors.Background,
    onBackground = ChainFlowColors.TextPrimary,
    surface = ChainFlowColors.Surface,
    onSurface = ChainFlowColors.TextPrimary,
    surfaceVariant = ChainFlowColors.SurfaceVariant,
    onSurfaceVariant = ChainFlowColors.TextSecondary,
    error = ChainFlowColors.Error,
    outline = ChainFlowColors.Outline,
)

/**
 * 语义色本地化 CompositionLocal
 *
 * MaterialTheme 不包含业务语义色（涨跌色等），
 * 通过 CompositionLocal 注入，业务组件中这样使用：
 * ```kotlin
 * val colors = LocalChainFlowColors.current
 * Text("+2.3%", color = colors.priceUp)
 * ```
 */
data class ChainFlowSemanticColors(
    val priceUp: Color = ChainFlowColors.PriceUp,
    val priceDown: Color = ChainFlowColors.PriceDown,
    val buy: Color = ChainFlowColors.BuyGreen,
    val sell: Color = ChainFlowColors.SellRed,
    val warning: Color = ChainFlowColors.Warning,
    val textTertiary: Color = ChainFlowColors.TextTertiary,
)

val LocalChainFlowColors = staticCompositionLocalOf { ChainFlowSemanticColors() }

/**
 * ChainFlow 全局主题入口
 *
 * 所有页面（包括各 feature 模块）统一通过 ChainFlowTheme 包裹，
 * 保证视觉一致性。当前固定暗色。
 */
@Composable
fun ChainFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // 预留：未来可支持跟随系统
    content: @Composable () -> Unit,
) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalChainFlowColors provides ChainFlowSemanticColors(),
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme, // 强制暗色（PRD §35）
            typography = ChainFlowTypography,
            content = content,
        )
    }
}
