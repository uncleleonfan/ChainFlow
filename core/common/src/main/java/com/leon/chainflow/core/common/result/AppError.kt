package com.leon.chainflow.core.common.result

/**
 * 应用统一错误模型
 *
 * 设计目标：把「网络异常 / RPC 错误 / 交易失败」等技术性异常，
 * 翻译成 UI 可直接展示的用户友好文案（对应 PRD §27 Error Handling）。
 *
 * 分层约定：
 * - data 层捕获底层异常 → 转换为 [AppError]；
 * - domain / UI 层只面对 [AppError]，通过 [messageToUser] 渲染提示。
 */
sealed interface AppError {

    /** 对用户展示的文案（中文，直接用于 Snackbar / Dialog） */
    val messageToUser: String

    /** 网络不可用 / 请求超时（PRD §27: "Unable to connect"） */
    data class Network(override val messageToUser: String = "网络连接失败，请检查网络后重试") : AppError

    /**
     * RPC 节点错误（PRD §27: "RPC temporarily unavailable"）
     * @param code RPC 错误码（JSON-RPC error.code），用于日志排查但不展示给用户
     */
    data class Rpc(val code: Int, override val messageToUser: String = "RPC 暂不可用，正在切换节点…") : AppError

    /** 交易上链失败（PRD §27: "Transaction failed"，例如 gas 不足） */
    data class TransactionFailed(val reason: String) : AppError {
        override val messageToUser: String = "交易失败：$reason"
    }

    /** 用户主动取消（拒绝签名 / 关闭生物识别）—— 不应视为错误，静默处理 */
    data object UserRejected : AppError {
        override val messageToUser: String = "交易已取消"
    }

    /** 余额不足（PRD §27 要求在构造交易前提前拦截，避免浪费 gas） */
    data object InsufficientBalance : AppError {
        override val messageToUser: String = "余额不足"
    }

    /** 钱包本地错误（解锁失败 / 密钥损坏等安全相关错误，详细信息只进日志） */
    data class Wallet(val detail: String) : AppError {
        override val messageToUser: String = "钱包操作失败"
    }

    /** 未归类错误（兜底） */
    data class Unknown(val detail: String) : AppError {
        override val messageToUser: String = "发生未知错误，请稍后重试"
    }
}
