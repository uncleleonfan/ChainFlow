package com.leon.chainflow.blockchain.solana

/**
 * Solana 网络配置（PRD §23）
 *
 * RPC 选型（2025-10 国内直连实测）：
 * - api.devnet/mainnet-beta.solana.com：直连超时（需代理）
 * - solana-rpc.publicnode.com：✅ 直连可用（Mainnet）
 * - solana-testnet-rpc.publicnode.com：✅ 直连可用（Testnet/Devnet 语义）
 *
 * 注意：publicnode 的 testnet 端点实际为 Solana Testnet（与 Devnet 是不同集群，
 * 但 faucet 生态相近）；MVP 统一走该端点作「测试环境」。
 */
object SolanaNetwork {

    /** 公共 RPC（免 Key 直连） */
    const val RPC_MAINNET = "https://solana-rpc.publicnode.com"

    /** 测试网 RPC */
    const val RPC_TESTNET = "https://solana-testnet-rpc.publicnode.com"

    /** 交易浏览器（PRD §22 跳转用，%s = signature） */
    const val EXPLORER_TX = "https://explorer.solana.com/tx/%s"

    /** 按环境取 RPC */
    fun rpcUrl(isMainnet: Boolean): String = if (isMainnet) RPC_MAINNET else RPC_TESTNET
}
