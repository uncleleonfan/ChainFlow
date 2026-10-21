# 数据层（data:wallet / data:market / data:transaction）

> 对应 PRD §25/§26/§27。模块路径 `data/wallet`、`data/market`、`data/transaction`。
> 安全细节见 [wallet-security.md](wallet-security.md)，实时链路见 [websocket.md](websocket.md)。

## 1. 模块划分

```
data/
├── wallet/       WalletRepository     创建/导入/解锁/余额/网络偏好
├── market/       MarketRepository     行情门面（REST 快照 + K 线 + WS 实时）
│                 BinancePriceStream   实时价聚合（进程级单例）
│                 TokenListItem/Candle  UI 唯一依赖的模型（屏蔽数据源）
└── transaction/  SwapRepository       Li.Fi 报价 + 预构建交易
                  TransactionRepository 交易历史 + 回执监控
                  db/                  Room（TransactionEntity/Dao/Database）
                  di/                  ApplicationScope（应用级协程作用域）
```

依赖方向：`feature → data → blockchain → core`。`data` 层可以依赖 `blockchain` 层（签名、RPC），但**私钥只以 `Credentials` 参数形式短暂穿过**，Repository 不持有任何密钥字段。

## 2. 存储选型矩阵

| 数据 | 载体 | 加密 | 理由 |
|---|---|---|---|
| 助记词 | `EncryptedPrefs`（SP 文件 `chainflow_secure_prefs`） | Keystore AES-256/GCM 密文 | 单条小体积 KV，明文永不落盘 |
| 钱包地址 | 同上（明文键 `wallet_address`） | 否 | 公开信息，需毫秒级读取 |
| 链 / Testnet 偏好 | DataStore `wallet_prefs` | 否 | 真正的偏好数据，响应式读取 |
| 交易历史 | Room `chainflow.db` / `transactions` | 否 | 结构化查询 + Flow 响应式 |
| 行情报价 / K 线 | **仅内存** | — | MVP 未做离线缓存（见 §7） |

> 选型原则：**「是否需要查询」决定 Room，「是否机密」决定 Keystore**。两者正交，不能因为「怕泄漏」就把所有数据塞进加密存储（会牺牲可查询性与性能）。

## 3. WalletRepository

### 3.1 依赖与职责

```kotlin
class WalletRepository @Inject constructor(
    @ApplicationContext context: Context,
    encryptedPrefs: EncryptedPrefs,
    walletFactory: EvmWalletFactory,        // BIP-39/44 派生
    rpcClient: EvmRpcClient,                // 余额查询
    solanaWalletFactory: SolanaWalletFactory, // 同助记词派生 SOL 地址
    solanaService: SolanaService,           // SOL 余额
    dispatchers: DispatcherProvider,
)
```

### 3.2 用例表

| 方法 | 语义 | 关键实现 |
|---|---|---|
| `hasWallet()` | 启动时判断走引导页还是总览 | `flowOf(encryptedPrefs.hasWallet())` |
| `createWallet(backupConfirmed)` | 创建新钱包 | `require(backupConfirmed)` + `require(!hasWallet())`，先加密落盘再返回明文 |
| `importWallet(mnemonic)` | 导入 / 恢复 | `MnemonicUtils` 校验，失败抛异常 → `AppError` |
| `unlock()` | 换取 `Credentials` 用于签名 | 每次重新解密派生，**不缓存** |
| `getNativeBalance()` | EVM 原生币余额 | `eth_getBalance` → `movePointLeft(18)` |
| `getErc20Balance(contract)` | ERC-20 余额 | ⏳ 占位，返回 `not implemented yet`（Phase 3） |
| `currentNetwork()` | 当前 `Chain + isMainnet` | 默认 **Base + Testnet**（PRD §3/§32） |
| `selectChain` / `setEnvironment` | 设置页写入 | 仓库只持久化，风险确认由 UI 负责 |
| `deleteWallet()` | 忘记钱包 | 清密文 + 地址；**保留**网络偏好（设备级配置） |
| `solanaAddress()` / `getSolBalance()` | Solana 资产 | 同助记词派生，SOL 失败不阻断 EVM 展示 |

