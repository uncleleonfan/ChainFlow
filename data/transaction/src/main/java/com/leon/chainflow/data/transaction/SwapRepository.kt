package com.leon.chainflow.data.transaction

import com.leon.chainflow.core.common.coroutines.DispatcherProvider
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.core.common.result.ChainFlowResult
import com.leon.chainflow.core.common.result.chainFlowRunCatching
import com.leon.chainflow.core.common.result.onFailure
import com.leon.chainflow.core.network.api.LiFiApi
import com.leon.chainflow.core.network.api.LiFiQuoteDto
import com.leon.chainflow.core.network.api.LiFiStepDto
import com.leon.chainflow.data.wallet.WalletRepository
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Swap 报价模型（UI 展示用，已从 Li.Fi DTO 映射为人类可读数值）
 */
data class SwapQuote(
    val fromSymbol: String,
    val toSymbol: String,
    /** 支付数量（人类可读十进制） */
    val fromAmount: BigDecimal,
    /** 预估到手数量 */
    val toAmount: BigDecimal,
    /** 汇率（1 fromToken = X toToken） */
    val rate: BigDecimal,
    /** 路由工具名（如 Nordstern Finance） */
    val toolName: String,
    /** Gas 费用（USD 估值） */
    val gasCostUsd: BigDecimal,
    /** 原始报价（换取预构建交易时整体回传 Li.Fi） */
    val raw: LiFiQuoteDto,
)

/**
 * 单链 Swap 可交易 Token（symbol → 链上地址 + 精度）
 *
 * @param symbol 展示符号（如 ETH / USDC）
 * @param address 合约地址（原生币为 0x000…000）
 * @param decimals 最小单位精度（换算 fromAmount 用，USDC=6 不能按 18 算）
 */
data class SwapToken(
    val symbol: String,
    val address: String,
    val decimals: Int,
)

/**
 * Swap 仓库（PRD §17-18 流程编排）
 *
 * 完整链路：
 * ```
 * getQuote（估价）
 *   → UI 展示 Rate/Impact/Fee，用户确认
 * → getStepTransaction（Li.Fi 预构建交易）
 *   → 上层（SwapViewModel）调用 EvmTransactionService.sendPrebuiltTransaction
 *     本地签名 + 广播（私钥不进入本模块）
 * → monitorTransaction（RPC 轮询回执）
 *   → getStatus（Li.Fi 侧状态补充，可选）
 * ```
 */
