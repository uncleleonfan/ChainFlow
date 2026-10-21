package com.leon.chainflow.blockchain.evm.tx

import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.blockchain.core.model.FeeEstimate
import com.leon.chainflow.blockchain.core.model.NetworkEnvironment
import com.leon.chainflow.blockchain.core.model.TransactionStatus
import com.leon.chainflow.blockchain.evm.rpc.EvmRpcClient
import com.leon.chainflow.core.common.result.ChainFlowResult
import com.leon.chainflow.core.common.result.chainFlowRunCatching
import com.leon.chainflow.core.common.result.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EVM 交易服务（PRD §20 Transaction Engine 的 Phase 2 子集）
 *
 * 覆盖 Send 流程（PRD §16）：
 * ```
 * TransactionPreview（预览：To/Amount/Gas/Total）
 *        ↓ 用户生物识别确认
 * Build（nonce + gasPrice + gasLimit + value）
 *        ↓
 * Sign 本地签名（TransactionEncoder，私钥离线）
 *        ↓
 * Broadcast（eth_sendRawTransaction）
 *        ↓
 * Monitor（轮询回执 → Pending → Confirmed/Failed）
 * ```
 *
 * 安全约束：签名使用 web3j 的本地实现（TransactionEncoder.sign），
 * 私钥不进入任何网络请求。
 */
