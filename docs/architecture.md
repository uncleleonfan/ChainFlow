# ChainFlow 技术架构总览

> 对应 PRD §5。本文是所有模块文档的入口，深入细节请阅读各模块专篇（见目录）。

## 1. 架构风格

**Clean Architecture + Multi-module + MVI**，单向依赖、分层隔离：

```
┌─────────────────────────────────────────────────────────┐
│  :app          应用组装层（导航/主题/生命周期，无业务）    │
├─────────────────────────────────────────────────────────┤
│  :feature:*    表现层（Screen + ViewModel，MVI）          │
│    home / market / token / swap / wallet / activity /    │
│    settings                                              │
├─────────────────────────────────────────────────────────┤
│  :data:*       数据层（Repository 编排，业务用例）         │
│    wallet / market / transaction                          │
├─────────────────────────────────────────────────────────┤
│  :blockchain:* 链抽象层（签名/RPC/派生，与 UI 完全解耦）   │
│    core / evm / solana                                   │
├─────────────────────────────────────────────────────────┤
│  :core:*       基础设施层（MVI/网络/安全/UI/通用）         │
│    common / network / security / ui                      │
└─────────────────────────────────────────────────────────┘
```

### 依赖规则（强制）

- 只允许**向下依赖**：feature → data → blockchain → core
- **Feature 之间零依赖**（导航回调由 :app 注入）
- **UI 不直接访问 RPC / WebSocket**（必须经 Repository）
- **私钥不进入 Repository 以外的 API 层**（签名只发生在 blockchain 层）

## 2. MVI 单向数据流

全项目 UI 状态管理统一基于 `core:common` 的 MVI 骨架：

```
用户交互 → UiIntent → ViewModel.onIntent()
                            │
                ┌───────────┴───────────┐
                ▼                       ▼
        updateState{}（原子）       sendEvent()（一次性）
                │                       │
        StateFlow<UiState>         Channel<UiEvent>
                │                       │
        Compose collect 渲染      LaunchedEffect 消费（Toast/导航/生物识别）
```

三要素的语义边界：
- **UiState**：不可变界面快照，可重复消费（旋转屏幕重放安全）
- **UiIntent**：用户意图（动词短语），View 层唯一职责是发送它
- **UiEvent**：发生即消费的副作用（导航/弹窗），不经 StateFlow 避免重放

## 3. 数据流全景（三大链路）

### 3.1 行情实时链路（PRD §10）

```
Binance WS (data-stream.binance.vision)
   → WebSocketManager（重连/心跳/订阅重放/去重）
   → BinancePriceStream（单例，进程级生命周期，批量订阅帧）
   → StateFlow<Map<symbol, Ticker>>
   → Repository → ViewModel.collectAsStateWithLifecycle → Compose 实时跳动
```

### 3.2 交易链路（PRD §18/20）

```
UiIntent(SwapConfirmed/SendConfirmed)
   → BiometricPrompt（UI 层）
   → WalletRepository.unlock()（每次解密派生，不缓存私钥）
   → blockchain 层（构建 → 本地签名 → 广播）
   → TransactionRepository（Room 落库 PENDING + 应用级协程轮询回执）
   → Flow → Activity 页自动刷新 CONFIRMED
```

### 3.3 钱包安全链路（PRD §13）

```
助记词（明文仅存在于内存瞬时窗口）
   → KeyStoreCipher（Android Keystore AES-256/GCM）
   → EncryptedPrefs（Base64(iv).ciphertext 落盘）
   → 使用时解密 → 派生 EVM(secp256k1) + Solana(ed25519) 账户
```

## 4. 技术栈与关键选型

| 领域 | 选型 | 决策依据 |
|---|---|---|
| 构建 | AGP 9.4.1 内置 Kotlin | 新 DSL（compileSdk 块状语法），无 kotlin-android 插件 |
| DI | Hilt 2.60.1 (KSP) | 与 Android Studio 集成最佳；AGP 9 要求 ≥2.59.2 |
| EVM | web3j 6.0.0 | 排除 AWS KMS/vertx/netty 传递依赖（只用本地签名） |
| Solana | SolanaKt 2.1.1 (JitPack) | 内置 BIP39，与 EVM 同助记词体验 |
| 行情 | binance.vision + publicnode | 国内直连可用（主站 451/超时，实测替代） |
| Swap | Li.Fi 聚合器 | 免 Key 免代理，quote→预构建→签名链路完整 |

## 5. 模块文档目录

| 文档 | 覆盖模块 |
|---|---|
| [wallet-security.md](wallet-security.md) | core:security + 助记词体系 + 威胁模型 |
| [evm-transaction.md](evm-transaction.md) | blockchain:evm（RPC/签名/ERC-20/交易引擎） |
| [solana-transaction.md](solana-transaction.md) | blockchain:solana |
| [websocket.md](websocket.md) | core:network（WS Manager + REST + 实时流） |
| [swap.md](swap.md) | data:transaction + feature:swap |
| [data-layer.md](data-layer.md) | data:wallet / data:market / Room |
| [ui-layer.md](ui-layer.md) | core:ui + core:common(MVI) + feature:* |
| [threat-model.md](threat-model.md) | 全项目安全威胁模型（PRD §39 要求） |
| [performance.md](performance.md) | 性能目标 / 测量方法 / 压力测试设计 |
