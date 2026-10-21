package com.leon.chainflow.blockchain.core.model

/**
 * 支持的区块链网络定义（PRD §3）
 *
 * Phase 2 支持 EVM 三链（Ethereum / Base / BSC），Base 为主要 Demo 链；
 * Testnet 为默认环境（PRD §32），切换 Mainnet 需用户显式确认风险。
 *
 * @param id 网络唯一标识（持久化用，稳定不变）
 * @param displayName 展示名称（避开 Enum.name）
 * @param chainId Mainnet chainId（EIP-155 签名与 Li.Fi 路由用）
 * @param testnetChainId Testnet chainId（同上，Testnet 环境）
 * @param nativeSymbol 原生代币符号（用于支付 Gas）
 * @param rpcUrlTestnet / rpcUrlMainnet 公共 RPC 端点（禁止硬编码任何私密 Key）
 * @param explorerTx 交易浏览器 URL 模板（%s 替换为 txHash，PRD §22 跳转用）
 */
enum class Chain(
    val id: String,
    val displayName: String, // 避开 Enum.name —— 展示用名称
    val chainId: Long,
    val testnetChainId: Long,
    val nativeSymbol: String,
    val rpcUrlTestnet: String,
    val rpcUrlMainnet: String,
    val explorerTx: String,
) {
    ETHEREUM(
        id = "ethereum",
        displayName = "Ethereum",
        chainId = 1L,
        testnetChainId = 11155111L, // Sepolia
        nativeSymbol = "ETH",
        rpcUrlTestnet = "https://ethereum-sepolia-rpc.publicnode.com", // Sepolia Testnet
        rpcUrlMainnet = "https://eth.llamarpc.com",
        explorerTx = "https://etherscan.io/tx/%s",
    ),
    BASE(
        id = "base",
        displayName = "Base",
        chainId = 8453L,
        testnetChainId = 84532L, // Base Sepolia
        nativeSymbol = "ETH", // Base 沿用 ETH 作为原生 Gas 代币
        rpcUrlTestnet = "https://sepolia.base.org", // Base Sepolia（PRD 指定的 Demo 链 Testnet）
        rpcUrlMainnet = "https://mainnet.base.org",
        explorerTx = "https://basescan.org/tx/%s",
    ),
    BSC(
        id = "bsc",
        displayName = "BSC",
        chainId = 56L,
        testnetChainId = 97L, // BSC Testnet
        nativeSymbol = "BNB",
        rpcUrlTestnet = "https://bsc-testnet-rpc.publicnode.com",
        rpcUrlMainnet = "https://bsc-dataseed.binance.org",
        explorerTx = "https://bscscan.com/tx/%s",
    );

    /** 根据当前网络环境返回对应 RPC 地址（PRD §32：默认 Testnet） */
    fun rpcUrl(isMainnet: Boolean): String =
        if (isMainnet) rpcUrlMainnet else rpcUrlTestnet

    /** 根据环境返回 chainId（EIP-155 签名与 Li.Fi 路由必一致，否则签名校验失败） */
    fun activeChainId(isMainnet: Boolean): Long =
        if (isMainnet) chainId else testnetChainId
}

/** 临时占位 —— enum 构造参数不支持函数调用糖，见下方修正版 */
private fun nativeTokenSymbol(): String = "ETH"
