package com.leon.chainflow.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leon.chainflow.blockchain.core.model.Chain

/**
 * 设置页（PRD §32：Network ○ Testnet ○ Mainnet）
 *
 * Mainnet 切换强制风险确认（PRD §32 原文）：
 * 「⚠️ You are about to use real assets. ChainFlow is an open-source educational project.」
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.dispatch(SettingsUiIntent.Load) }

    // 网络切换提示
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsUiEvent.EnvironmentChanged ->
                    snackbarHostState.showSnackbar(
                        if (event.isMainnet) "已切换到 Mainnet（真实资产）" else "已切换到 Testnet",
                    )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ---- 链选择（PRD §3：Ethereum / Base / BSC） ----
            Column {
                Text("Blockchain", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chain.entries.forEach { chain ->
                        FilterChip(
                            selected = state.currentChain == chain,
                            onClick = { viewModel.dispatch(SettingsUiIntent.ChainSelected(chain)) },
                            label = { Text(chain.displayName) },
                        )
                    }
                }
            }

            // ---- 网络环境（PRD §32：默认 Testnet） ----
            Column {
                Text("Network", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.isMainnet,
                        onClick = { viewModel.dispatch(SettingsUiIntent.TestnetSelected) },
                        label = { Text("○ Testnet") },
                    )
                    FilterChip(
                        selected = state.isMainnet,
                        onClick = { viewModel.dispatch(SettingsUiIntent.MainnetSelected) },
                        label = { Text("⚠️ Mainnet") },
                    )
                }
                Text(
                    if (state.isMainnet) "当前：Mainnet —— 使用真实资产" else "当前：Testnet —— 测试网络",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isMainnet) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // ---- Mainnet 风险确认弹窗（PRD §32 原文要求） ----
    if (state.showMainnetWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(SettingsUiIntent.MainnetCancelled) },
            title = { Text("⚠️ Mainnet") },
            text = {
                Text(
                    "你即将使用真实资产。\n\n" +
                        "ChainFlow 是一个开源教育项目。\n" +
                        "请确认你了解交易不可撤销，并将使用真实资金。",
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.dispatch(SettingsUiIntent.MainnetConfirmed) }) {
                    Text("我已了解风险，继续")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dispatch(SettingsUiIntent.MainnetCancelled) }) {
                    Text("取消")
                }
            },
        )
    }
}
