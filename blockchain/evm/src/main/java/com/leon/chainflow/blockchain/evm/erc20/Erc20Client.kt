package com.leon.chainflow.blockchain.evm.erc20

import com.leon.chainflow.blockchain.evm.rpc.EvmRpcClient
import com.leon.chainflow.core.common.result.ChainFlowResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.web3j.abi.DefaultFunctionEncoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Function // web3j 6.0：Function 位于 datatypes 包
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.Utf8String
import org.web3j.utils.Numeric
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ERC-20 标准方法编码器（PRD §19）
 *
 * 实现最小 ABI 集：balanceOf / transfer / approve / allowance / decimals / symbol
 *
 * 实现方式：使用 web3j 的 Function 编码器生成 calldata，
 * 再经 [EvmRpcClient] 的 eth_call（读）/ 交易发送（写）执行 ——
 * 不引入运行时合约代理（Contract 类），减少反射开销与包体积。
 */
@Singleton
class Erc20Client @Inject constructor(
    private val rpcClient: EvmRpcClient,
) {

    /**
     * 查询 ERC-20 余额（eth_call，只读不上链）
     *
     * @param owner 持有人地址
     * @param contract 代币合约地址
     * @return 最小单位余额（需再除以 decimals 换算）
     */
    suspend fun balanceOf(
        owner: String,
        contract: String,
        rpcUrl: String,
    ): ChainFlowResult<BigInteger> {
        // ABI: function balanceOf(address) view returns (uint256)
        val function = Function(
            "balanceOf",
            listOf(Address(owner)),
            listOf(TypeReference.create(Uint256::class.java)),
        )
        val data = DefaultFunctionEncoder().encodeFunction(function)
        val result = ethCall(contract, data, rpcUrl)
        return if (result is ChainFlowResult.Success) {
            ChainFlowResult.success(Numeric.decodeQuantity(result.data.ifEmpty { "0x0" }))
        } else {
            result as ChainFlowResult.Failure
        }
    }

    /**
     * 编码 ERC-20 transfer calldata（供 EvmTransactionService 构建合约调用交易）
     *
     * 注意：transfer 是写操作，编码后需作为交易的 data 字段广播
     *
     * @param to 收款人地址
     * @param amount 最小单位金额（raw，未除 decimals）
     */
    fun encodeTransfer(to: String, amount: BigInteger): String {
        val function = Function(
            "transfer",
            listOf(Address(to), Uint256(amount)),
            listOf(TypeReference.create(org.web3j.abi.datatypes.Bool::class.java)), // returns (bool)
        )
        return DefaultFunctionEncoder().encodeFunction(function)
    }

    /**
     * 编码 ERC-20 approve calldata（Swap 授权流程使用，Phase 4）
     *
     * @param spender 被授权地址（如 DEX Router / Aggregator 合约）
     * @param amount 授权额度（常用 MaxUint256 表示无限授权，Phase 4 讨论精确额度）
     */
    fun encodeApprove(spender: String, amount: BigInteger): String {
        val function = Function(
            "approve",
            listOf(Address(spender), Uint256(amount)),
            listOf(TypeReference.create(org.web3j.abi.datatypes.Bool::class.java)),
        )
        return DefaultFunctionEncoder().encodeFunction(function)
    }

    /**
     * 查询授权额度 allowance（owner → spender，Phase 4 Swap 前置检查）
     */
    suspend fun allowance(
        owner: String,
        spender: String,
        contract: String,
        rpcUrl: String,
    ): ChainFlowResult<BigInteger> {
        val function = Function(
            "allowance",
            listOf(Address(owner), Address(spender)),
            listOf(TypeReference.create(Uint256::class.java)),
        )
        val result = ethCall(contract, DefaultFunctionEncoder().encodeFunction(function), rpcUrl)
        return if (result is ChainFlowResult.Success) {
            ChainFlowResult.success(Numeric.decodeQuantity(result.data.ifEmpty { "0x0" }))
        } else {
            result as ChainFlowResult.Failure
        }
    }

    /** 查询代币 decimals（用于 raw ↔ 人类可读的换算） */
    suspend fun decimals(contract: String, rpcUrl: String): ChainFlowResult<Int> {
        val function = Function("decimals", emptyList(), listOf(TypeReference.create(Uint256::class.java)))
        val result = ethCall(contract, DefaultFunctionEncoder().encodeFunction(function), rpcUrl)
        return if (result is ChainFlowResult.Success) {
            ChainFlowResult.success(Numeric.decodeQuantity(result.data.ifEmpty { "0x0" }).toInt())
        } else {
            result as ChainFlowResult.Failure
        }
    }

    /** 查询代币 symbol（如 "USDC"） */
    suspend fun symbol(contract: String, rpcUrl: String): ChainFlowResult<String> {
        val function = Function("symbol", emptyList(), listOf(TypeReference.create(Utf8String::class.java)))
        val result = ethCall(contract, DefaultFunctionEncoder().encodeFunction(function), rpcUrl)
        return if (result is ChainFlowResult.Success) {
            // Utf8String 返回值是 32 字节对齐的 ABI 编码，需按规则解码
            val hex = result.data.removePrefix("0x")
            val bytes = Numeric.hexStringToByteArray(hex)
            // 简易解码：跳过 32 字节 offset + 32 字节长度，取实际内容
            val offset = 32
            if (bytes.size > offset + 32) {
                val length = BigInteger(bytes.copyOfRange(offset, offset + 32)).toInt()
                ChainFlowResult.success(String(bytes, offset + 32, length, Charsets.UTF_8))
            } else {
                ChainFlowResult.success("")
            }
        } else {
            result as ChainFlowResult.Failure
        }
    }

    // ---- 内部：统一 eth_call 只读调用 ----

    private suspend fun ethCall(to: String, data: String, rpcUrl: String): ChainFlowResult<String> =
        withContext(Dispatchers.IO) {
            val params = JSONObject().apply {
                put("to", to)
                put("data", data)
            }
            rpcClient.ethCallJson(params, rpcUrl)
        }
}