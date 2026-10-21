package com.leon.chainflow.feature.wallet

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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leon.chainflow.core.security.BiometricAuthenticator
import com.leon.chainflow.core.security.SecurityGuard
import com.leon.chainflow.core.ui.components.EmptyView

/**
 * 钱包页（Wallet Tab）—— Phase 2 完整实现
 *
 * 页面形态由 [WalletUiState.stage] 驱动（页面内状态机，不走全局导航）：
 * NO_WALLET → BACKUP → BACKUP_CONFIRM → OVERVIEW
 *      └────→ IMPORT ─┘
 *
 * 敏感页防护：助记词展示（BACKUP）阶段启用 FLAG_SECURE 禁止截屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 生物识别器（需要 FragmentActivity —— MainActivity 即是）
    val biometricAuthenticator = remember { BiometricAuthenticator() }
    val securityGuard = remember { SecurityGuard(context.applicationContext) }

    // 截屏防护：仅在助记词展示阶段开启（离开自动恢复）
    val activity = context as? FragmentActivity
    LaunchedEffect(state.stage) {
        if (state.stage == WalletUiState.Stage.BACKUP) {
            securityGuard.enableScreenshotProtection(activity ?: return@LaunchedEffect)
        } else {
            activity?.let { securityGuard.disableScreenshotProtection(it) }
        }
    }

    // 一次性事件消费：Snackbar 提示 / 生物识别拉起
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WalletUiEvent.AddressCopied -> {
                    // 复制地址到剪贴板（SecurityGuard 版本：45s 后自动清空）
                    state.address?.let { securityGuard.copySensitiveToClipboard(it, "ChainFlow Address") }
                    snackbarHostState.showSnackbar("地址已复制（45 秒后自动清除）")
                }
                is WalletUiEvent.RequireBiometric -> {
                    // 拉起生物识别；通过后执行真实发送（PRD §16 Biometric → Sign）
                    val activityContext = context as? FragmentActivity
                    if (activityContext != null && biometricAuthenticator.canAuthenticate(activityContext)) {
                        val ok = biometricAuthenticator.authenticate(
                            activity = activityContext,
                            title = "确认转账",
                            subtitle = "使用生物识别验证以签名交易",
                        )
                        if (ok) {
                            viewModel.executeSendAfterBiometric(state.sendTo, state.sendAmount)
                        } else {
                            snackbarHostState.showSnackbar("交易已取消")
                        }
                    } else {
                        // 设备无生物识别：直接发送（Phase 6 可加 PIN 码备份方案）
                        viewModel.executeSendAfterBiometric(state.sendTo, state.sendAmount)
                    }
                }
                is WalletUiEvent.TransactionBroadcast ->
                    snackbarHostState.showSnackbar("交易已广播：${event.txHash.take(18)}…")
                is WalletUiEvent.TransactionConfirmed ->
                    snackbarHostState.showSnackbar("✅ 交易已确认")
                is WalletUiEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
                is WalletUiEvent.WalletDeleted ->
                    snackbarHostState.showSnackbar("钱包已删除，可用助记词重新导入恢复")
            }
        }
    }

    // 进入页面触发首次加载
    LaunchedEffect(Unit) { viewModel.dispatch(WalletUiIntent.Load) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Wallet") },
                navigationIcon = {
                    if (state.stage != WalletUiState.Stage.OVERVIEW) {
                        IconButton(onClick = { viewModel.dispatch(WalletUiIntent.BackClicked) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                // 总览阶段提供「删除钱包」菜单（无钱包阶段无此入口，
                // 创建/导入入口在 NO_WALLET 引导页上）
                actions = {
                    if (state.stage == WalletUiState.Stage.OVERVIEW) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除钱包") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.dispatch(WalletUiIntent.DeleteWalletClicked)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.stage) {
                WalletUiState.Stage.NO_WALLET -> NoWalletContent(
                    onCreate = { viewModel.dispatch(WalletUiIntent.CreateWalletClicked) },
                    onImport = { viewModel.dispatch(WalletUiIntent.ImportClicked) },
                )

                // BACKUP_CONFIRM 阶段已合并进 BACKUP（勾选 + 完成按钮同页）
                WalletUiState.Stage.BACKUP, WalletUiState.Stage.BACKUP_CONFIRM -> BackupContent(
                    mnemonic = state.pendingMnemonic.orEmpty(),
                    confirmed = state.backupConfirmed,
                    onConfirmChanged = { viewModel.dispatch(WalletUiIntent.BackupConfirmChanged(it)) },
                    onDone = { viewModel.dispatch(WalletUiIntent.BackupConfirmed) },
                    isLoading = state.isLoading,
                )

                WalletUiState.Stage.IMPORT -> ImportContent(
                    input = state.importInput,
                    onInputChanged = { viewModel.dispatch(WalletUiIntent.ImportInputChanged(it)) },
                    onSubmit = { viewModel.dispatch(WalletUiIntent.ImportSubmitted) },
                    isLoading = state.isLoading,
                )

                WalletUiState.Stage.OVERVIEW -> OverviewContent(
                    state = state,
                    viewModel = viewModel,
                )
            }
        }
    }
}

/** 无钱包引导页（PRD §12：Create / Import 双入口） */
@Composable
private fun NoWalletContent(onCreate: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔐", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            "创建你的非托管钱包",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "私钥只保存在本机安全硬件中\nChainFlow 无法帮你找回，请务必妥善备份助记词",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Text("创建钱包")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("导入钱包")
        }
    }
}

