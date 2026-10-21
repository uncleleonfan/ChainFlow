package com.leon.chainflow.feature.token

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leon.chainflow.core.ui.components.ErrorView
import com.leon.chainflow.core.ui.components.LoadingView
import com.leon.chainflow.core.ui.components.PriceChangeText
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Token 详情页（PRD §9 核心页面）
 *
 * 布局：名称 / 实时价 / [1H][4H][1D][1W] / Canvas K 线 / 统计 / [Swap]
 *
 * @param symbol Binance 交易对（如 ETHUSDT，路由参数）
 * @param onNavigateToSwap 跳转 Swap 页，回传基础币符号（如 ETH），Swap 页据此预选支付 Token
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenDetailScreen(
    symbol: String,
    onBack: () -> Unit,
    onNavigateToSwap: (baseAsset: String) -> Unit,
    viewModel: TokenDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 进入页面加载（路由参数经 Intent 注入）
    LaunchedEffect(symbol) {
        viewModel.dispatch(TokenDetailUiIntent.Load(symbol))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.baseAsset.ifEmpty { "Token" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && state.basePrice == null -> LoadingView(Modifier.padding(padding))
            state.error != null && state.basePrice == null -> ErrorView(
                state.error!!,
                onRetry = { viewModel.dispatch(TokenDetailUiIntent.Load(symbol)) },
                Modifier.padding(padding),
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ---- 名称与实时价（WS 优先） ----
                Text("${state.symbol} · 实时", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.displayPrice?.let { price ->
                        Text(
                            text = formatDetailPrice(price),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    PriceChangeText(state.changePercent)
                }

                // ---- 周期切换（PRD §9 [1H][4H][1D][1W]） ----
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = state.chartPeriod == period,
                            onClick = { viewModel.dispatch(TokenDetailUiIntent.PeriodSelected(period)) },
                            label = { Text(period.label) },
                        )
                    }
                }

                // ---- K 线图（Canvas 自绘） ----
                if (state.isChartLoading) {
                    Text("加载图表…", style = MaterialTheme.typography.bodyMedium)
                } else {
                    CandlestickChart(candles = state.candles)
                }

                // ---- 统计行（PRD §9 Volume / 24h High-Low） ----
                StatRow("24h 成交额", state.quoteVolume24h?.let { formatUsd(it) })
                StatRow("24h 最高", state.high24h?.let { formatDetailPrice(it) })
                StatRow("24h 最低", state.low24h?.let { formatDetailPrice(it) })

                Spacer(Modifier.height(8.dp))

                // ---- Swap 入口（PRD §9 [Swap]） ----
                // 回传当前币种符号：详情状态未就绪时退回从交易对派生（ETHUSDT → ETH）
                Button(
                    onClick = {
                        onNavigateToSwap(state.baseAsset.ifBlank { symbol.removeSuffix("USDT") })
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Swap")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 统计行 */
@Composable
private fun StatRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value ?: "—", style = MaterialTheme.typography.labelLarge)
    }
}

/** 详情页价格格式化（千分位 + 小数位自适应） */
private fun formatDetailPrice(price: Double): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    val format = if (price >= 1.0) DecimalFormat("#,##0.00", symbols)
    else DecimalFormat("0.00000000", symbols) // 小币种保留 8 位
    return "$" + format.format(price)
}

/** 大数额美元格式化（$1.23B / $45.6M） */
private fun formatUsd(value: Double): String {
    val df = DecimalFormat("#.##", DecimalFormatSymbols(Locale.US))
    return when {
        value >= 1e12 -> "$${df.format(value / 1e12)}T"
        value >= 1e9 -> "$${df.format(value / 1e9)}B"
        value >= 1e6 -> "$${df.format(value / 1e6)}M"
        value >= 1e3 -> "$${df.format(value / 1e3)}K"
        else -> "$${df.format(value)}"
    }
}