### 3.3 CPU 密集路径

派生（PBKDF2-HMAC-SHA512，2048 轮）与解密全部在 `dispatchers.default`：

```kotlin
withContext(dispatchers.default) { walletFactory.createNewWallet() }
```

这是冷启动/创建钱包耗时的主要来源；不要挪到 `io`（IO 调度器线程池会被网络请求占满）。

### 3.4 为什么不缓存 `Credentials`

威胁模型是「内存 dump / 进程注入」：把私钥常驻字段，等于把攻击窗口从「签名瞬间」扩大到「整个进程生命周期」。代价是每次签名都要解密派生（约几十毫秒），对交互无感。

## 4. MarketRepository

### 4.1 门面职责

| 方法 | 数据源 | 输出 |
|---|---|---|
| `getMarketList()` | `GET ticker/24hr?symbols=[…30 个…]` | `List<TokenListItem>`，按成交额降序 |
| `getTickerDetail(symbol)` | 同上（单 symbol） | 详情页头部（价格/涨跌/成交额） |
| `getKlines(symbol, interval)` | `GET klines?limit=200` | `List<Candle>`（open/high/low/close） |
| `startRealtime(symbols)` | WS（`BinancePriceStream`） | 追加订阅，幂等 |
| `realtimeTickers` / `wsState` | WS StateFlow | UI 直接 collect |

### 4.2 模型隔离

`TokenListItem` 是 UI 唯一依赖的行情模型：

```kotlin
data class TokenListItem(symbol, baseAsset, price, changePercent, quoteVolume24h)
```

数据源从 CoinGecko 切到 Binance vision 时 UI 未改动 —— 这是这层模型存在的唯一理由。`CoinGeckoApi` 仍保留在 `core:network`（备用），但没有 Repository 调用它。

### 4.3 实时价与快照的合并策略

**不合并到 Repository，留给 UI 状态组合**：

```
REST 快照 → MarketUiState.tokens（列表骨架，含成交额）
WS 实时  → MarketUiState.livePrices（覆盖显示价与涨跌幅）
```

理由：成交额、排序字段由快照提供（WS ticker 也有，但快照是所有字段的权威源），价格由 WS 提供（新鲜度更高）。两者分离后，断流时列表仍可读（降级为快照）。

## 5. TransactionRepository

```
recordAndMonitor(txHash, type, chain, environment, from/to, counterparty)
   ├─ ① runBlocking { dao.insert(PENDING) }        // 调用方返回时保证「已入库」
   └─ ② ApplicationScope.launch { monitorTransaction → dao.updateStatus }
```

### 5.1 状态机落库

| RPC 结果 | Room `status` |
|---|---|
| 广播成功 | `PENDING` |
| receipt.status = 0x1 | `CONFIRMED` |
| receipt.status = 0x0 | `FAILED` |
| 120s 超时 | 保持 `PENDING`（不误报失败，用户可去浏览器查） |

`observeAll()` 暴露 `Flow<List<TransactionEntity>>`，Activity 页订阅后状态变化自动刷新（Room 可观察查询）。

### 5.2 `ApplicationScope`

