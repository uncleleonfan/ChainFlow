package com.leon.chainflow.feature.market

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.leon.chainflow.core.ui.components.PriceText
import com.leon.chainflow.data.market.TokenListItem
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * 市场页（Markets Tab，PRD §8）
 *
 * 功能：Token 列表（WS 实时价）+ 搜索 + 排序 + 下拉刷新
 * 性能：LazyColumn 虚拟化 + key（PRD §30 要求 1000+ tokens 流畅滚动）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(
    onTokenClick: (symbol: String) -> Unit,
    viewModel: MarketViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.dispatch(MarketUiIntent.Load) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Markets") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ---- 搜索框（PRD §8 Search） ----
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.dispatch(MarketUiIntent.SearchQueryChanged(it)) },
                placeholder = { Text("搜索 Token") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // ---- 排序条（PRD §8 Sort） ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                MarketSort.entries.forEach { sort ->
                    FilterChip(
                        selected = state.sort == sort,
                        onClick = { viewModel.dispatch(MarketUiIntent.SortSelected(sort)) },
                        label = { Text(sort.label) },
                    )
                }
            }

            // ---- 内容区 ----
            when {
                state.isLoading && state.tokens.isEmpty() -> LoadingView()

                state.error != null && state.tokens.isEmpty() -> ErrorView(
                    state.error!!,
                    onRetry = { viewModel.dispatch(MarketUiIntent.Load) },
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.dispatch(MarketUiIntent.Refresh) }, // PRD §8 Pull to Refresh
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = state.displayList,
                            key = { it.symbol }, // key：WS 更新时只重组变化的行
                        ) { token ->
                            TokenRow(
                                token = token,
                                livePrice = state.livePrices[token.symbol]?.price,
                                liveChange = state.livePrices[token.symbol]?.changePercent,
                                onClick = {
                                    viewModel.dispatch(MarketUiIntent.TokenClicked(token.symbol))
                                },
                            )
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

/** 单行 Token：symbol + 实时价（WS 覆盖）+ 24h 涨跌 + 成交额 */
@Composable
private fun TokenRow(
    token: TokenListItem,
    livePrice: Double?,
    liveChange: Double?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(token.baseAsset, style = MaterialTheme.typography.bodyLarge)
            Text(
                "Vol ${formatCompact(token.quoteVolume24h)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            // WS 实时价优先，REST 快照兜底
            PriceText(price = livePrice ?: token.price)
            Spacer(Modifier.height(2.dp))
            PriceChangeText(changePercent = liveChange ?: token.changePercent)
        }
    }
}

/** 紧凑数字（$1.2B / $345M） */
private fun formatCompact(value: Double): String {
    val df = DecimalFormat("#.#", DecimalFormatSymbols(Locale.US))
    return when {
        value >= 1e9 -> "$${df.format(value / 1e9)}B"
        value >= 1e6 -> "$${df.format(value / 1e6)}M"
        value >= 1e3 -> "$${df.format(value / 1e3)}K"
        else -> "$${df.format(value)}"
    }
}
