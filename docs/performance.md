# 性能工程（目标 · 测量 · 压力测试）

> 对应 PRD §11/§30/§34。本文分两部分：**已实现的性能设计** 与 **尚未实现但必须建立的基准体系**（明确标注 ⏳，避免把规划写成现状）。

## 1. 目标（PRD §30）

| 场景 | 目标 | 当前状态 |
|---|---|---|
| 冷启动 | < 1.5s | ⏳ 未测量 |
| Token 列表 | 1,000 / 10,000 条流畅滚动 | 🟡 当前精选 30 条；LazyColumn 已具备虚拟化能力，未做千级压测 |
| WebSocket | 100 / 500 / 1000 / 5000 events/sec | ⏳ 未实现压力测试面板（PRD §11） |
| UI | 丢帧率、帧耗时、内存、CPU | ⏳ 无基线记录 |

> 结论：**性能设计已就位，基准数据尚未建立**。本文给出可执行的测量方案与埋点位置，避免「看起来很快」的口头结论。

## 2. 已实现的性能设计（按链路）

### 2.1 启动路径

```
Application.onCreate
  ├─ Hilt 容器初始化（编译期生成，无反射扫描）
  ├─ ProcessLifecycleOwner 注册（一次对象分配）
  └─ 结束 ← 无网络请求、无数据库打开、无 WS 连接
```

三个刻意延迟（lazy）的点：

| 资源 | 触发时机 | 收益 |
|---|---|---|
| WS 连接 | 首个行情页 `startRealtime()` | 冷启动不承担 DNS/TLS/握手（约 200–600ms） |
| Room `chainflow.db` | 首次 `recordAndMonitor` / Activity 页订阅 | 避免启动期磁盘 IO |
| Keystore 密钥 | 首次加解密 | Keystore 首次调用有百毫秒级系统开销 |

### 2.2 实时行情链路

| 环节 | 成本 | 量级（30 symbols） |
|---|---|---|
| OkHttp WS 收帧 | 线程切换 + 字符串解码 | 30 帧/s |
| `Json.parseToJsonElement` | 树形解析，产生中间对象 | 每帧 < 0.1ms |
| `Map` 合并 `_tickers.value + (symbol to ticker)` | **每次整表复制** | 30 条/次 × 30 次/s = 900 次拷贝/s |
| Compose 重组 | 仅 price 变化的行 | `key = symbol` 限制扩散范围 |

**这是当前最重要的性能特征**：整表复制是 O(n) 且每帧一次。n=30 时可忽略（微秒级），但 n=1000、1000 events/s 时会成为瓶颈（每秒百万级 Map.Entry 分配 + GC 压力）。优化方向见 §6。

### 2.3 Compose 渲染

| 设计 | 位置 | 效果 |
|---|---|---|
| `LazyColumn + key` | Markets / Home / Activity | WS 更新只重组变化行 |
| `collectAsStateWithLifecycle` | 全部 Screen | 后台停止收集，不做无谓重组 |
| 等宽字体价格 | `labelLarge` | 价格跳动时布局不抖动（避免整行重排） |
| 纯 Canvas K 线 | `CandlestickChart` | 200 根 K 线单帧绘制，无第三方图表库/无 View 互操作 |
| 强制暗色 | `ChainFlowTheme` | 无主题切换，减少重组分支 |

### 2.4 密码学路径

| 操作 | 调度器 | 说明 |
|---|---|---|
| 创建钱包（生成助记词 + 派生） | `Dispatchers.Default` | PBKDF2-HMAC-SHA512 × 2048 轮，纯 CPU |
| 解锁（解密 + 派生） | `Dispatchers.Default` | 每次签名都执行一次，用户可感知约 50–200ms |
| ERC-20 ABI 编码 | 调用方线程 | 轻量，BigInteger 运算 |

**不要在 `Dispatchers.IO` 做派生**：IO 线程池会被 RPC/网络占满，派生会把签名延迟放大到秒级。

