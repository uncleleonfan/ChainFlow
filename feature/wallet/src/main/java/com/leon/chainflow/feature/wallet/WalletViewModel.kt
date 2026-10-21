package com.leon.chainflow.feature.wallet

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.blockchain.core.model.NetworkEnvironment
import com.leon.chainflow.blockchain.evm.tx.EvmTransactionService
import com.leon.chainflow.blockchain.evm.wallet.EvmWalletFactory
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.core.common.result.getOrNull
import com.leon.chainflow.core.common.result.onFailure
import com.leon.chainflow.core.common.result.onSuccess
import com.leon.chainflow.data.transaction.TransactionRepository
import com.leon.chainflow.data.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 钱包页 ViewModel —— Phase 2 完整实现
 *
 * 覆盖用例（PRD §12-16）：
 * 1. 创建钱包：生成助记词 → 备份展示 → 确认勾选 → 导入落盘（同一助记词）
 * 2. 导入钱包：助记词校验 → 派生地址 → 落盘
 * 3. 余额查询：RPC 实时拉取原生币余额
 * 4. Receive：复制地址（SecurityGuard 定时清空剪贴板）
 * 5. Send：地址 + 金额 → 生物识别 → 签名 → 广播 → 轮询回执
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val transactionService: EvmTransactionService,
    private val walletFactory: EvmWalletFactory,
    private val transactionRepository: TransactionRepository, // Send 历史记录
) : MviViewModel<WalletUiState, WalletUiIntent, WalletUiEvent>(
    initialState = WalletUiState(),
) {

    override suspend fun onIntent(intent: WalletUiIntent) {
        when (intent) {
            is WalletUiIntent.Load -> loadWallet()
            is WalletUiIntent.CreateWalletClicked -> startCreateFlow()
            is WalletUiIntent.BackupConfirmChanged -> updateState { copy(backupConfirmed = intent.checked) }
            is WalletUiIntent.BackupConfirmed -> confirmBackup()
            is WalletUiIntent.ImportClicked -> updateState { copy(stage = WalletUiState.Stage.IMPORT, error = null) }
            is WalletUiIntent.ImportInputChanged -> updateState { copy(importInput = intent.text, error = null) }
            is WalletUiIntent.ImportSubmitted -> submitImport()
            is WalletUiIntent.BackClicked -> goBack()
            is WalletUiIntent.ReceiveClicked -> onReceiveClicked()
            is WalletUiIntent.SendClicked -> Unit // Send 表单由 Screen 层对话框展示
            is WalletUiIntent.SendToChanged -> updateState { copy(sendTo = intent.value) }
            is WalletUiIntent.SendAmountChanged -> updateState { copy(sendAmount = intent.value) }
            is WalletUiIntent.SendConfirmed -> onSendConfirmed()
            is WalletUiIntent.RefreshBalance -> refreshBalance()
            is WalletUiIntent.DeleteWalletClicked ->
                // 切换弹窗显隐（再次点击作为“取消/关闭”语义复用）
                updateState {
                    copy(showDeleteDialog = !showDeleteDialog, deleteBackupConfirmed = false)
                }
            is WalletUiIntent.DeleteBackupConfirmChanged ->
                updateState { copy(deleteBackupConfirmed = intent.checked) }
            is WalletUiIntent.DeleteWalletConfirmed -> deleteWallet()
        }
    }

    // ================= 用例实现 =================

    /** 进入页面：判断钱包存在性并加载资产 */
    private suspend fun loadWallet() {
        val hasWallet = walletRepository.hasWallet().first()
        if (hasWallet) {
            updateState { copy(hasWallet = true, stage = WalletUiState.Stage.OVERVIEW) }
            refreshBalance()
        } else {
            updateState { copy(hasWallet = false, stage = WalletUiState.Stage.NO_WALLET) }
        }
    }

    /**
     * 创建流程第一步：只生成助记词（不落盘！）
     * 落盘延迟到用户完成备份确认 —— 防止用户中途退出产生「已保存但从未备份」的钱包
     */
    private suspend fun startCreateFlow() {
        updateState { copy(isLoading = true) }
        // 助记词生成是 CPU 密集（PBKDF2 2048 轮）：切 Default 线程
        val mnemonic = withContext(Dispatchers.Default) { walletFactory.createNewWallet().mnemonic }
        updateState {
            copy(
                isLoading = false,
                pendingMnemonic = mnemonic,
                backupConfirmed = false,
                stage = WalletUiState.Stage.BACKUP,
            )
        }
    }

    /**
     * 备份确认完成：用用户已备份的助记词正式落盘
     * （复用 importWallet 路径，保证「展示的助记词」与「保存的助记词」严格一致）
     */
    private suspend fun confirmBackup() {
        val mnemonic = currentState.pendingMnemonic ?: return
        if (!currentState.backupConfirmed) {
            sendEvent(WalletUiEvent.ShowError("请先勾选「我已安全备份助记词」"))
            return
        }
        updateState { copy(isLoading = true, error = null) }
        walletRepository.importWallet(mnemonic)
            .onSuccess { address ->
                updateState {
                    copy(
                        isLoading = false,
                        pendingMnemonic = null, // 清除内存明文，缩短敏感数据存活窗口
                        hasWallet = true,
                        stage = WalletUiState.Stage.OVERVIEW,
                        address = address,
                    )
                }
                refreshBalance()
            }
            .onFailure { error -> updateState { copy(isLoading = false, error = error) } }
    }

    /** 提交导入：校验词数后交由仓库校验并恢复钱包 */
    private suspend fun submitImport() {
        val input = currentState.importInput.trim()
        if (input.split(Regex("\\s+")).size != 12) {
            updateState { copy(error = AppError.Wallet("助记词必须为 12 个单词")) }
            return
        }
        updateState { copy(isLoading = true, error = null) }
        walletRepository.importWallet(input)
            .onSuccess { address ->
                updateState {
                    copy(
                        isLoading = false,
                        hasWallet = true,
                        importInput = "", // 清除输入明文
                        stage = WalletUiState.Stage.OVERVIEW,
                        address = address,
                    )
                }
                refreshBalance()
            }
            .onFailure { error -> updateState { copy(isLoading = false, error = error) } }
    }

    /** 页面内返回：按阶段回退并清除暂存敏感数据 */
    private suspend fun goBack() {
        updateState {
            copy(
                stage = WalletUiState.Stage.NO_WALLET,
                pendingMnemonic = null,
                importInput = "",
                error = null,
            )
        }
    }

    /** Receive：通知 UI 展示地址 + 复制（复制需 Context，在 Screen 层执行） */
    private suspend fun onReceiveClicked() {
        val address = walletRepository.walletAddress() ?: return
        updateState { copy(address = address) }
        sendEvent(WalletUiEvent.AddressCopied)
    }

    /**
     * Send 确认入口：表单校验通过后触发生物识别事件
     * （UI 层拉起 BiometricPrompt；认证通过后调用 [executeSendAfterBiometric]）
     */
    private suspend fun onSendConfirmed() {
        val state = currentState
        val to = state.sendTo
        val amount = state.sendAmount

        // PRD §27：发送前拦截无效输入
        if (!to.startsWith("0x") || to.length != 42) {
            sendEvent(WalletUiEvent.ShowError("收款地址格式无效"))
            return
        }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0.0) {
            sendEvent(WalletUiEvent.ShowError("请输入有效金额"))
            return
        }
        sendEvent(WalletUiEvent.RequireBiometric)
    }

    /**
     * 生物识别通过后的实际发送（由 Screen 层认证成功回调）
     * 链路：unlock → sign → broadcast → monitor（PRD §16）
     */
    fun executeSendAfterBiometric(to: String, amountEth: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            val (chain, isMainnet) = walletRepository.currentNetwork()
            val environment = if (isMainnet) NetworkEnvironment.MAINNET else NetworkEnvironment.TESTNET

            // 1. 解锁（每次重新解密派生，不缓存私钥）
            val credentials = walletRepository.unlock().getOrNull()
            if (credentials == null) {
                updateState { copy(isLoading = false, error = AppError.Wallet("unlock failed")) }
                sendEvent(WalletUiEvent.ShowError("钱包解锁失败"))
                return@launch
            }

            // 2. 签名 + 广播
            transactionService.sendNativeTransfer(credentials, to, amountEth, chain, environment)
                .onSuccess { txHash ->
                    updateState { copy(isLoading = false) }
                    sendEvent(WalletUiEvent.TransactionBroadcast(txHash))

                    // 记录 Send 历史 + 后台监控（Activity 页数据源，PRD §22）
                    transactionRepository.recordAndMonitor(
                        txHash = txHash,
                        type = "SEND",
                        chain = chain,
                        environment = environment,
                        fromSymbol = chain.nativeSymbol,
                        fromAmount = amountEth,
                        counterparty = to,
                    )
                    // 3. 后台轮询确认（独立子协程；页面销毁自动取消）
                    launch {
                        val final = transactionService.monitorTransaction(txHash, chain, environment).getOrNull()
                        if (final == com.leon.chainflow.blockchain.core.model.TransactionStatus.CONFIRMED) {
                            sendEvent(WalletUiEvent.TransactionConfirmed)
                            refreshBalance()
                        }
                    }
                }
                .onFailure { error ->
                    updateState { copy(isLoading = false, error = error) }
                    sendEvent(WalletUiEvent.ShowError(error.messageToUser))
                }
        }
    }

    /**
     * 删除钱包（「忘记钱包」）
     *
     * 语义：仅清除本地数据（Keystore 密文 + 地址 + DataStore 偏好），
     * 不影响链上资产 —— 用户可用助记词随时重新导入恢复。
     */
    private suspend fun deleteWallet() {
        updateState { copy(isLoading = true) }
        walletRepository.deleteWallet() // 同步清除加密存储
        updateState {
            copy(
                isLoading = false,
                hasWallet = false,
                stage = WalletUiState.Stage.NO_WALLET, // 回到创建/导入引导页
                showDeleteDialog = false,
                deleteBackupConfirmed = false,
                address = null,
                balance = null,
                sendTo = "",
                sendAmount = "",
            )
        }
        sendEvent(WalletUiEvent.WalletDeleted)
    }

    /** 刷新余额与网络信息（EVM + SOL 双链并行，Phase 5） */
    private suspend fun refreshBalance() {
        updateState { copy(isLoading = true) }
        val (chain, isMainnet) = walletRepository.currentNetwork()

        // EVM 原生币余额（主链）
        walletRepository.getNativeBalance()
            .onSuccess { balance ->
                updateState {
                    copy(
                        isLoading = false,
                        balance = "%.6f".format(balance).trimEnd('0').trimEnd('.'),
                        address = walletRepository.walletAddress(),
                        chain = chain,
                        isMainnet = isMainnet,
                    )
                }
            }
            .onFailure { error ->
                updateState { copy(isLoading = false, error = error) }
            }

        // SOL 余额（Phase 5：同助记词派生，失败不阻断 EVM 展示）
        walletRepository.getSolBalance()
            .onSuccess { sol ->
                updateState {
                    copy(solBalance = "%.4f".format(sol).trimEnd('0').trimEnd('.'))
                }
            }
            .onFailure { /* SOL 查询失败静默：总览仍显示 EVM 资产 */ }
    }
}
