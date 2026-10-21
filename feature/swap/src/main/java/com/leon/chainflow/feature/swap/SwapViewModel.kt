package com.leon.chainflow.feature.swap

import androidx.lifecycle.viewModelScope
import com.leon.chainflow.blockchain.core.model.NetworkEnvironment
import com.leon.chainflow.blockchain.evm.tx.EvmTransactionService
import com.leon.chainflow.core.common.mvi.MviViewModel
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.core.common.result.getOrNull
import com.leon.chainflow.core.common.result.onFailure
import com.leon.chainflow.core.common.result.onSuccess
import com.leon.chainflow.data.transaction.SwapRepository
import com.leon.chainflow.data.transaction.SwapToken
import com.leon.chainflow.data.transaction.TransactionRepository
import com.leon.chainflow.data.wallet.WalletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/**
 * Swap 页 ViewModel（PRD §17-18 完整流程）
 *
 * 报价策略：金额输入防抖 600ms → getQuote → 更新 rate/impact/gas 展示
 * 执行链路：SwapConfirmed → 生物识别（UI 层）→ executeSwapAfterBiometric：
 * ```
 * getStepTransaction（Li.Fi 预构建）
 *   → sendPrebuiltTransaction（本地签名 + 广播，EIP-1559）
 *   → recordAndMonitor（Room 记录 + 后台轮询回执）
 * ```
 */