@Singleton
class EvmTransactionService @Inject constructor(
    private val rpcClient: EvmRpcClient,
) {

    /**
     * 构建转账预览信息（进入 Send 确认页前调用）
     *
     * @return gas 上限 / 单价 / ETH 计价预估费用
     */
    suspend fun previewTransfer(
        from: String,
        to: String,
        chain: Chain,
        environment: NetworkEnvironment,
    ): ChainFlowResult<FeeEstimate> =
        rpcClient.estimateTransferFee(from, to, chain.rpcUrl(environment == NetworkEnvironment.MAINNET))

    /**
     * 构建 + 签名 + 广播一笔原生币转账（ETH/BNB）
     *
     * @param amountEth 以 ETH 为单位十进制金额（UI 输入值）
     * @param credentials 已解锁的钱包凭证
     * @return txHash（随后用 [monitorTransaction] 跟踪状态）
     */
    suspend fun sendNativeTransfer(
        credentials: Credentials,
        to: String,
        amountEth: String,
        chain: Chain,
        environment: NetworkEnvironment,
    ): ChainFlowResult<String> {
        val rpcUrl = chain.rpcUrl(environment == NetworkEnvironment.MAINNET)

        // eth → wei（18 位精度），使用 BigDecimal 保证小额精度
        val valueWei = java.math.BigDecimal(amountEth)
            .movePointRight(18)
            .toBigInteger()

        return chainFlowRunCatching {
            // 1. 获取 nonce（pending 模式：包含内存池中未确认交易，防止 nonce 冲突）
            val nonce = (rpcClient.getTransactionCount(credentials.address, rpcUrl) as ChainFlowResult.Success).data

            // 2. 获取 gas 参数
            val gasPrice = (rpcClient.getGasPrice(rpcUrl) as ChainFlowResult.Success).data
            val gasLimit = (rpcClient.estimateGas(credentials.address, to, "0x", valueWei, rpcUrl) as ChainFlowResult.Success).data

            // 3. 构建原始交易（Legacy 类型：公共测试网兼容性最好；EIP-1559 在 Phase 4 优化）
            val rawTx = RawTransaction.createEtherTransaction(
                nonce, gasPrice, gasLimit, to, valueWei,
            )

            // 4. 本地签名（EIP-155 重放保护：chainId 必须按环境区分 —— Testnet 签成 Mainnet id 会被节点拒绝）
            val signedBytes = TransactionEncoder.signMessage(
                rawTx, chain.activeChainId(environment == NetworkEnvironment.MAINNET), credentials,
            )
            val signedHex = Numeric.toHexString(signedBytes)

            // 5. 广播
            (rpcClient.sendRawTransaction(signedHex, rpcUrl) as ChainFlowResult.Success).data
        }
    }

    /**
     * 签名 + 广播一笔 Li.Fi 预构建交易（PRD §18 Swap 流程核心）
     *
     * Li.Fi 返回的 transactionRequest 已含 to/data/gas/1559 费率字段，
     * 本方法补齐 nonce 后本地签名（EIP-1559 优先，缺费率字段时回退 Legacy）。
     *
     * @return txHash
     */
    suspend fun sendPrebuiltTransaction(
        credentials: Credentials,
        to: String,
        data: String,
        value: BigInteger,
        gasLimit: BigInteger,
        maxFeePerGas: BigInteger?,
        maxPriorityFeePerGas: BigInteger?,
        chain: Chain,
        environment: NetworkEnvironment,
    ): ChainFlowResult<String> {
        val isMainnet = environment == NetworkEnvironment.MAINNET
        val rpcUrl = chain.rpcUrl(isMainnet)
        return chainFlowRunCatching {
            val nonce = (rpcClient.getTransactionCount(credentials.address, rpcUrl) as ChainFlowResult.Success).data

            val signedHex = if (maxFeePerGas != null && maxPriorityFeePerGas != null) {
                // EIP-1559 动态费率交易（Base 等现代链默认）
                val tx1559 = RawTransaction.createTransaction(
                    chain.activeChainId(isMainnet), // EIP-155 防重放：chainId 必须与目标网络一致
                    nonce,
                    gasLimit,
                    to,
                    value,
                    data,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                )
                Numeric.toHexString(TransactionEncoder.signMessage(tx1559, chain.activeChainId(isMainnet), credentials))
            } else {
                // Legacy 回退（无 1559 字段时）
                val gasPrice = (rpcClient.getGasPrice(rpcUrl) as ChainFlowResult.Success).data
                val legacy = RawTransaction.createTransaction(
                    nonce, gasPrice, gasLimit, to, value, data,
                )
                Numeric.toHexString(TransactionEncoder.signMessage(legacy, chain.activeChainId(isMainnet), credentials))
            }

            (rpcClient.sendRawTransaction(signedHex, rpcUrl) as ChainFlowResult.Success).data
        }
    }

    /**
     * 轮询交易回执直到确认 / 失败 / 超时
     *
     * @param onStatus 每次状态变化回调（PENDING → CONFIRMED/FAILED），
     *                 UI 层用它驱动状态展示（PRD §16 Pending → Confirmed）
     * @return 最终状态；超时返回 PENDING（交由用户去浏览器查看）
     */
    suspend fun monitorTransaction(
        txHash: String,
        chain: Chain,
        environment: NetworkEnvironment,
        onStatus: suspend (TransactionStatus) -> Unit = {},
    ): ChainFlowResult<TransactionStatus> {
        val rpcUrl = chain.rpcUrl(environment == NetworkEnvironment.MAINNET)
        val deadline = System.currentTimeMillis() + com.leon.chainflow.core.common.AppConstants.TX_POLL_TIMEOUT_MS

        onStatus(TransactionStatus.PENDING)

        while (currentCoroutineContext().isActive && System.currentTimeMillis() < deadline) {
            val receipt = rpcClient.getTransactionReceipt(txHash, rpcUrl)
            if (receipt is ChainFlowResult.Success && receipt.data != null) {
                // status 字段："0x1" 成功 / "0x0" 失败（revert）
                val receiptJson = requireNotNull(receipt.data) { "receipt missing" }
                val success = receiptJson.optString("status") == "0x1"
                val final = if (success) TransactionStatus.CONFIRMED else TransactionStatus.FAILED
                onStatus(final)
                return ChainFlowResult.success(final)
            }
            delay(com.leon.chainflow.core.common.AppConstants.TX_POLL_INTERVAL_MS)
        }
        return ChainFlowResult.success(TransactionStatus.PENDING) // 超时未确认
    }
}
