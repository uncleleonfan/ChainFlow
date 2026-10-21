package com.leon.chainflow.feature.activity

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leon.chainflow.core.ui.components.EmptyView
import com.leon.chainflow.core.ui.theme.LocalChainFlowColors
import com.leon.chainflow.data.transaction.db.TransactionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 活动页（Activity Tab，PRD §22）
 *
 * 每笔交易展示：类型（Swap/Send）/ 币种与数量 / 状态 / 时间 / TxHash
 * 点击跳转 Block Explorer。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val semantic = LocalChainFlowColors.current

    LaunchedEffect(Unit) { viewModel.dispatch(ActivityUiIntent.Load) }

    // 消费事件：打开浏览器
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ActivityUiEvent.OpenInExplorer ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
            }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Activity") }) },
    ) { padding ->
        if (state.transactions.isEmpty()) {
            EmptyView(
                message = "暂无交易记录\n发起 Send 或 Swap 后将在这里展示",
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                items(state.transactions, key = { it.txHash }) { tx ->
                    TransactionRow(
                        tx = tx,
                        onClick = {
                            viewModel.dispatch(
                                ActivityUiIntent.TransactionClicked(tx.txHash, tx.chainId),
                            )
                        },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

/** 单笔交易行（PRD §22 线框） */
@Composable
private fun TransactionRow(tx: TransactionEntity, onClick: () -> Unit) {
    val semantic = LocalChainFlowColors.current
    val isSwap = tx.type == "SWAP"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isSwap) "Swap" else "Send",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                // Swap: -0.1 ETH → +462 USDC；Send: -100 USDC
                buildString {
                    append("-${trimAmount(tx.fromAmount)} ${tx.fromSymbol}")
                    if (isSwap) tx.toAmount?.let { append("  →  +${trimAmount(it)} ${tx.toSymbol}") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${timeText(tx.createdAt)} · ${tx.txHash.take(12)}…",
                style = MaterialTheme.typography.bodySmall,
                color = semantic.textTertiary,
            )
        }
        // 状态徽章（PENDING 黄 / CONFIRMED 绿 / FAILED 红）
        Text(
            when (tx.status) {
                "CONFIRMED" -> "✓ 已确认"
                "FAILED" -> "✗ 失败"
                else -> "⏳ 待确认"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when (tx.status) {
                "CONFIRMED" -> semantic.priceUp
                "FAILED" -> semantic.priceDown
                else -> semantic.warning
            },
        )
    }
}

/** 金额展示修剪（去多余尾零） */
private fun trimAmount(amount: String): String =
    amount.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: amount

/** 时间格式化（MM-dd HH:mm） */
private fun timeText(epochMs: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(epochMs))
