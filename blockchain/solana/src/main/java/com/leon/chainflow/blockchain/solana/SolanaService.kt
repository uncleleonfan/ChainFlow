package com.leon.chainflow.blockchain.solana

import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.core.common.result.ChainFlowResult
import com.leon.chainflow.core.common.result.chainFlowRunCatching
import com.solana.Solana
import com.solana.api.getBalance
import com.solana.api.getRecentBlockhash
import com.solana.api.getSignatureStatuses
import com.solana.api.sendTransaction
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import com.solana.core.Transaction
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.Network
import com.solana.networking.RPCEndpoint
import com.solana.programs.SystemProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Solana 钱包派生器（PRD §23：Account / Signer）
 *
 * 与 EVM 共用同一套 BIP-39 助记词（一份备份恢复所有链），
 * 派生路径 m/44'/501'/0'/0'（Solana CLI 标准，SolanaKt 默认 BIP44）。
 */
@Singleton
class SolanaWalletFactory @Inject constructor() {

    /** 派生结果：Base58 公钥地址 + ed25519 签名账户 */
    data class SolanaAccount(
        val publicKeyBase58: String,
        val account: HotAccount,
    )

    /** 助记词 → Solana 账户（ed25519，与 EVM secp256k1 路径不同但助记词相同；passphrase 为空与 EVM 侧一致） */
    fun fromMnemonic(mnemonic: String): SolanaAccount {
        val words = mnemonic.trim().lowercase().split(Regex("\\s+"))
        val account = HotAccount.fromMnemonic(words, "")
        return SolanaAccount(
            publicKeyBase58 = account.publicKey.toBase58(),
            account = account,
        )
    }
}

/**
 * Solana 交易服务（PRD §23：Send SOL / Recent Blockhash / 广播 / 状态）
 *
 * Send 流程：
 * ```
 * getLatestBlockhash（防重放，等价 EVM nonce）
 *   → Transaction（SystemProgram.transfer 指令）
 *   → 本地 ed25519 签名 + 序列化
 *   → sendTransaction 广播
 *   → getSignatureStatuses 轮询确认
 * ```
 */
@Singleton
class SolanaService @Inject constructor() {

    /** 按环境创建 Solana 客户端（publicnode 直连端点，见 [SolanaNetwork]） */
    private fun api(isMainnet: Boolean): Solana {
        val url = SolanaNetwork.rpcUrl(isMainnet)
        val endpoint = RPCEndpoint.custom(
            URL(url),
            URL(url),
            if (isMainnet) Network.mainnetBeta else Network.devnet,
        )
        return Solana(HttpNetworkingRouter(endpoint))
    }

    /** 查询 SOL 余额（lamports → SOL，9 位小数） */
    suspend fun getBalance(addressBase58: String, isMainnet: Boolean): ChainFlowResult<BigDecimal> =
        withContext(Dispatchers.IO) {
            chainFlowRunCatching {
                api(isMainnet).api.getBalance(PublicKey(addressBase58))
                    .getOrElse { throw it }
                    .let { lamports -> BigDecimal(lamports).movePointLeft(9) }
            }
        }

    /**
     * 转账 SOL（PRD §23 Send SOL）
     *
     * @param amountSol 人类可读 SOL 数量（如 "0.1"）
     * @return 交易签名 signature（等价 EVM txHash）
     */
    suspend fun sendSol(
        account: SolanaWalletFactory.SolanaAccount,
        toAddressBase58: String,
        amountSol: String,
        isMainnet: Boolean,
    ): ChainFlowResult<String> = withContext(Dispatchers.IO) {
        chainFlowRunCatching {
            val lamports = BigDecimal(amountSol).movePointRight(9).toLong()
            val solana = api(isMainnet)

            // 1. 最近区块哈希（Recent Blockhash：Solana 防重放，等价 EVM nonce）
            val blockhash = solana.api.getRecentBlockhash()
                .getOrElse { throw it }

            // 2. 构建交易：单个 SystemProgram.transfer 指令
            val tx = Transaction().apply {
                addInstruction(
                    SystemProgram.transfer(
                        fromPublicKey = account.account.publicKey, // Account 接口的 publicKey 属性
                        toPublickKey = PublicKey(toAddressBase58), // 库内拼写即 toPublickKey
                        lamports = lamports,
                    ),
                )
            }

            // 3. 签名 + 广播（sendTransaction 扩展自动签名并发送原始交易）
            solana.api.sendTransaction(tx, listOf(account.account), blockhash)
                .getOrElse { throw it }
        }
    }

    /**
     * 查询确认状态（PRD §23 交易状态）
     *
     * @return true = confirmed/finalized；false = processed（未完全确认）；null = 未上链
     */
    suspend fun isConfirmed(signature: String, isMainnet: Boolean): ChainFlowResult<Boolean?> =
        withContext(Dispatchers.IO) {
            chainFlowRunCatching {
                val status = api(isMainnet).api.getSignatureStatuses(listOf(signature))
                    .getOrElse { throw it }
                    .firstOrNull()
                when (status?.confirmationStatus) {
                    "confirmed", "finalized" -> true
                    null -> null // 交易尚未出现在链上（继续轮询）
                    else -> false // "processed"：已见未确认
                }
            }
        }
}
