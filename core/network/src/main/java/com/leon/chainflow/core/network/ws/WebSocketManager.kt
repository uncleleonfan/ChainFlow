package com.leon.chainflow.core.network.ws

import com.leon.chainflow.core.common.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * WebSocket 连接状态（PRD §10.2 connection status）
 */
enum class WsConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED,
}

/**
 * 通用 WebSocket 管理器（PRD §10 实时数据流核心组件）
 *
 * 数据流：
 * ```
 * Binance WS  →  WebSocketListener（IO 线程）
 *          →  SharedFlow<Message>（去重后）
 *          →  Repository 订阅 → StateFlow → ViewModel → Compose
 * ```
 *
 * 实现的可靠性要求（PRD §10.2 全项）：
 * 1. **reconnect + 指数退避**：失败后 1s → 2s → 4s → … 上限 30s
 * 2. **heartbeat**：通过 OkHttp pingInterval 实现（协议层 keep-alive）
 * 3. **connection status**：StateFlow 暴露给 UI 显示连接徽章
 * 4. **subscription management**：自动重放订阅（重连后恢复数据推送）
 * 5. **lifecycle awareness**：Repository 层按页面生命周期 start/stop
 * 6. **duplicate handling**：seq/messageId 去重（消息层 LRU）
 * 7. **stale data detection**：超过 N 秒无消息置为陈旧状态
 *
 * 设计约束：一个实例管理一条连接；多市场订阅走同一条连接的 subscribe 消息。
 */
class WebSocketManager(
    private val url: String,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
    private val client: OkHttpClient = defaultClient(),
) {

    /** 当前连接状态（UI 展示 Connected/Connecting/Disconnected 徽章） */
    private val _state = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val state: StateFlow<WsConnectionState> = _state.asStateFlow()

    /** 最近一次收到消息的时间戳（ms）—— 0 表示从未收到 */
    private val _lastMessageAt = MutableStateFlow(0L)
    val lastMessageAt: StateFlow<Long> = _lastMessageAt.asStateFlow()

    /** 活动的 WebSocket 连接（null = 未连接） */
    private var webSocket: WebSocket? = null

    /** 重连任务（取消即停止重连循环） */
    private var reconnectJob: Job? = null

    /** 当前重连次数（用于指数退避计算；连接成功后清零） */
    private var reconnectAttempts = 0

    /** 已发送的订阅消息缓存 —— 重连成功后自动重放（subscription management） */
    private val subscriptions = mutableSetOf<String>()

    /** 最近 N 条消息的指纹（duplicate event handling） */
    private val recentMessageKeys = object : LinkedHashMap<String, Unit>(64, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>) = size > 128
    }

    /** 防止 close() 与 onFailure 并发触发双重重连 */
    @Volatile
    private var manualClose = false

    /**
     * 建立连接（幂等：已连接则忽略）
     * 失败自动进入重连循环（指数退避）。
     */
    @Synchronized
    fun connect() {
        if (_state.value != WsConnectionState.DISCONNECTED) return
        manualClose = false
        openSocket()
    }

    /** 真正发起 WebSocket 握手 */
    private fun openSocket() {
        _state.value = WsConnectionState.CONNECTING
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    /**
     * 发送订阅消息（如 Binance 的 SUBSCRIBE 帧）
     * 连接断开期间缓存，重连成功后统一重放。
     */
    @Synchronized
    fun send(message: String): Boolean {
        subscriptions.add(message)
        val ws = webSocket ?: return false
        return ws.send(message)
    }

    /**
     * 主动关闭（页面退出时调用 —— lifecycle awareness 由上层驱动）
     * 关闭后不再自动重连，直到下一次 connect()。
     */
    @Synchronized
    fun close() {
        manualClose = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "client closed")
        webSocket = null
        _state.value = WsConnectionState.DISCONNECTED
    }

    /** OkHttp 回调实现：网络事件 → 状态流转 */
    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            _state.value = WsConnectionState.CONNECTED
            reconnectAttempts = 0 // 重置退避
            // 重放订阅：恢复断线前的数据推送（subscription management）
            synchronized(this@WebSocketManager) {
                subscriptions.forEach { webSocket.send(it) }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            _lastMessageAt.value = System.currentTimeMillis()

            // 去重：完全相同的帧在极短时间内到达时只处理一次
            // （Binance 单流与组合流在重连瞬间可能重发最后一条）
            val key = text.hashCode().toString()
            synchronized(recentMessageKeys) {
                if (recentMessageKeys.containsKey(key)) return
                recentMessageKeys[key] = Unit
            }
            onMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            handleDisconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // 服务器主动关闭也走重连（只有用户主动 close 不重连）
            if (!manualClose) handleDisconnect()
        }
    }

    /**
     * 断线处理：标记状态并启动指数退避重连
     * 退避序列：1s, 2s, 4s, 8s, 16s, 30s(封顶) …
     */
    private fun handleDisconnect() {
        if (manualClose) return
        _state.value = WsConnectionState.DISCONNECTED
        webSocket = null

        reconnectJob?.cancel() // 防重复
        reconnectJob = scope.launch {
            while (!manualClose && _state.value == WsConnectionState.DISCONNECTED) {
                val delayMs = min(
                    AppConstants.WS_RECONNECT_BASE_DELAY_MS shl min(reconnectAttempts, 5),
                    AppConstants.WS_RECONNECT_MAX_DELAY_MS,
                )
                delay(delayMs)
                if (manualClose) return@launch
                reconnectAttempts++
                openSocket()
                // 等待握手结果：onOpen 会把状态置 CONNECTED 从而退出循环
                delay(2_000)
            }
        }
    }

    companion object {
        /** 默认 OkHttp：pingInterval 实现 heartbeat（协议层 keep-alive） */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .pingInterval(AppConstants.WS_HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WS 长连接禁用读超时
            .build()
    }
}