/**
 * 助记词备份页（PRD §12：12-word mnemonic + Backup confirmation）
 * 敏感页：外层已启用 FLAG_SECURE 禁止截屏
 */
@Composable
private fun BackupContent(
    mnemonic: String,
    confirmed: Boolean,
    onConfirmChanged: (Boolean) -> Unit,
    onDone: () -> Unit,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "⚠️ 备份你的助记词",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "这 12 个单词是你钱包的唯一恢复方式：\n" +
                "• 任何人拿到助记词都能转走你的资产\n" +
                "• 请抄写在纸上离线保存，切勿截图 / 云端存储",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        // 助记词卡片（等宽字体 + 编号，便于人工抄写核对）
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                mnemonic.split(" ").forEachIndexed { index, word ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            word,
                            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = onConfirmChanged)
            Text("我已安全备份助记词，了解丢失将无法恢复", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDone,
            enabled = confirmed && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("完成，进入钱包")
        }
    }
}

/** 导入钱包页（PRD §12：Restore） */
@Composable
private fun ImportContent(
    input: String,
    onInputChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    isLoading: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text("导入钱包", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "输入 12 个单词的助记词（空格分隔）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = onInputChanged,
            label = { Text("助记词") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSubmit,
            enabled = input.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp))
            else Text("导入")
        }
    }
}

/** 钱包总览页（PRD §14：Total Assets + Assets + Receive/Send/Swap） */
@Composable
private fun OverviewContent(
    state: WalletUiState,
    viewModel: WalletViewModel,
) {
    var showSendDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        // ---- 网络徽章（Testnet/Mainnet + 链名，PRD §32） ----
        Text(
            "${state.chain.displayName} · ${if (state.isMainnet) "⚠️ Mainnet" else "Testnet"}",
            style = MaterialTheme.typography.labelMedium,
            color = if (state.isMainnet) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        // ---- 总资产卡片 ----
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("总资产", style = MaterialTheme.typography.bodyMedium)
                Text(
                    state.balance?.let { "$it ${state.chain.nativeSymbol}" } ?: "—",
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "地址：${state.address?.let { it.take(10) + "…" + it.takeLast(8) } ?: ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ---- 资产列表（PRD §14 Assets：EVM + SOL，Phase 5 双链） ----
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                // EVM 原生币行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Text(state.chain.nativeSymbol, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.balance?.let { "$it ${state.chain.nativeSymbol}" } ?: "—",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(12.dp))
                // SOL 行（Phase 5：同助记词的 Solana 账户）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Text("SOL", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        state.solBalance?.let { "$it SOL" } ?: "…",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- 操作按钮组（PRD §14：Receive / Send / Swap） ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { viewModel.dispatch(WalletUiIntent.ReceiveClicked) },
                modifier = Modifier.weight(1f),
            ) { Text("Receive") }
            Button(
                onClick = { showSendDialog = true },
                modifier = Modifier.weight(1f),
            ) { Text("Send") }
        }

        Spacer(Modifier.height(24.dp))
        if (state.isLoading) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) { CircularProgressIndicator() }
        }
    }

    // ---- 删除钱包二次确认对话框（防误删：需勾选「已备份」才可确认） ----
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dispatch(WalletUiIntent.DeleteWalletClicked) }, // 复用：再次点击关闭
            title = { Text("⚠️ 删除钱包") },
            text = {
                Column {
                    Text(
                        "将从本机移除钱包数据（链上资产不受影响）。\n如果没有备份助记词，删除后将无法恢复资产。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            viewModel.dispatch(
                                WalletUiIntent.DeleteBackupConfirmChanged(!state.deleteBackupConfirmed),
                            )
                        },
                    ) {
                        Checkbox(
                            checked = state.deleteBackupConfirmed,
                            onCheckedChange = {
                                viewModel.dispatch(WalletUiIntent.DeleteBackupConfirmChanged(it))
                            },
                        )
                        Text("我已备份助记词，了解删除后可用它恢复", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                Text(
                    text = "确认删除",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable(
                        enabled = state.deleteBackupConfirmed, // 未勾选备份时禁用
                    ) {
                        viewModel.dispatch(WalletUiIntent.DeleteWalletConfirmed)
                    },
                )
            },
            dismissButton = {
                Text(
                    text = "取消",
                    modifier = Modifier.clickable {
                        viewModel.dispatch(WalletUiIntent.DeleteWalletClicked) // 切换关闭
                    },
                )
            },
        )
    }

    // ---- Send 对话框（地址 + 金额 → 确认触发生物识别） ----
    if (showSendDialog) {
        AlertDialog(
            onDismissRequest = { showSendDialog = false },
            title = { Text("发送 ${state.chain.nativeSymbol}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = state.sendTo,
                        onValueChange = { viewModel.dispatch(WalletUiIntent.SendToChanged(it)) },
                        label = { Text("收款地址 (0x…)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.sendAmount,
                        onValueChange = { viewModel.dispatch(WalletUiIntent.SendAmountChanged(it)) },
                        label = { Text("金额 (${state.chain.nativeSymbol})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSendDialog = false
                        viewModel.dispatch(WalletUiIntent.SendConfirmed)
                    },
                ) { Text("确认发送") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSendDialog = false }) { Text("取消") }
            },
        )
    }
}