```kotlin
@Qualifier annotation class ApplicationScope
CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

监控必须超越页面生命周期：用户广播后立刻切 Tab / 退出页面，交易仍在打包。用 `viewModelScope` 会被取消，导致状态永远停在 `PENDING`。

### 5.3 为什么用 `runBlocking` 插入

`executeSwapAfterBiometric` 在发送 `TransactionBroadcast` 事件后，Activity 页可能**立刻**读取列表。若 insert 是异步的，会出现「提示已广播但列表为空」的竞态。`runBlocking` 提供「返回即已落库」的强语义。

代价：在 IO 线程上阻塞一次本地 insert（毫秒级）。如果后续发现主线程抖动，可改为 `insert 后 emit 事件`（顺序反转），而不是继续加阻塞。

## 6. Room 结构

```sql
CREATE TABLE transactions (
  txHash       TEXT PRIMARY KEY,
  type         TEXT NOT NULL,      -- SEND / SWAP
  chainId      INTEGER NOT NULL,
  status       TEXT NOT NULL,      -- PENDING / CONFIRMED / FAILED
  fromSymbol   TEXT NOT NULL,
  fromAmount   TEXT NOT NULL,      -- 人类可读字符串（避免 BigDecimal 序列化问题）
  toSymbol     TEXT,
  toAmount     TEXT,
  counterparty TEXT,               -- Send 收款地址 / Swap 路由合约
  createdAt    INTEGER NOT NULL,
  updatedAt    INTEGER NOT NULL
);
```

DAO：`insert(IGNORE)`（txHash 幂等）、`update`、`updateStatus(txHash, status, updatedAt)`、`observeAll()`、`findByHash()`。

设计取舍：
- **金额用 TEXT**：SQLite 无 BigDecimal，REAL 会丢精度；展示型金额用字符串最安全
- **txHash 作主键**：天然去重，重试广播不会产生重复行
- **`exportSchema = false` + version 1**：MVP 无迁移负担；Phase 6 打开 schema 导出并补 Migration

## 7. PRD §25 / §26 对照

| PRD 要求 | 状态 |
|---|---|
| Wallet Metadata | ✅ 地址（EncryptedPrefs）+ 链/环境（DataStore） |
| Token Metadata | ⏳ 未落库（行情清单来自 REST，内存态） |
| Recent Transactions | ✅ Room `transactions` |
| Cached Prices | ⏳ 未实现 |
| User Preferences | ✅ DataStore（链 / 环境） |
| Private Key 不明文存 Room | ✅ 助记词只在 Keystore 密文 SP 中 |
| 离线查看上次 Portfolio | ⏳ 未实现（无余额/行情缓存） |
| 「Offline · Last updated N minutes ago」 | ⏳ 未实现（无 `lastUpdated` 字段） |
| 恢复网络后刷新 | 部分：下拉刷新 / 重新进入页面会重拉；无网络监听自动刷新 |

## 8. 错误模型

```kotlin
sealed interface ChainFlowResult<out T> { Success(data) | Failure(AppError) }
```

- 所有 Repository 对外返回 `ChainFlowResult`，异常在边界处收敛为 `AppError`
- `chainFlowRunCatching` **原样重抛 `CancellationException`**：否则页面退出时的取消会被吞掉，破坏结构化并发
- `Throwable.toAppError()`：`IOException → Network`，其余 → `Unknown(detail)`；`AppError.Rpc` 由 RPC 层显式构造（保留 code 供日志）

## 9. 已知问题与后续

| 问题 | 影响 | 建议 |
|---|---|---|
| `getErc20Balance` 占位 | 无法展示 USDC 等 Token 余额 | 注入 `Erc20Client`，按 `decimals` 换算 |
| `hasWallet()` 用 `flowOf` 包装 | 非响应式，导入后需手动刷新 | 改用 DataStore 或 `MutableStateFlow` 驱动 |
| 无行情/余额缓存 | 断网后页面空白（PRD §26） | 新增 `cached_tickers` / `cached_balance` 表 + `lastUpdated` |
| 进程被杀后 PENDING 不恢复 | 历史交易可能永久停留待确认 | 启动时扫描 `status = PENDING` 重新 `monitorTransaction` |
| `runBlocking` 插入 | IO 线程短阻塞 | 改为「先入库再发事件」的显式顺序 |
| 无数据库迁移 | 后续改表会崩 | 开启 `exportSchema`，补 Migration |
| 无缓存淘汰策略 | 交易表无限增长 | 按 `createdAt` 保留最近 N 条 |
