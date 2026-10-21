package com.leon.chainflow.data.wallet

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.blockchain.core.model.NetworkEnvironment
import com.leon.chainflow.blockchain.core.model.TokenBalance
import com.leon.chainflow.blockchain.core.model.WalletInfo
import com.leon.chainflow.blockchain.evm.rpc.EvmRpcClient
import com.leon.chainflow.blockchain.evm.wallet.EvmWalletFactory
import com.leon.chainflow.blockchain.solana.SolanaService
import com.leon.chainflow.blockchain.solana.SolanaWalletFactory
import com.leon.chainflow.core.common.coroutines.DispatcherProvider
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.core.common.result.ChainFlowResult
import com.leon.chainflow.core.common.result.chainFlowRunCatching
import com.leon.chainflow.core.common.result.map
import com.leon.chainflow.core.security.EncryptedPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/** DataStore 实例（顶层扩展属性，官方推荐写法） */
private val Context.walletDataStore by preferencesDataStore(name = "wallet_prefs")

/**
 * 钱包仓库（PRD §12/§14 —— 创建 / 导入 / 恢复 / 查询）
 *
 * 数据职责划分：
 * - 助记词密文 → [EncryptedPrefs]（Keystore 加密，安全红线数据）
 * - 钱包地址 → [EncryptedPrefs]（公开信息）
 * - 网络偏好（链 / Testnet-Mainnet）→ DataStore（普通配置）
 *
 * 安全红线（PRD §13.1）：
 * - [unlock] 返回的 Credentials 只在内存中短暂使用（签名时），不缓存到任何字段
 * - 所有涉及助记词的路径使用 [com.leon.chainflow.core.security.SecurityGuard.logSecure]
 *   模式（不输出任何敏感日志）
 */
