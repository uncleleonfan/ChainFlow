package com.leon.chainflow.core.common

/**
 * 全局常量定义
 *
 * 集中管理可配置常量，避免魔法数字/字符串散落在各业务模块。
 */
object AppConstants {

    /** WebSocket 重连初始延迟（毫秒）—— 指数退避起点（Phase 3 使用） */
    const val WS_RECONNECT_BASE_DELAY_MS = 1_000L

    /** WebSocket 重连最大延迟（毫秒）—— 指数退避上限，防止无限增长 */
    const val WS_RECONNECT_MAX_DELAY_MS = 30_000L

    /** WebSocket 心跳间隔（毫秒） */
    const val WS_HEARTBEAT_INTERVAL_MS = 30_000L

    /** RPC 请求超时（毫秒） */
    const val RPC_TIMEOUT_MS = 15_000L

    /** 交易回执轮询间隔（毫秒）—— TransactionMonitor 使用（Phase 4） */
    const val TX_POLL_INTERVAL_MS = 3_000L

    /** 交易回执轮询超时（毫秒）：超过该时长仍未确认则提示用户手动查看 */
    const val TX_POLL_TIMEOUT_MS = 120_000L
}