### 2.5 交易监控

`monitorTransaction` 每 3s 轮询一次，超时 120s。当前实现是**每笔交易一个独立协程**：

```
N 笔 pending 交易 → N 个循环 → 峰值 3N 次/分钟的 eth_getTransactionReceipt
```

5 笔并发 ≈ 100 req/min，公共 RPC 尚可承受；N > 20 时需改为批量轮询（一次 `eth_getBlockByNumber` 或 `eth_getTransactionReceipt` 合并调度）。

## 3. 测量方法（可直接执行）

### 3.1 冷启动

```bash
# 强制停止 → 冷启动 → 输出 ThisTime/TotalTime/WaitTime
adb shell am force-stop com.leon.chainflow
adb shell am start -W -n com.leon.chainflow/.MainActivity
```

判读：`TotalTime` 为冷启动总耗时（含 Application）；建议连续 5 次取中位数，并区分 **debug 与 release**（R8 开启后通常快 20–40%）。

### 3.2 帧耗时与丢帧

```bash
adb shell dumpsys gfxinfo com.leon.chainflow reset
# 操作 10s（滚动列表 / 停留在实时行情页）
adb shell dumpsys gfxinfo com.leon.chainflow
```

关注 `Janky frames`、`50th/90th/95th/99th percentile`。目标：滚动与实时跳动时 95th < 16ms（60Hz）。

更深一层用系统级 trace：

```bash
adb shell atrace -t 10 -b 32768 gfx view wm am sched freq idle binder_driver > /data/local/tmp/trace.txt
```

### 3.3 内存 / CPU

```bash
adb shell dumpsys meminfo com.leon.chainflow     # PSS / Java Heap
adb shell top -H -p $(adb shell pidof com.leon.chainflow)   # 线程级 CPU
```

重点关注：WS 长跑 10 分钟后 Java Heap 是否持续增长（`recentMessageKeys` 上限 128，`subscriptions` 只增不减，理论有界）。

### 3.4 Macrobenchmark（推荐补齐）

新增 `:benchmark` 模块（`com.android.test` + `androidx.benchmark.macro`）：

```kotlin
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStartup() = rule.measureRepeated(
        packageName = "com.leon.chainflow",
        metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
    ) { pressHome(); startActivityAndWait() }
}
```

同时用 `BaselineProfileRule` 生成 baseline profile（首屏 + Markets 滚动），预计冷启动再降 15–30%。

## 4. 压力测试（PRD §11，⏳ 未实现）

### 4.1 指标定义

| 指标 | 定义 | 采集点 |
|---|---|---|
| Events Received | 注入/收到的帧总数 | 注入源计数 |
| Events Processed | 完成解析并写入 `_tickers` 的帧数 | `handleFrame` 成功分支 |
| Dropped Events | 因背压/去重/主动采样丢弃的帧数 | Received − Processed − 去重命中 |
| Frame Time | 每帧渲染耗时 | `Choreographer.postFrameCallback` 差值 |
| CPU / Memory | 进程 PSS 与主线程 CPU | `Debug.MemoryInfo` / `Debug.threadCpuTimeNanos` |

### 4.2 实现方案（不改动生产链路）

```
SyntheticTickerSource（仅 debug）
   │  以目标速率（100/500/1000/5000 eps）生成 Binance 格式帧
   ▼
BinancePriceStream.handleFrame（复用真实解析与聚合逻辑）
   ▼
DeveloperModePanel（Settings → Developer Mode）
   实时展示上表指标 + 目标速率切换
```

要点：
1. **复用真实解析路径**，不要绕过 JSON 解析 —— 否则测的是 `Map` 拷贝而不是整条链路
2. 注入用 `Dispatchers.Default` 的单协程 `while` + 批量补偿（不要为每帧起协程）
3. 面板本身 1s 刷新一次（`snapshotFlow` + `sample`），避免被测对象被监控拖慢
4. 必须能在 release 构建关闭（`if (BuildConfig.DEBUG)` 或 Hilt 绑定替换）

