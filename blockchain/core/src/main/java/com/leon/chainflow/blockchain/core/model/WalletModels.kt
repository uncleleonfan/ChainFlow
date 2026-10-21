package com.leon.chainflow.blockchain.core.model

import java.math.BigDecimal
import java.math.BigInteger

/**
 * 网络环境（PRD §32）
 * App 默认 TESTNET，切换 MAINNET 必须经过风险确认弹窗。
 */
enum class NetworkEnvironment { TESTNET, MAINNET }

/**
 * 钱包元数据（非机密部分，可持久化明文存储）
 *
 * @param address 钱包地址（公开信息）
 * @param chain 所属链（当前 Phase 2 固定 EVM，结构上预留多链）
 */
data class WalletInfo(
    val address: String,
    val chain: Chain,
)

/**
 * 链上余额快照
 *
 * @param raw 原始 wei 数量（BigInteger，避免精度丢失）
 * @param decimals 代币精度（ETH=18，USDC=6 等）
 */
data class TokenBalance(
    val raw: BigInteger,
    val decimals: Int,
) {
    /** 转换为人类可读的十进制表示（BigDecimal 保证精度） */
    fun toDecimal(): BigDecimal = BigDecimal(raw).movePointLeft(decimals)
}

/**
 * 交易回执状态（PRD §20 状态机子集 —— Phase 2 Send 流程使用）
 */
enum class TransactionStatus {
    /** 已构建未签名 */
    CREATED,

    /** 已签名待广播 */
    SIGNED,

    /** 已广播，等待矿工打包 */
    PENDING,

    /** 已上链确认 */
    CONFIRMED,

    /** 上链失败（revert / out of gas 等） */
    FAILED,
}

/**
 * 手续费估算结果（Send 页预览用，PRD §16）
 *
 * @param gasLimit 预估 gas 上限（wei 计价的单位数）
 * @param gasPrice 当前建议 gas 单价（ wei / gas 单位）
 * @param estimateEth 转换为 ETH 计价的预估总费用（展示用）
 */
data class FeeEstimate(
    val gasLimit: BigInteger,
    val gasPrice: BigInteger,
    val estimateEth: BigDecimal,
)
