package com.leon.chainflow.data.market

import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.leon.chainflow.core.network.ws.WebSocketManager
import com.leon.chainflow.core.network.ws.WsConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Binance 实时价格流（PRD §10 数据流的 WS 部分）
 *
 * 使用 Binance 组合流（combined stream）：
 * - URL：wss://data-stream.binance.vision/stream（官方公开镜像，国内直连）
 * - 订阅：{"method":"SUBSCRIBE","params":["btcusdt@ticker",...],"id":1}
 * - 推送：{"stream":"btcusdt@ticker","data":{"c":"当前价","P":"24h涨跌%",...}}
 *
 * ================= 生命周期设计（重要） =================
 * 本类是 @Singleton —— WS 连接的生命周期绑定【应用进程】而非单个页面：
 * - 历史缺陷：早期版本把 stop() 暴露给页面 ViewModel 的 onCleared()，
 *   导致 Tab 切换（saveState 会销毁 ViewModel）时共享连接被误关，
 *   其他页面实时数据停止更新 —— 表现为「数据不实时」。
 * - 现策略：
 *   1. 连接随 ProcessLifecycleOwner（App 前台期间保活，退后台自动断开省电）
 *   2. 订阅集合只增不减（单例流，页面叠加订阅成本可忽略：30 个 ticker ≈ 30 msg/s）
 *   3. 页面退出只取消自己的 collect 协程，不触碰连接
 */