@HiltViewModel
class SwapViewModel @Inject constructor(
    private val swapRepository: SwapRepository,
    private val transactionRepository: TransactionRepository,
    private val walletRepository: WalletRepository,
    private val transactionService: EvmTransactionService,
) : MviViewModel<SwapUiState, SwapUiIntent, SwapUiEvent>(
    initialState = SwapUiState(),
) {

    /** 防抖报价任务 */
    private var quoteJob: Job? = null

    override suspend fun onIntent(intent: SwapUiIntent) {
        when (intent) {
            is SwapUiIntent.PayTokenSelected -> onPayTokenSelected(intent.symbol)
            is SwapUiIntent.PayAmountChanged -> onAmountChanged(intent.amount)
            is SwapUiIntent.DirectionToggled -> onDirectionToggled()
            is SwapUiIntent.SlippageChanged -> onSlippageChanged(intent.bps)
            is SwapUiIntent.SwapConfirmed -> onSwapConfirmed()
        }
    }

    /**
     * 指定支付 Token（详情页「Swap」入口）
     *
     * 解析不到地址说明该币种不在当前网络的 Swap 白名单（行情列表多为 CEX 币，
     * 不在 Base 链上）—— 此时保持默认交易对并提示，避免「显示 A 却成交 B」。
     */
    private suspend fun onPayTokenSelected(symbol: String) {
        if (symbol.isBlank()) return // 底部 Tab 直接进入：保持默认 ETH → USDC
        val (_, isMainnet) = walletRepository.currentNetwork()
        val token = SwapRepository.findToken(symbol, isMainnet)
        if (token == null) {
            sendEvent(SwapUiEvent.ShowMessage("${symbol.uppercase()} 在 Base 链暂不支持 Swap"))
            return
        }
        if (currentState.paySymbol.equals(token.symbol, ignoreCase = true)) return // 同一币种重复进入：保留已输入金额

        val receiveSymbol = if (token.symbol.equals("USDC", ignoreCase = true)) "ETH" else "USDC"
        updateState { copy(paySymbol = token.symbol, receiveSymbol = receiveSymbol, quote = null, error = null) }
        if (currentState.payAmount.toDoubleOrNull()?.let { it > 0 } == true) {
            quoteJob?.cancel()
            quoteJob = viewModelScope.launch { requestQuote() }
        }
    }

    /** 金额变化：防抖 600ms 后请求报价（PRD §17 Get Quote） */
    private fun onAmountChanged(amount: String) {
        updateState { copy(payAmount = amount.filter { it.isDigit() || it == '.' }, quote = null, error = null) }
        quoteJob?.cancel()

        val parsed = currentState.payAmount.toDoubleOrNull()
        if (parsed == null || parsed <= 0.0) return // 空输入/非法：不报价

        quoteJob = viewModelScope.launch {
            delay(600) // 防抖：用户连续输入时只发最后一次
            requestQuote()
        }
    }

    /** 方向切换：支付/收到互换并重报价 */
    private fun onDirectionToggled() {
        updateState { copy(paySymbol = receiveSymbol, receiveSymbol = paySymbol, quote = null) }
        if (currentState.payAmount.toDoubleOrNull()?.let { it > 0 } == true) {
            quoteJob?.cancel()
            quoteJob = viewModelScope.launch { requestQuote() }
        }
    }

    /** 滑点变化：立即重报价 */
    private fun onSlippageChanged(bps: Int) {
        updateState { copy(slippageBps = bps, quote = null) }
        if (currentState.payAmount.toDoubleOrNull()?.let { it > 0 } == true) {
            quoteJob?.cancel()
            quoteJob = viewModelScope.launch { requestQuote() }
        }
    }

    /** 请求报价（符号 → 地址/精度由网络环境决定，USDC 主网与测试网合约不同） */
    private suspend fun requestQuote() {
        val state = currentState
        updateState { copy(isQuoting = true) }
        val (_, isMainnet) = walletRepository.currentNetwork()

        // 兜底：状态里的符号若不在白名单（如默认值之外的脏数据），退回原生币
        val from = SwapRepository.findToken(state.paySymbol, isMainnet) ?: SwapRepository.NATIVE_ETH
        val to = SwapRepository.findToken(state.receiveSymbol, isMainnet)
            ?: SwapToken("USDC", SwapRepository.usdcAddress(isMainnet), 6)

        swapRepository.getQuote(from, to, state.payAmount, state.slippageBps.toDouble())
            .onSuccess { quote -> updateState { copy(isQuoting = false, quote = quote) } }
            .onFailure { error -> updateState { copy(isQuoting = false, error = error) } }
    }

    /** 确认 Swap：校验报价存在后触发生物识别 */
    private suspend fun onSwapConfirmed() {
        if (currentState.quote == null) {
            sendEvent(SwapUiEvent.ShowMessage("请先输入金额获取报价"))
            return
        }
        if (currentState.isExecuting) return
        sendEvent(SwapUiEvent.RequireBiometric)
    }

    /**
     * 生物识别通过后的执行（UI 层回调）
     * 链路：预构建 → 签名广播 → 记录监控（PRD §18）
     */
    fun executeSwapAfterBiometric() {
        viewModelScope.launch {
            updateState { copy(isExecuting = true, error = null) }
            val state = currentState
            val quote = state.quote ?: return@launch

            val (chain, isMainnet) = walletRepository.currentNetwork()
            val environment = if (isMainnet) NetworkEnvironment.MAINNET else NetworkEnvironment.TESTNET

            // 1. 解锁钱包（每次解密派生，不缓存私钥）
            val credentials = walletRepository.unlock().getOrNull()
            if (credentials == null) {
                updateState { copy(isExecuting = false) }
                sendEvent(SwapUiEvent.ShowMessage("钱包解锁失败"))
                return@launch
            }

            // 2. 获取预构建交易
            val step = swapRepository.getStepTransaction(quote).getOrNull()
            val txReq = step?.transactionRequest
            if (step == null || txReq == null || txReq.to == null || txReq.data == null) {
                updateState { copy(isExecuting = false, error = AppError.TransactionFailed("获取交易数据失败")) }
                sendEvent(SwapUiEvent.ShowMessage("获取 Swap 交易数据失败"))
                return@launch
            }

            // 3. 本地签名 + 广播（EIP-1559 优先）
            transactionService.sendPrebuiltTransaction(
                credentials = credentials,
                to = txReq.to!!,
                data = txReq.data!!,
                value = parseWei(txReq.value),
                gasLimit = parseWei(txReq.gasLimit ?: "200000"),
                maxFeePerGas = parseWeiOrNull(txReq.maxFeePerGas),
                maxPriorityFeePerGas = parseWeiOrNull(txReq.maxPriorityFeePerGas),
                chain = chain,
                environment = environment,
            ).onSuccess { txHash ->
                updateState { copy(isExecuting = false) }
                sendEvent(SwapUiEvent.TransactionBroadcast(txHash))

                // 4. 记录历史 + 后台监控（Activity 页即时可见 PENDING）
                transactionRepository.recordAndMonitor(
                    txHash = txHash,
                    type = "SWAP",
                    chain = chain,
                    environment = environment,
                    fromSymbol = quote.fromSymbol,
                    fromAmount = quote.fromAmount.toPlainString(),
                    toSymbol = quote.toSymbol,
                    toAmount = quote.toAmount.toPlainString(),
                    counterparty = txReq.to,
                )
            }.onFailure { error ->
                updateState { copy(isExecuting = false, error = error) }
                sendEvent(SwapUiEvent.ShowMessage(error.messageToUser))
            }
        }
    }

    /** 解析 wei 数量（Li.Fi 可能返回十进制或 0x 十六进制字符串） */
    private fun parseWei(value: String?): BigInteger =
        value?.let { BigInteger(it.removePrefix("0x"), if (it.startsWith("0x")) 16 else 10) } ?: BigInteger.ZERO

    private fun parseWeiOrNull(value: String?): BigInteger? =
        value?.let { BigInteger(it.removePrefix("0x"), if (it.startsWith("0x")) 16 else 10) }
}
