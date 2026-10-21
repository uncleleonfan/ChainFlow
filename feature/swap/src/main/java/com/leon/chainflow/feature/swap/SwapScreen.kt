package com.leon.chainflow.feature.swap

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
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leon.chainflow.core.security.BiometricAuthenticator

/**
 * Swap 页（Swap Tab，PRD §17 UI 设计）
 *
 * 布局：
 * ```
 * Swap
 * ┌─ You Pay ─────────────┐
 * │ [0.1        ]  ETH ↕  │
 * └───────────────────────┘
 *          ↓ 方向切换
 * ┌─ You Receive ────────┐
 * │ ~462 USDC            │
 * └───────────────────────┘
 * Rate / Slippage / Gas
 * [Swap 按钮]
 * ```
 *
 * 执行流程（PRD §18）：报价 → 确认弹窗 → 生物识别 → 签名广播
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwapScreen(
    /** 从 Token 详情页进入时指定的支付 Token 符号（空 = 底部 Tab 直接进入，默认 ETH） */
    payToken: String = "",
    viewModel: SwapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // 生物识别器（组合级缓存，认证时使用）
    val authenticator = remember { BiometricAuthenticator() }

    // 入口 Token 注入：参数变化（详情页多次进入不同币种）时重新对齐交易对
    LaunchedEffect(payToken) { viewModel.dispatch(SwapUiIntent.PayTokenSelected(payToken)) }

    // 事件消费：生物识别 / 广播结果 / 提示
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SwapUiEvent.RequireBiometric -> {
                    val activity = context as? FragmentActivity
                    if (activity != null && authenticator.canAuthenticate(activity)) {
                        // suspend authenticate：结果以返回值回流
                        val ok = authenticator.authenticate(
                            activity, "确认 Swap", "验证身份以签名交易",
                        )
                        if (ok) viewModel.executeSwapAfterBiometric()
                        else snackbarHostState.showSnackbar("交易已取消")
                    } else {
                        viewModel.executeSwapAfterBiometric() // 无生物识别设备时直接执行
                    }
                }
                is SwapUiEvent.TransactionBroadcast ->
                    snackbarHostState.showSnackbar("✅ 已广播：${event.txHash.take(18)}…")
                is SwapUiEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Swap") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- You Pay（PRD §17） ----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("You Pay", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.payAmount,
                            onValueChange = { viewModel.dispatch(SwapUiIntent.PayAmountChanged(it)) },
                            placeholder = { Text("0.0") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        Text(state.paySymbol, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            // ---- 方向切换 ----
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = { viewModel.dispatch(SwapUiIntent.DirectionToggled) }) {
                    Icon(Icons.Default.SwapVert, contentDescription = "切换方向", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // ---- You Receive（预估，PRD §17） ----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("You Receive (estimated)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            state.receiveAmount,
                            style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        Text(state.receiveSymbol, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }

            // ---- 路由信息（Rate / Gas，PRD §17） ----
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoRow("Rate", state.rateText)
                    InfoRow("Gas", state.gasText)
                    state.quote?.let { InfoRow("Route", it.toolName) }
                    if (state.isQuoting) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                            Text("获取报价中…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    state.error?.let {
                        Text(it.messageToUser, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ---- 滑点选择（PRD §17 Slippage，预设 0.1/0.5/1/3%） ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 50, 100, 300).forEach { bps ->
                    FilterChip(
                        selected = state.slippageBps == bps,
                        onClick = { viewModel.dispatch(SwapUiIntent.SlippageChanged(bps)) },
                        label = { Text("${bps / 100.0}%") },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ---- Swap 按钮（有报价才可点） ----
            Button(
                onClick = { viewModel.dispatch(SwapUiIntent.SwapConfirmed) },
                enabled = state.quote != null && !state.isExecuting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isExecuting) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.padding(start = 8.dp))
                    Text("执行中…")
                } else {
                    Text("Swap")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 键值信息行 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}