### 4.3 预期瓶颈与判据

| 速率 | 预期瓶颈 | 判据 |
|---|---|---|
| 100 eps | 无 | 丢帧 < 1% |
| 500 eps | JSON 树解析分配 | 主线程 95th < 16ms |
| 1000 eps | 整表 Map 复制（1000 条 × 1000 次/s） | 出现 GC 抖动，需采样/批量 |
| 5000 eps | 必须引入「按帧合并 + 采样」 | 不做优化必然丢帧 |

> 关键认知：真实行情中同一 symbol 每秒最多数次推送，**5000 eps 是工程能力演示，不是业务需求**。处理策略应是「合并为最新值」而非「全部渲染」—— 价格只关心最后一口价。

## 5. 已知性能风险（代码级）

| # | 位置 | 风险 | 触发条件 | 建议 |
|---|---|---|---|---|
| 1 | `BinancePriceStream.handleFrame` | 每帧整表 `Map` 复制 + 分配 | symbol 数 > 200 或 eps > 500 | 按 symbol 分片 `MutableStateFlow`，或用 `SnapshotStateMap` / 批量合并后单次提交 |
| 2 | `MarketUiState.displayList` | 每次重组重新过滤 + 排序 | 列表 > 500 条 | 在 ViewModel `combine` 后 `stateIn`，或 `derivedStateOf` |
| 3 | `TransactionRepository.recordAndMonitor` | `runBlocking` 插入 | 主线程调用时 | 改为「先入库再发事件」的顺序，不用阻塞换语义 |
| 4 | `monitorTransaction` | 每笔交易一条轮询循环 | pending > 20 | 统一调度器批量查询 |
| 5 | `WebSocketManager.recentMessageKeys` | `synchronized` 全局锁（IO 线程） | 高频帧 | 已限 128 条，锁区间极小；如需扩展改无锁环形缓冲 |
| 6 | `TokenDetailViewModel.loadChart` | 切换周期时并发请求无节流 | 快速连点 4 个周期 | 记录 job 并取消上一个（同 Swap 报价做法） |
| 7 | `WalletScreen` | 大 `Column` + 多处 `format` 字符串 | 每帧重组 | 仅总览页，可接受；金额格式化结果可 `remember` |
| 8 | R8 `packageScope` | 仅优化 androidx/kotlin/kotlinx | — | web3j/protobuf 未优化，APK 与启动仍有压缩空间 |

## 6. 优化路线（按收益/成本排序）

1. **采样 + 帧合并**：WS 侧维护 `latestBySymbol`，以固定节奏（如 100ms）批量提交一次 StateFlow —— 直接解决 §5-1，且真实行情下体验无差别
2. **Baseline Profile**：首屏 + Markets/Swap 路径，冷启动典型收益 15–30%
3. **`displayList` 缓存**：把过滤/排序移到 ViewModel，UI 只读结果
4. **批量回执轮询**：合并 pending 交易监控
5. **列表千级压测**：构造 1000/10000 条假数据，验证 `LazyColumn` 滚动帧率与内存
6. **R8 全量优化**：评估 web3j 反射需求后放开 `packageScope`

## 7. 基线记录模板（每次发版填入）

| 版本 | 构建类型 | 冷启动(ms, 中位数) | 行情页 95th 帧耗时 | 滚动 Janky% | PSS(MB, 10min) | 压力测试结果 |
|---|---|---|---|---|---|---|
| 0.1.0 | debug | TODO | TODO | TODO | TODO | 未实现 |
| 0.1.0 | release | TODO | TODO | TODO | TODO | 未实现 |

> 没有基线数据的性能优化等于猜测。任何「优化」提交都应附带前后两组数字（同设备、同构建类型、≥5 次取中位数）。