@Singleton
class SwapRepository @Inject constructor(
    private val liFiApi: LiFiApi,
    private val walletRepository: WalletRepository,
    private val json: kotlinx.serialization.json.Json, // 注入统一解析器（NetworkModule 提供）
    private val dispatchers: DispatcherProvider,
) {

    /**
     * Swap 支持的 Token 表
     * 0x000...000 = 原生币；USDC 地址随网络环境区分（Testnet 与 Mainnet 合约不同！）
     */
    companion object {
        const val NATIVE_TOKEN = "0x0000000000000000000000000000000000000000"

        /** Base Mainnet USDC（官方原生合约） */
        const val BASE_USDC_MAINNET = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"

        /** Base Sepolia Testnet USDC */
        const val BASE_USDC_TESTNET = "0x036CbD53842c5426634e7929541eC2318f3dCF7e"

        /** Base 预部署 WETH（主网与 Sepolia 同址） */
        const val BASE_WETH = "0x4200000000000000000000000000000000000006"

        /** 原生币 ETH（默认支付 Token） */
        val NATIVE_ETH = SwapToken(symbol = "ETH", address = NATIVE_TOKEN, decimals = 18)

        /** Mainnet 支持的 Token（MVP 白名单：Base 上流动性可用的主流币） */
        private val MAINNET_TOKENS = listOf(
            NATIVE_ETH,
            SwapToken("WETH", BASE_WETH, 18),
            SwapToken("USDC", BASE_USDC_MAINNET, 6),
            SwapToken("DAI", "0x50c5725949A6F0c72E6C4a641F24049A917DB0Cb", 18),
            SwapToken("cbBTC", "0xcbB7C0000aB88B473b1f5aFd9ef808440eed33Bf", 8),
            SwapToken("AERO", "0x940181a94A35A4569E4529A3CDfB74e38FD98631", 18),
        )

        /** Testnet 支持的 Token（Base Sepolia 上仅有少量代币有流动性） */
        private val TESTNET_TOKENS = listOf(
            NATIVE_ETH,
            SwapToken("WETH", BASE_WETH, 18),
            SwapToken("USDC", BASE_USDC_TESTNET, 6),
        )

        /**
         * 按符号解析 Token（大小写不敏感）
         *
         * @return null = 当前网络不支持该 Token（行情列表里的 CEX 币大多不在 Base 链上）
         */
        fun findToken(symbol: String, isMainnet: Boolean): SwapToken? =
            (if (isMainnet) MAINNET_TOKENS else TESTNET_TOKENS)
                .firstOrNull { it.symbol.equals(symbol.trim(), ignoreCase = true) }

        /** 按网络环境取 USDC 地址 */
        fun usdcAddress(isMainnet: Boolean): String =
            if (isMainnet) BASE_USDC_MAINNET else BASE_USDC_TESTNET
    }

    /**
     * 获取报价（PRD §17 UI 数据）
     *
     * @param fromAmountHuman 人类可读支付数量（如 "0.1" ETH）
     * @param slippageBps 滑点（bps：50 = 0.5%；内部转换为 Li.Fi 的小数比例格式）
     */
    suspend fun getQuote(
        fromToken: SwapToken,
        toToken: SwapToken,
        fromAmountHuman: String,
        slippageBps: Double = 50.0,
    ): ChainFlowResult<SwapQuote> {
        val address = walletRepository.walletAddress()
            ?: return ChainFlowResult.failure(AppError.Wallet("no wallet"))
        val (chain, isMainnet) = walletRepository.currentNetwork()

        return withContext(dispatchers.io) {
            chainFlowRunCatching {
                val amount = BigDecimal(fromAmountHuman)
                // 精度随 Token 变化（USDC/cbBTC 不是 18 位，写死 18 会导致报价金额错位）
                val fromAmountRaw = amount.movePointRight(fromToken.decimals).toBigInteger()

                val dto = liFiApi.getQuote(
                    fromChain = chain.activeChainId(isMainnet),
                    toChain = chain.activeChainId(isMainnet),
                    fromToken = fromToken.address,
                    toToken = toToken.address,
                    fromAmount = fromAmountRaw.toString(),
                    fromAddress = address,
                    // Li.Fi 协议：slippage 为小数比例（0.005 = 0.5%），上限 1 —— 不是 bps！
                    slippage = slippageBps / 10_000.0,
                )
                dto.toSwapQuote(fromAmountHuman)
            }.onFailure { error ->
                android.util.Log.w("ChainFlowSwap", "quote failed: $error") // 仅错误信息，不含任何敏感数据
                // Li.Fi 404 = 无可用路由；Testnet 上几乎必然（测试网 DEX 流动性枯竭）。
                // 转译为可操作的提示：引导用户切 Mainnet（PRD §32）或换金额。
                if (error is AppError.Unknown && "404" in error.detail) {
                    if (!isMainnet) {
                        return@withContext ChainFlowResult.failure(
                            AppError.Network("Testnet 流动性不足，无法获取报价\n请在设置中切换 Mainnet 后重试"),
                        )
                    }
                    return@withContext ChainFlowResult.failure(
                        AppError.Network("暂无可用路由，请调整金额后重试"),
                    )
                }
            }
        }
    }

    /**
     * 获取预构建交易（用户确认报价后调用）
     *
     * @param quote [getQuote] 返回的报价对象（内部携带原始 DTO）
     * @return 预构建交易请求（含 to/data/gas/1559 费率），供本地签名
     */
    suspend fun getStepTransaction(quote: SwapQuote): ChainFlowResult<LiFiStepDto> {
        val address = walletRepository.walletAddress()
            ?: return ChainFlowResult.failure(AppError.Wallet("no wallet"))
        val (chain, _) = walletRepository.currentNetwork() // step/transaction 不需要 chainId（step 内已含）

        return withContext(dispatchers.io) {
            chainFlowRunCatching {
                // quote DTO 补充 fromAddress 后整体序列化回传（Li.Fi step 协议要求）
                // 注意用 StringFormat 成员函数形式（serializer 显式传递）
                val stepJson = kotlinx.serialization.json.Json.Default.encodeToString(
                    JsonObject.serializer(),
                    encodeStepWithAddress(quote.raw, address),
                )
                liFiApi.getStepTransaction(step = stepJson)
            }
        }
    }

    /** Li.Fi 状态查询（广播后聚合器侧补充；RPC 回执为主 PRD §20） */
    suspend fun getStatus(txHash: String): ChainFlowResult<String?> {
        val (chain, isMainnet) = walletRepository.currentNetwork()
        return withContext(dispatchers.io) {
            chainFlowRunCatching {
                liFiApi.getStatus(
                    txHash = txHash,
                    fromChain = chain.activeChainId(isMainnet),
                    toChain = chain.activeChainId(isMainnet),
                ).status
            }
        }
    }

    // ================= 映射辅助 =================

    /** Li.Fi DTO → UI 报价模型（金额按各自 decimals 换算为十进制） */
    private fun LiFiQuoteDto.toSwapQuote(fromAmountHuman: String): SwapQuote {
        val from = action?.fromToken ?: error("missing fromToken")
        val to = action?.toToken ?: error("missing toToken")
        val toAmountRaw = estimate?.toAmountRaw ?: error("missing toAmount")
        val fromDecimals = from.decimals
        val toDecimals = to.decimals

        val toAmount = BigDecimal(toAmountRaw).movePointLeft(toDecimals)
        val fromAmount = BigDecimal(fromAmountHuman)
        // 汇率 = toAmount / fromAmount（fromAmount>0 由 UI 校验保证）
        val rate = if (fromAmount > BigDecimal.ZERO) toAmount.divide(fromAmount, 8, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO

        return SwapQuote(
            fromSymbol = from.symbol,
            toSymbol = to.symbol,
            fromAmount = fromAmount,
            toAmount = toAmount,
            rate = rate,
            toolName = toolDetails?.name ?: toolDetails?.key ?: "Unknown DEX",
            gasCostUsd = BigDecimal(
                estimate?.gasCosts?.firstOrNull()?.amountUsd ?: "0",
            ),
            raw = this,
        )
    }

    /** quote DTO 注入 fromAddress 后转为 JsonObject（step/transaction 参数格式） */
    private fun encodeStepWithAddress(
        quote: LiFiQuoteDto,
        address: String,
    ): kotlinx.serialization.json.JsonObject {
        val element = Json.Default.parseToJsonElement(
            Json.Default.encodeToString(LiFiQuoteDto.serializer(), quote),
        ) as JsonObject

        // action 补充 fromAddress / toAddress（单链 Swap 收款人即自己）
        val action = (element["action"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        action["fromAddress"] = JsonPrimitive(address)
        action["toAddress"] = JsonPrimitive(address)

        return JsonObject(
            element.toMutableMap().apply { put("action", JsonObject(action)) },
        )
    }
}