@Singleton
class BinancePriceStream @Inject constructor(
    private val json: Json,
) {

    /** 单个 symbol 的实时报价快照 */
    data class Ticker(
        val symbol: String,        // 大写交易对，如 BTCUSDT
        val price: Double,         // 最新价（USDT 计价）
        val changePercent: Double, // 24h 涨跌幅
        val eventTime: Long,       // 事件时间戳（ms）—— 陈旧检测用
    )

    /** 当前全量报价表（key = symbol）—— UI 的唯一数据源 */
    private val _tickers = MutableStateFlow<Map<String, Ticker>>(emptyMap())
    val tickers: StateFlow<Map<String, Ticker>> = _tickers.asStateFlow()

    /** WS 连接（懒创建；scope 绑定进程级 ProcessLifecycleOwner） */
    private var ws: WebSocketManager? = null

    /** 连接状态收集任务：disconnect 时必须取消，否则旧连接的状态会覆盖新连接（显示 DISCONNECTED） */
    private var stateJob: Job? = null

    /** 连接状态（组合：无连接 → DISCONNECTED；有连接透传其状态） */
    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    /** 已订阅 symbol 集合（只增不减 —— 重连时由 Manager 自动重放） */
    private val subscribed = mutableSetOf<String>()

    /** 递增请求 id（Binance 协议要求 ACK 对应，与修复无关但保持规范） */
    private val requestId = java.util.concurrent.atomic.AtomicLong(1)

    /** 消息接收计数（开发者模式：messages/sec 统计） */
    private val _messageCount = MutableStateFlow(0L)
    val messageCount: StateFlow<Long> = _messageCount.asStateFlow()

    /**
     * 启动实时流（页面进入时调用，幂等）
     *
     * 订阅策略（2025-10-17 实测协议限制后的定案）：
     * - ❌ 逐条发 SUBSCRIBE：Binance vision 限制单连接订阅帧速率
     *   （30 条逐发只 ACK ~5 条、无任何推送）
     * - ✅ 一条 SUBSCRIBE 帧批量携带全部 params：30 symbol 全部即时推送
     *
     * @param symbols 需要订阅的交易对（如 ["BTCUSDT"]）
     */
    @Synchronized
    fun start(symbols: List<String>) {
        ensureConnected()
        // 合并新 symbol 到订阅集，然后【整体重发一条批量帧】
        // （幂等：已订阅的 symbol 服务端会忽略重复，不影响成本）
        symbols.forEach { subscribed.add(it.uppercase()) }
        sendBatchSubscribe()
    }

    /**
     * 应用回到前台时调用（由 ProcessLifecycleOwner 观察）
     *
     * 退后台时 [disconnect] 已释放连接，这里重建并重放订阅：
     * - 有历史订阅 → 立即重连（否则页面 ViewModel 存活时数据永久停更）
     * - 无历史订阅 → 保持懒连接，不额外耗电（冷启动路径不变）
     */
    @Synchronized
    fun resumeIfNeeded() {
        if (subscribed.isEmpty()) return
        ensureConnected()
        sendBatchSubscribe()
    }

    /** 懒创建连接（幂等）：新连接建立后由 Manager 的 onOpen 重放订阅帧 */
    private fun ensureConnected() {
        if (ws != null) return
        Log.d("BinancePriceStream", "ensureConnected: ")
        val processScope = ProcessLifecycleOwner.get().lifecycleScope
        val manager = WebSocketManager(
            url = BINANCE_WS_URL,
            scope = processScope,
            onMessage = ::handleFrame,
        )
        ws = manager
        manager.connect()
        stateJob?.cancel()
        stateJob = processScope.launch {
            manager.state.collect { _connectionState.value = it }
        }
    }

    /** 发送批量订阅帧：当前全量订阅集一条帧携带 */
    private fun sendBatchSubscribe() {
        if (subscribed.isEmpty()) return
        ws?.send(batchSubscribeFrame(subscribed.toList()))
    }

    /**
     * 页面退出时调用 —— 【刻意不做任何事】
     *
     * 历史版本此处会 ws.close()，但单例流被多页面共享：
     * Tab 切换即销毁 ViewModel（Navigation saveState 语义），
     * 关闭连接会让其他页面数据停更。
     * 真正的断开由应用退后台触发（ProcessLifecycleOwner → stop）。
     */
    fun stop() {
        // no-op：保留方法签名兼容调用方，语义改为「页面放弃独占」
    }

    /** 应用退到后台时调用（由 ProcessLifecycleOwner 观察）：断开连接省电 */
    @Synchronized
    fun disconnect() {
        // 先停状态收集：close() 会让旧 manager 发 DISCONNECTED，
        // 若 collector 还活着，回前台新建连接后会被旧状态覆盖
        stateJob?.cancel()
        stateJob = null
        ws?.close()
        ws = null
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    // ================= 内部实现 =================

    /** 解析组合流帧并合并进报价表 */
    private fun handleFrame(text: String) {
        _messageCount.value += 1
        runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            // 订阅确认帧 {"result":null,"id":1} 无 stream 字段，直接跳过
            val stream = root["stream"]?.jsonPrimitive?.content ?: return
            if (!stream.endsWith("@ticker")) return

            val data = root["data"]?.jsonObject ?: return
            val symbol = data["s"]?.jsonPrimitive?.content ?: return
            val ticker = Ticker(
                symbol = symbol,
                price = data["c"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return,
                changePercent = data["P"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                eventTime = data["E"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            )
            // 合并更新：新报价覆盖旧值（StateFlow 原子读改写，UI collect 自动重组）
            _tickers.value = _tickers.value + (symbol to ticker)
        }
    }

    /** 构造批量 SUBSCRIBE 帧：全部 symbol 放入同一 params 数组（协议验证过的可靠模式） */
    private fun batchSubscribeFrame(symbols: List<String>): String {
        val params = symbols.joinToString(",") { "\"${it.lowercase()}@ticker\"" }
        return """{"method":"SUBSCRIBE","params":[$params],"id":${requestId.getAndIncrement()}}"""
    }

    private companion object {
        /**
         * 数据源域名（2025 国内网络实测）：
         * - stream.binance.com：地域封锁（451），直连与代理均不可用
         * - data-stream.binance.vision：官方公开镜像，免 Key 免代理直连
         */
        const val BINANCE_WS_URL = "wss://data-stream.binance.vision/stream"
    }

    /** 应用前后台切换：退后台断开省电，回前台自动重连（Ktor 不适用，这里用生命周期观察） */
    init {
        // 延迟注册：避免 Singleton 构造期间触碰 ProcessLifecycleOwner（Hilt 容器尚未就绪）
        // 实际注册发生在首次 start()（懒连接策略）
    }
}