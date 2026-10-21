package com.leon.chainflow.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.leon.chainflow.core.ui.components.PriceChangeText
import com.leon.chainflow.core.ui.components.PriceText
import com.leon.chainflow.core.ui.theme.LocalChainFlowColors
import com.leon.chainflow.data.market.TokenListItem

/**
 * 首页（Home Tab，PRD §7）
 *
 * 结构：
 * ```
 * ChainFlow          ⚙
 * ● 实时连接（WS 徽章，PRD §10.2）
 * Market Overview（BTC/ETH/SOL 实时价，PRD §7 线框）
 * Top Movers（24h 涨幅榜 —— MVP 用公开数据代替 Smart Money）
 * ```
 *
 * MVI 数据流：
 * LaunchedEffect → dispatch(Load) → State collect 渲染 / events 消费导航
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit, // 由 app 模块注入（Feature 解耦）
    onTokenClick: (symbol: String) -> Unit, // 点击币种 → 详情
    viewModel: HomeViewModel = hiltViewModel(),
) {
    //把 ViewModel 里的 Flow 变成 Compose 能用的 State，同时只在界面可见（STARTED 状态）时才收集，进入后台就自动暂停，省电又省资源。
    val state by viewModel.state.collectAsStateWithLifecycle()
    val semantic = LocalChainFlowColors.current

    // LaunchedEffect 把协程生命周期与Composable绑定：页面离开自动取消
    LaunchedEffect(Unit) { viewModel.dispatch(HomeUiIntent.Load) }

    // 消费一次性事件
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                HomeUiEvent.NavigateToSettings -> onNavigateToSettings()
                is HomeUiEvent.NavigateToTokenDetail -> onTokenClick(event.symbol)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ChainFlow") },
                actions = {
                    IconButton(onClick = { viewModel.dispatch(HomeUiIntent.SettingsClicked) }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ---- WS 连接徽章 ----
            item {
                Text(
                    text = if (state.wsConnected) "● 实时连接" else "○ 未连接（自动重连中）",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.wsConnected) semantic.priceUp else semantic.textTertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ---- Market Overview（PRD §7 三大币实时卡片） ----
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Market Overview", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    OVERVIEW_SYMBOLS.forEach { symbol ->
                        val ticker = state.tickers[symbol]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.dispatch(HomeUiIntent.TokenClicked(symbol)) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                symbol.removeSuffix("USDT"),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            PriceText(price = ticker?.price ?: 0.0) // WS 首帧 1s 内到达
                            Spacer(Modifier.padding(horizontal = 8.dp))
                            PriceChangeText(changePercent = ticker?.changePercent)
                        }
                    }
                }
            }

            // ---- Top Movers（PRD §7 Trending） ----
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text("Top Movers", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.isLoading) "加载中…" else "24h 涨幅榜 · Binance",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.topMovers, key = { it.symbol }) { token ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clickable {
                            viewModel.dispatch(HomeUiIntent.TokenClicked(token.symbol))
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(token.baseAsset, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${formatVol(token.quoteVolume24h)} · 实时 $${token.price}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Movers 行实时价覆盖（WS 推送后跳动）
                        state.tickers[token.symbol]?.let { PriceText(price = it.price) }
                        Spacer(Modifier.padding(horizontal = 8.dp))
                        PriceChangeText(changePercent = token.changePercent)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 总览三大币（PRD §7 线框图）：Binance symbol */
private val OVERVIEW_SYMBOLS = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")

/** 紧凑成交额（$1.2B） */
private fun formatVol(value: Double): String = when {
    value >= 1e9 -> "$${"%.1f".format(value / 1e9)}B"
    value >= 1e6 -> "$${"%.0f".format(value / 1e6)}M"
    else -> "$${"%.0f".format(value / 1e3)}K"
}
