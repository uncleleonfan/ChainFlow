# 实时行情链路（core:network + data:market）

> 对应 PRD §10/§10.2、§8。模块路径 `core/network/.../ws/`（连接层）+ `data/market/`（业务流）。

## 1. 分层与职责

| 层 | 类 | 职责 | 生命周期 |
|---|---|---|---|
| 连接层 | `WebSocketManager` | 一条连接的可靠性：重连/退避/心跳/订阅重放/去重 | 由持有方创建与关闭 |
| 业务流 | `BinancePriceStream` | Binance 协议编解码、`Map<symbol, Ticker>` 聚合、进程级保活 | `@Singleton`（进程） |
| 门面 | `MarketRepository` | REST 快照 + K 线 + 实时价统一出口，DTO → UI 模型 | `@Singleton` |
| 传输 | `NetworkModule` | Retrofit/OkHttp/Json 单例配置 | `@Singleton` |

关键边界：**UI 不接触 WS**。ViewModel 只 collect `StateFlow` 并 dispatch Intent。

## 2. 数据流

```
data-stream.binance.vision（组合流）
   │  IO 线程（OkHttp WebSocketListener）
   ▼
WebSocketManager.onMessage
   ├─ 去重（128 帧 LRU 指纹）
   ├─ lastMessageAt 打点
   ▼
BinancePriceStream.handleFrame（Json 解析 → Ticker）
   ▼
MutableStateFlow<Map<String, Ticker>>（整表快照，原子替换）
   ▼
MarketRepository.realtimeTickers
   ▼
ViewModel.collect → UiState → Compose（collectAsStateWithLifecycle）
```

**线程模型**：解析与状态写入发生在 OkHttp IO 线程；`StateFlow.value = 新 Map` 是 CAS 原子替换，UI 侧在主线程收到新快照，因此无需额外加锁。

## 3. WebSocketManager 设计

### 3.1 对外接口

```kotlin
class WebSocketManager(
    url: String,
    scope: CoroutineScope,
    onMessage: (String) -> Unit,
    client: OkHttpClient = defaultClient(),
) {
    val state: StateFlow<WsConnectionState>       // DISCONNECTED / CONNECTING / CONNECTED
    val lastMessageAt: StateFlow<Long>            // 最近收包时间（ms），0 = 从未收到
    fun connect()                                  // 幂等
    fun send(message: String): Boolean             // 断线期间缓存
    fun close()                                    // 主动关闭，不重连
}
```

### 3.2 状态机

```
DISCONNECTED ──connect()──→ CONNECTING ──onOpen──→ CONNECTED
      ▲                          │                    │
      │                     onFailure/onClosed         │
      └────退避后重试────────────┴────────────────────┘
                              close()（manualClose=true，终止循环）
```

`connect()` 用 `@Synchronized` + 状态判断实现幂等：重复调用不会建立第二条连接。

### 3.3 指数退避

```kotlin
delay(min(BASE shl min(attempts, 5), MAX))   // 1s→2s→4s→8s→16s→30s(封顶)
```

- `BASE = AppConstants.WS_RECONNECT_BASE_DELAY_MS = 1000`
- `MAX = AppConstants.WS_RECONNECT_MAX_DELAY_MS = 30_000`
- `attempts` 在 `onOpen` 时清零，避免一次长时间断网后永久处于最大退避
- 每轮发起握手后再 `delay(2000)` 观察结果：握手失败（未收到 `onOpen`）时循环继续，即实际重试节奏为 `退避 + 2s`

### 3.4 心跳

通过 OkHttp 的 `pingInterval(30s)` 在协议层发 WS Ping/Pong，**不占用应用层消息通道**；同时 `readTimeout(0)` 关闭读超时，避免长连接被误判空闲。

选型理由：Binance 自身有 24h 强制断开策略，应用层心跳（如 `{"method":"PING"}`）会增加帧开销与解析成本，协议层 ping 已足够探活。

### 3.5 订阅管理（断线重放）

`send()` 会把消息写入 `subscriptions: MutableSet<String>`，`onOpen` 时整体重放：

```kotlin
synchronized(this) { subscriptions.forEach { webSocket.send(it) } }
```

这样调用方（`BinancePriceStream`）不需要感知断线，重连后数据自动恢复推送。代价是订阅集合只增不减（见 §7）。

### 3.6 重复帧处理

```kotlin
val key = text.hashCode().toString()
if (recentMessageKeys.containsKey(key)) return   // 128 条 LRU
```

针对的场景：重连瞬间服务端可能重发最后一条帧、单流与组合流同时订阅同一 symbol。ticker 帧含事件时间 `E`，正常情况下指纹不会碰撞。

**已知局限**（有意保留，MVP 不做过度设计）：若某 symbol 在极短时间内推送两条**逐字节相同**的帧（价格与时间戳均未变），第二条会被丢弃 —— 对 UI 无可见影响；而 128 条窗口不足以覆盖 30 symbol 的一轮推送，不会误杀正常帧。

### 3.7 主动关闭 vs 服务端关闭

| 触发 | `manualClose` | 行为 |
|---|---|---|
| `close()` | true | 关闭连接、取消失协程、置 DISCONNECTED |
| `onFailure` | false | 进入重连循环 |
| `onClosed`（服务端 24h 断开等） | false | 进入重连循环 |

## 4. Binance 组合流协议细节

```
URL   wss://data-stream.binance.vision/stream
订阅  {"method":"SUBSCRIBE","params":["btcusdt@ticker","ethusdt@ticker"],"id":1}
推送  {"stream":"btcusdt@ticker","data":{"s":"BTCUSDT","c":"68000.1","P":"2.31","E":1730000000000}}
ACK   {"result":null,"id":1}          ← 无 stream 字段，需跳过
```