@Singleton
class WalletRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryptedPrefs: EncryptedPrefs,
    private val walletFactory: EvmWalletFactory,
    private val rpcClient: EvmRpcClient,
    private val solanaWalletFactory: SolanaWalletFactory, // SOL 地址派生（同助记词）
    private val solanaService: SolanaService,             // SOL 余额查询
    private val dispatchers: DispatcherProvider,
) {

    // ---- DataStore 键定义 ----
    private object PrefKeys {
        val SELECTED_CHAIN = stringPreferencesKey("selected_chain")
        val IS_MAINNET = booleanPreferencesKey("is_mainnet")
    }

    /**
     * 是否已有钱包（App 启动时决定展示「创建/导入引导」还是「钱包首页」）
     * 转为 Flow：未来支持外部导入触发 UI 自动刷新
     */
    fun hasWallet(): Flow<Boolean> = flowOf(encryptedPrefs.hasWallet())

    private fun <T> flowOf(value: T): Flow<T> = kotlinx.coroutines.flow.flow { emit(value) }

    /** 当前钱包地址（无钱包返回 null） */
    fun walletAddress(): String? = encryptedPrefs.loadAddress()

    /**
     * 创建新钱包（PRD §12.1）
     *
     * 流程：生成 12 词助记词 → 派生凭证 → 助记词加密落盘 + 地址明文落盘
     *
     * @param backupConfirmedByUser 用户已完成备份确认（UI 层勾选「我已安全备份」后才可调用）
     * @return 助记词（仅供本次展示，仓库不缓存）—— 调用方展示后应立即丢弃引用
     */
    suspend fun createWallet(backupConfirmedByUser: Boolean): ChainFlowResult<String> =
        withContext(dispatchers.default) { // 密码学计算是 CPU 密集型：Default 调度器
            chainFlowRunCatching {
                require(backupConfirmedByUser) { "必须先完成助记词备份确认" }
                require(!encryptedPrefs.hasWallet()) { "钱包已存在，如需重置请先删除现有钱包" }

                val newWallet = walletFactory.createNewWallet()
                // 安全顺序：先加密落盘，再返回明文（保证任何中断都不会丢失钱包）
                encryptedPrefs.saveMnemonic(newWallet.mnemonic)
                encryptedPrefs.saveAddress(newWallet.credentials.address)
                newWallet.mnemonic
            }
        }

    /**
     * 导入已有钱包（PRD §12.1 Import / Restore）
     *
     * @param mnemonic 12 词助记词
     * @return 派生出的钱包地址（用于 UI 展示确认）
     */
    suspend fun importWallet(mnemonic: String): ChainFlowResult<String> =
        withContext(dispatchers.default) {
            chainFlowRunCatching {
                val credentials = walletFactory.importFromMnemonic(mnemonic) // 校验失败抛异常
                encryptedPrefs.saveMnemonic(mnemonic.trim().lowercase())
                encryptedPrefs.saveAddress(credentials.address)
                credentials.address
            }
        }

    /**
     * 解锁钱包（签名 / 查看助记词等敏感操作前调用）
     *
     * 每次从加密存储重新解密派生 —— 刻意不做内存缓存：
     * 缩短私钥在内存中的存活窗口（威胁模型：内存 dump 攻击）
     */
    suspend fun unlock(): ChainFlowResult<Credentials> =
        withContext(dispatchers.default) {
            chainFlowRunCatching {
                val mnemonic = encryptedPrefs.loadMnemonic()
                    ?: throw IllegalStateException("尚未创建钱包")
                walletFactory.importFromMnemonic(mnemonic)
            }
        }

    /**
     * 查询原生币余额（PRD §14 Balance）
     *
     * @return 以 ETH 为单位的十进制余额
     */
    suspend fun getNativeBalance(): ChainFlowResult<BigDecimal> {
        val address = walletAddress()
            ?: return ChainFlowResult.failure(AppError.Wallet("no wallet"))
        val (chain, isMainnet) = currentNetwork()

        return rpcClient.getBalance(address, chain.rpcUrl(isMainnet))
            .map { wei -> BigDecimal(wei).movePointLeft(18) }
    }

    /**
     * 查询 ERC-20 余额（Phase 2 预留接口，Phase 3 Token 列表接入后使用）
     */
    suspend fun getErc20Balance(contract: String): ChainFlowResult<TokenBalance> {
        val address = walletAddress()
            ?: return ChainFlowResult.failure(AppError.Wallet("no wallet"))
        val (chain, isMainnet) = currentNetwork()
        // 实现留待 Phase 3（依赖 Erc20Client 注入），此处返回占位
        return ChainFlowResult.failure(AppError.Unknown("not implemented yet"))
    }

    /** 当前网络配置：用户选择的链 + 环境（默认 Base + Testnet，PRD §3/§32） */
    suspend fun currentNetwork(): Pair<Chain, Boolean> {
        val prefs = context.walletDataStore.data.first()
        val chain = prefs[PrefKeys.SELECTED_CHAIN]?.let { id ->
            Chain.entries.find { it.id == id }
        } ?: Chain.BASE // 默认 Base（PRD 指定主 Demo 链）
        val isMainnet = prefs[PrefKeys.IS_MAINNET] ?: false // 默认 Testnet
        return chain to isMainnet
    }

    /** 切换链（Settings 页调用） */
    suspend fun selectChain(chain: Chain) {
        context.walletDataStore.edit { it[PrefKeys.SELECTED_CHAIN] = chain.id }
    }

    /**
     * 切换网络环境（PRD §32：切换 Mainnet 必须先经过 UI 风险确认弹窗，
     * 仓库层只负责持久化，不重复拦截）
     */
    suspend fun setEnvironment(environment: NetworkEnvironment) {
        context.walletDataStore.edit { it[PrefKeys.IS_MAINNET] = environment == NetworkEnvironment.MAINNET }
    }

    /** 删除钱包（「忘记钱包」功能 —— UI 层必须二次确认） */
    fun deleteWallet() {
        encryptedPrefs.clearWallet()
        // 网络偏好保留（设备级配置，与钱包无关）
    }

    /**
     * Solana 地址（同助记词派生，Phase 5）：无钱包返回 null
     * —— 用于钱包总览展示 SOL 资产
     */
    suspend fun solanaAddress(): String? = withContext(dispatchers.default) {
        encryptedPrefs.loadMnemonic()?.let { solanaWalletFactory.fromMnemonic(it).publicKeyBase58 }
    }

    /** SOL 余额（PRD §23：SOL Balance） */
    suspend fun getSolBalance(): ChainFlowResult<BigDecimal> {
        val address = solanaAddress()
            ?: return ChainFlowResult.failure(AppError.Wallet("no wallet"))
        val (_, isMainnet) = currentNetwork()
        return solanaService.getBalance(address, isMainnet)
    }

    /** 钱包信息（地址 + 当前链） */
    suspend fun walletInfo(): WalletInfo? {
        val address = walletAddress() ?: return null
        val (chain, _) = currentNetwork()
        return WalletInfo(address = address, chain = chain)
    }
}
