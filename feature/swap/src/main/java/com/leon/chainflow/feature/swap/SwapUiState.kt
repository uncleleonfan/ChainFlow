package com.leon.chainflow.feature.swap

import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.core.common.result.AppError
import com.leon.chainflow.data.transaction.SwapQuote

/**
 * Swap 页状态（PRD §17）
 */
data class SwapUiState(
    /** 支付金额输入（字符串，如 "0.1"） */
    val payAmount: String = "",
    /** 当前报价（输入停止防抖后自动请求） */
    val quote: SwapQuote? = null,
    /** 是否正在请求报价 */
    val isQuoting: Boolean = false,
    /** 滑点（bps；50 = 0.5%，PRD §17 Slippage） */
    val slippageBps: Int = 50,
    /**
     * 支付 Token 符号（默认 ETH；从 Token 详情页进入时由路由参数注入该币种）
     * 地址/精度由 [com.leon.chainflow.data.transaction.SwapRepository.findToken] 解析
     */
    val paySymbol: String = "ETH",
    /** 收到 Token 符号（默认 USDC；方向切换时与 [paySymbol] 互换） */
    val receiveSymbol: String = "USDC",
    /** 执行阶段（PRD §18 状态机：签名/广播中） */
    val isExecuting: Boolean = false,
    /** 错误 */
    val error: AppError? = null,
) : UiState {

    /** 展示用预估到手（quote 无时为 "—"） */
    val receiveAmount: String
        get() = quote?.toAmount?.toPlainString()?.take(10) ?: "—"

    /** 展示用汇率（1 ETH = X USDC） */
    val rateText: String
        get() = quote?.let { "1 ${it.fromSymbol} = ${it.rate.toPlainString()} ${it.toSymbol}" } ?: "—"

    /** 展示用 Gas 费 */
    val gasText: String get() = quote?.gasCostUsd?.let { "≈ $${it.toPlainString()}" } ?: "—"
}
