package com.leon.chainflow.core.network.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Li.Fi Swap 聚合器 API（https://li.quest，PRD §18 —— MVP 不自建 DEX）
 *
 * 实测（2025-10）：国内直连可用、免 API Key（公共限额即可满足 Demo）。
 *
 * Swap 流程（PRD §18）：
 * ```
 * quote（估价）→ 用户确认 → step transaction（预构建交易）→ 本地签名 → 广播 → 轮询 status
 * ```
 */
interface LiFiApi {

    /**
     * 获取报价（PRD §17：Rate / Price Impact / 预估到手数量）
     *
     * @param fromChain / toChain EVM chainId（Base=8453）
     * @param fromToken / toToken 合约地址（原生币用 0x000...000）
     * @param fromAmount 最小单位数量（wei，注意 decimals）
     * @param fromAddress 用户地址（影响路由可用性）
     */
    @GET("quote")
    suspend fun getQuote(
        @Query("fromChain") fromChain: Long,
        @Query("toChain") toChain: Long,
        @Query("fromToken") fromToken: String,
        @Query("toToken") toToken: String,
        @Query("fromAmount") fromAmount: String,
        @Query("fromAddress") fromAddress: String,
        /** 滑点容忍（bps，默认 50 = 0.5%） */
        @Query("slippage") slippage: Double = 50.0,
        /** 允许中间步骤的额外校验（integrator 免 Key 场景保持默认） */
        @Query("allowBridges") allowBridges: String? = null,
    ): LiFiQuoteDto

    /**
     * 获取预构建交易（PRD §18 Build Transaction）
     *
     * 返回的 transactionRequest 可直接本地签名（EIP-1559 或 Legacy），
     * 我们只负责签名 + 广播 —— 私钥不进入 Li.Fi（PRD §13.1 红线）。
     */
    @GET("step/transaction")
    suspend fun getStepTransaction(
        /** quote 返回的完整 step 对象（需整体回传，含路由信息） */
        @Query("step") step: String,
    ): LiFiStepDto

    /**
     * 查询交易状态（PRD §20 TransactionMonitor 的聚合器侧补充）
     *
     * @param txHash 广播后的交易哈希
     */
    @GET("status") suspend fun getStatus(
        @Query("txHash") txHash: String,
        @Query("bridge") bridge: String? = null,
        @Query("fromChain") fromChain: Long,
        @Query("toChain") toChain: Long,
    ): LiFiStatusDto

    /** 查询链上 Token 元数据（Swap 页 Token 选择器数据源） */
    @GET("token")
    suspend fun getToken(
        @Query("chain") chain: Long,
        @Query("token") token: String,
    ): LiFiTokenDto

    /** 查询链上 Token 余额（Swap 页展示可用余额） */
    @GET("balance")
    suspend fun getBalance(
        @Query("chain") chain: Long,
        @Query("token") token: String,
        @Query("wallet") wallet: String,
    ): LiFiBalanceDto
}

// ================= DTO（只声明使用到的字段，ignoreUnknownKeys 兜底） =================

/** Token 元数据（Li.Fi 全链统一格式） */
@Serializable
data class LiFiTokenDto(
    val address: String,
    val chainId: Long,
    val symbol: String,
    val name: String? = null,
    val decimals: Int,
    @SerialName("priceUSD") val priceUsd: String? = null,
    @SerialName("coinKey") val coinKey: String? = null,
)

/** Token 余额 */
@Serializable
data class LiFiBalanceDto(
    @SerialName("amount") val amountRaw: String,
    @SerialName("token") val token: LiFiTokenDto? = null,
)

/** 报价：estimate 中含预估到手与费率信息 */
@Serializable
data class LiFiQuoteDto(
    /** 工具（DEX/桥）名称 —— Swap 页展示路由来源 */
    @SerialName("toolDetails") val toolDetails: LiFiToolDetails? = null,
    val action: LiFiAction? = null,
    val estimate: LiFiEstimate? = null,
)

@Serializable
data class LiFiToolDetails(
    val key: String? = null,
    val name: String? = null,
)

/** 交易动作（from/to Token 与金额） */
@Serializable
data class LiFiAction(
    @SerialName("fromToken") val fromToken: LiFiTokenDto? = null,
    @SerialName("toToken") val toToken: LiFiTokenDto? = null,
    @SerialName("fromAmount") val fromAmountRaw: String? = null,
    @SerialName("toAmount") val toAmountRaw: String? = null,
)

/** 估价详情（PRD §17：Rate / Price Impact / 手续费） */
@Serializable
data class LiFiEstimate(
    @SerialName("toAmount") val toAmountRaw: String? = null,
    @SerialName("fromAmount") val fromAmountRaw: String? = null,
    @SerialName("gasCosts") val gasCosts: List<LiFiGasCost>? = null,
    @SerialName("feeCosts") val feeCosts: List<LiFiFeeCost>? = null,
)

@Serializable
data class LiFiGasCost(
    val type: String? = null,
    val amount: String? = null,       // 原生币最小单位
    @SerialName("amountUSD") val amountUsd: String? = null,
    val token: LiFiTokenDto? = null,
)

@Serializable
data class LiFiFeeCost(
    val type: String? = null,
    val amount: String? = null,
    @SerialName("amountUSD") val amountUsd: String? = null,
)

/** 预构建交易（step/transaction 返回） */
@Serializable
data class LiFiStepDto(
    val id: String? = null,
    val type: String? = null,
    val tool: String? = null,
    val action: LiFiAction? = null,
    val estimate: LiFiEstimate? = null,
    /** 关键：可直接签名的交易请求 */
    @SerialName("transactionRequest") val transactionRequest: LiFiTransactionRequest? = null,
)

/** EVM 交易请求（Legacy 与 EIP-1559 字段都兼容） */
@Serializable
data class LiFiTransactionRequest(
    val from: String? = null,
    val to: String? = null,
    val data: String? = null,
    val value: String? = null,      // wei（十六进制或十进制字符串）
    @SerialName("gasLimit") val gasLimit: String? = null,
    @SerialName("gasPrice") val gasPrice: String? = null,       // Legacy
    @SerialName("maxFeePerGas") val maxFeePerGas: String? = null, // EIP-1559
    @SerialName("maxPriorityFeePerGas") val maxPriorityFeePerGas: String? = null,
)

/** 交易状态 */
@Serializable
data class LiFiStatusDto(
    val status: String? = null, // DONE / FAILED / PENDING / NOT_FOUND
    @SerialName("substatuses") val subStatuses: List<String>? = null,
    @SerialName("receiving") val receiving: LiFiTokenAmount? = null,
    @SerialName("sending") val sending: LiFiTokenAmount? = null,
)

@Serializable
data class LiFiTokenAmount(
    @SerialName("amount") val amountRaw: String? = null,
    val token: LiFiTokenDto? = null,
)
