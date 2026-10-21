package com.leon.chainflow.blockchain.evm.rpc

import com.leon.chainflow.core.common.AppConstants
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.core.common.result.ChainFlowResult
import com.leon.chainflow.core.common.result.chainFlowRunCatching
import com.leon.chainflow.core.common.result.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EVM JSON-RPC 客户端（PRD §21 RPC Layer）
 *
 * 实现的最小方法集（PRD 要求）：
 * - eth_chainId            链 ID 校验（防连错网）
 * - eth_getBalance         原生币余额
 * - eth_estimateGas        Gas 预估
 * - eth_getTransactionCount nonce 获取（防重放）
 * - eth_gasPrice           Gas 单价
 * - eth_sendRawTransaction 广播签名交易
 * - eth_getTransactionReceipt 交易回执（确认状态）
 *
 * 工程约束：
 * - RPC timeout：[AppConstants.RPC_TIMEOUT_MS]
 * - 轮询逻辑由上层（Phase 4 TransactionMonitor）负责，本类只做单次调用
 * - Failover / Rate limit 处理在 Phase 4 的 RpcManager 中增强
 */
@Singleton
class EvmRpcClient @Inject constructor() {

    /** 专用 OkHttp：独立超时配置，避免与行情 API 互相影响 */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(AppConstants.RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(AppConstants.RPC_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    /** JSON-RPC 请求 id 自增器（协议要求唯一即可） */
    private val requestId = java.util.concurrent.atomic.AtomicLong(1)

    /**
     * 查询原生币（ETH/BNB）余额
     *
     * @param address 0x 开头的钱包地址
     * @param rpcUrl 当前网络 RPC 端点
     * @return wei 计价的余额（BigInteger）
     */
    suspend fun getBalance(address: String, rpcUrl: String): ChainFlowResult<BigInteger> =
        rpc(rpcUrl, "eth_getBalance", address, "latest")
            .map { hexToBigInteger(it) }

    /** 查询链 ID（连接时校验，防止 Testnet/Mainnet 混淆） */
    suspend fun getChainId(rpcUrl: String): ChainFlowResult<Long> =
        rpc(rpcUrl, "eth_chainId").map { hexToBigInteger(it).toLong() }

    /** 获取交易计数 nonce（构建交易的必要参数，防重放攻击） */
    suspend fun getTransactionCount(address: String, rpcUrl: String): ChainFlowResult<BigInteger> =
        rpc(rpcUrl, "eth_getTransactionCount", address, "pending") // pending：包含内存池未打包交易
            .map { hexToBigInteger(it) }

    /** 获取当前 gas 单价（wei） */
    suspend fun getGasPrice(rpcUrl: String): ChainFlowResult<BigInteger> =
        rpc(rpcUrl, "eth_gasPrice").map { hexToBigInteger(it) }

    /**
     * 预估交易 gas 消耗
     *
     * @param to 目标合约地址（或 null 表示纯转账）
     * @param data 合约调用数据（纯转账传 "0x"）
     * @param value 转账金额（wei），可空
     */
    suspend fun estimateGas(
        from: String,
        to: String?,
        data: String,
        value: BigInteger?,
        rpcUrl: String,
    ): ChainFlowResult<BigInteger> {
        val params = JSONObject().apply {
            put("from", from)
            if (to != null) put("to", to)
            put("data", data)
            if (value != null) put("value", "0x${value.toString(16)}")
        }
        return rpcObject(rpcUrl, "eth_estimateGas", params).map { hexToBigInteger(it) }
    }

    /** 广播已签名的原始交易（返回 txHash） */
    suspend fun sendRawTransaction(signedTxHex: String, rpcUrl: String): ChainFlowResult<String> =
        rpc(rpcUrl, "eth_sendRawTransaction", signedTxHex)

    /**
     * 查询交易回执
     *
     * @return null = 尚未打包（Pending）；非 null = 已上链（status 字段 0x1 成功 / 0x0 失败）
     */
    suspend fun getTransactionReceipt(
        txHash: String,
        rpcUrl: String,
    ): ChainFlowResult<JSONObject?> = rpcOptional(rpcUrl, "eth_getTransactionReceipt", txHash)

    /**
     * 组合查询：一笔转账的完整手续费预估（gasLimit × gasPrice → ETH 计价）
     * 返回 [FeeEstimate] 供 Send 页预览展示（PRD §16 Network Fee）。
     */
    suspend fun estimateTransferFee(
        from: String,
        to: String,
        rpcUrl: String,
    ): ChainFlowResult<com.leon.chainflow.blockchain.core.model.FeeEstimate> {
        val gasPrice = getGasPrice(rpcUrl)
        val gasLimit = estimateGas(from, to, "0x", null, rpcUrl)
        return if (gasPrice is ChainFlowResult.Success && gasLimit is ChainFlowResult.Success) {
            val feeWei = gasPrice.data * gasLimit.data
            ChainFlowResult.success(
                com.leon.chainflow.blockchain.core.model.FeeEstimate(
                    gasLimit = gasLimit.data,
                    gasPrice = gasPrice.data,
                    estimateEth = BigDecimal(feeWei).movePointLeft(18), // wei → ETH
                ),
            )
        } else {
            (gasPrice as? ChainFlowResult.Failure) ?: (gasLimit as ChainFlowResult.Failure)
        }
    }

    // ================= 内部实现 =================

    /**
     * 发送 JSON-RPC 请求（参数为可变 String 列表，自动包装为数组）
     * IO 线程执行网络，返回 result 字段（String 类型）。
     */
    private suspend fun rpc(rpcUrl: String, method: String, vararg params: String): ChainFlowResult<String> =
        withContext(Dispatchers.IO) {
            chainFlowRunCatching {
                val jsonArray = org.json.JSONArray().apply { params.forEach { put(it) } }
                val body = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", requestId.getAndIncrement())
                    .put("method", method)
                    .put("params", jsonArray)
                    .toString()

                val response = httpClient.newCall(
                    Request.Builder()
                        .url(rpcUrl)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build(),
                ).execute()

                response.use { resp ->
                    val text = resp.body?.string().orEmpty()
                    val json = JSONObject(text)

                    // JSON-RPC 错误对象：转换为 AppError.Rpc（保留 code 便于日志排查）
                    json.optJSONObject("error")?.let { err ->
                        throw IllegalStateException(err.optString("message", "RPC error"))
                    }
                    json.optString("result")
                }
            }
        }

    /** 参数为 JSONObject（eth_estimateGas 的对象参数场景） */
    private suspend fun rpcObject(rpcUrl: String, method: String, param: JSONObject): ChainFlowResult<String> =
        withContext(Dispatchers.IO) {
            chainFlowRunCatching {
                val params = org.json.JSONArray().put(param)
                val body = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", requestId.getAndIncrement())
                    .put("method", method)
                    .put("params", params)
                    .toString()
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(rpcUrl)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build(),
                ).execute()
                response.use { JSONObject(it.body?.string().orEmpty()).optString("result") }
            }
        }

    /**
     * eth_call：只读合约调用（ERC-20 balanceOf / decimals / symbol 等，PRD §19）
     *
     * @param callObject {to, data} 标准调用对象
     * @return ABI 编码的返回值（0x...），由上层按 ABI 规则解码
     */
    suspend fun ethCallJson(callObject: JSONObject, rpcUrl: String): ChainFlowResult<String> =
        withContext(Dispatchers.IO) {
            chainFlowRunCatching {
                val params = org.json.JSONArray()
                    .put(callObject)
                    .put("latest") // 最新区块状态执行
                val body = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", requestId.getAndIncrement())
                    .put("method", "eth_call")
                    .put("params", params)
                    .toString()
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(rpcUrl)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build(),
                ).execute()
                response.use {
                    val json = JSONObject(it.body?.string().orEmpty())
                    json.optJSONObject("error")?.let { err ->
                        throw IllegalStateException(err.optString("message", "RPC error"))
                    }
                    json.optString("result")
                }
            }
        }

    /** result 可能为 null 的场景（如 getTransactionReceipt 未打包时返回 null） */
    private suspend fun rpcOptional(rpcUrl: String, method: String, param: String): ChainFlowResult<JSONObject?> =
        withContext(Dispatchers.IO) {
            chainFlowRunCatching {
                val params = org.json.JSONArray().put(param)
                val body = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", requestId.getAndIncrement())
                    .put("method", method)
                    .put("params", params)
                    .toString()
                val response = httpClient.newCall(
                    Request.Builder()
                        .url(rpcUrl)
                        .post(body.toRequestBody("application/json".toMediaType()))
                        .build(),
                ).execute()
                response.use {
                    JSONObject(it.body?.string().orEmpty()).optJSONObject("result")
                }
            }
        }

    /** "0x..." 十六进制 → BigInteger（JSON-RPC 的数值格式约定） */
    private fun hexToBigInteger(hex: String): BigInteger =
        BigInteger(hex.removePrefix("0x"), 16)
}