字段映射：`s` symbol、`c` 最新价、`P` 24h 涨跌幅、`E` 事件时间。

### 4.1 踩坑记录：必须批量订阅

逐条发送 `SUBSCRIBE` 时，`data-stream.binance.vision` 有**订阅帧速率限制**：30 条逐发只 ACK 约 5 条，且不产生任何推送（表现为「连接成功但无数据」）。

修复方式：维护 `subscribed: MutableSet<String>`，每次 `start()` 后把**当前全量订阅集放进同一条帧**重发（幂等，服务端忽略重复）。

```kotlin
{"method":"SUBSCRIBE","params":["btcusdt@ticker",...全部...],"id":N}
```

### 4.2 踩坑记录：域名选择

| 域名 | 国内直连 |
|---|---|
| `stream.binance.com` | ❌ 451（地域封锁） |
| **`data-stream.binance.vision`** | ✅ 官方公开镜像，免 Key 免代理 |

REST 同源使用 `data-api.binance.vision`（见 `NetworkModule`），一条数据链路贯穿 REST + WS。

## 5. 生命周期策略（进程级而非页面级）

`BinancePriceStream` 是 `@Singleton`，连接绑定**应用进程**：

```
ProcessLifecycleOwner.onStop   → priceStream.disconnect()    // 退后台断开，省电省流量
ProcessLifecycleOwner.onStart  → priceStream.resumeIfNeeded() // 回前台重连 + 重放订阅
页面 start()                    → 追加订阅 + 重发批量帧        // 懒连接（首次进入行情页）
页面退出                         → 只取消自己的 collect 协程
```

> 回前台的 `resumeIfNeeded()` 在无历史订阅时直接返回（保持懒连接，冷启动路径不变）。
> 两个回调都在主线程**同步**执行：若派发到其它 Dispatcher，快速「切后台→回前台」
> 时 `disconnect` 可能晚于 `resume`，把刚重连的连接又关掉。
> 同时 `disconnect()` 会取消 `state` 收集任务，否则旧 manager 的 `DISCONNECTED`
> 会在新连接建立后覆盖 `connectionState`，UI 徽章错误显示为断开。

### 5.1 历史缺陷：Tab 切换导致「数据不实时」

早期版本把 `stop()` 暴露给 `ViewModel.onCleared()`。但底部导航使用 `saveState/restoreState`，**切换 Tab 会销毁 ViewModel**，共享连接被误关，其他页面的实时价停更。

现行策略：
1. `stop()` 改为 no-op（保留签名，语义变更为「页面放弃独占」）
2. 断开只由 `ProcessLifecycleOwner.onStop` 触发
3. 订阅集合只增不减（30 个 ticker ≈ 30 msg/s，成本可忽略）

> 注：`HomeViewModel/MarketViewModel/TokenDetailViewModel` 仍在 `onCleared()` 调用 `stopRealtime()`，因而是安全的 no-op；后续如要清理订阅，应改为引用计数而非直接关闭连接。

## 6. REST 部分（快照与 K 线）

| 端点 | 用途 | 备注 |
|---|---|---|
| `GET /ticker/24hr?symbols=[...]` | 列表 / 详情快照 | `symbols` 必须是 JSON 数组字符串（Retrofit 会 URL 编码） |
| `GET /klines?symbol&interval&limit` | K 线 | 返回数组的数组，`limit=200` |

- 精选清单 `CORE_SYMBOLS`（30 个主流 USDT 对）：全量约 3000+ 对含杠杆代币噪音，MVP 用精选集
- 默认排序：`quoteVolume24h` 降序
- 周期映射：`1H→1h / 4H→4h / 1D→1d / 1W→1w`（`ChartPeriod` 枚举）
- `CoinGeckoApi` 保留但**不再作为主源**（国内直连不可达），仅作未来聚合备用

## 7. PRD §10.2 逐项对照

| 要求 | 状态 | 实现位置 |
|---|---|---|
| WebSocket connection | ✅ | `WebSocketManager.openSocket()` |
| reconnect | ✅ | `handleDisconnect()` |
| exponential backoff | ✅ | 1s→30s 封顶，成功后重置 |
| heartbeat | ✅ | OkHttp `pingInterval(30s)` |
| connection status | ✅ | `state: StateFlow` + Home 页徽章 |
| subscription management | ✅ | 订阅集缓存 + `onOpen` 重放 + 批量帧 |
| lifecycle awareness | ✅ | `ProcessLifecycleOwner`（进程级） |
| duplicate event handling | ✅ | 128 帧指纹 LRU |
| stale data detection | ⏳ | `lastMessageAt` 已打点，UI 尚未消费（见 §8） |

## 8. 当前边界与后续

- **陈旧检测未闭环**：`lastMessageAt` 已维护，但 `UiState` 尚未暴露「N 秒未更新」标记；建议在 Repository 侧 `combine(tickers, lastMessageAt)` 输出 `staleness`，由 Home 徽章展示「实时/延迟/离线」
- **离线策略（PRD §26）未实现**：行情表仅存内存，进程重启即丢失；缓存需新增 Room 表（见 [data-layer.md](data-layer.md)）
- **退避无抖动**：多设备同时断线会在同一时刻重连，Phase 6 可加 `Random.nextLong(0, 500)`
- **订阅无回收**：长期运行会累积所有访问过的 symbol；Phase 6 改为引用计数
- **压力测试未实现**：PRD §11 要求 100→5000 events/sec 的开发者模式面板，设计见 [performance.md](performance.md)
