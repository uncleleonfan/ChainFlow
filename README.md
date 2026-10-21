# ChainFlow

**An open-source Android Web3 trading terminal.**

Kotlin • Compose • MVI • Coroutines • Flow
EVM • Solana • WebSocket • Li.Fi Swap
Android Keystore • Local Signing • Real On-chain Transactions

---

ChainFlow 是一个面向 Android 的**非托管（non-custodial）Web3 交易钱包**：创建或导入钱包、查看实时行情、连接聚合器做 Swap、本地签名并广播真实链上交易。

它同时也是一个**工程演示项目**：Clean Architecture + 多模块 + MVI，私钥只存在于 Android Keystore 保护的密文中，签名只发生在 `blockchain` 层，UI 不直连 RPC。

> ⚠️ **风险提示**：本项目用于技术学习与演示，未经过任何第三方安全审计。请勿存放真实大额资产，默认使用 Testnet。

---

## Features

- **非托管钱包**：BIP-39 助记词生成 / 导入，一份助记词同时派生 EVM（secp256k1）与 Solana（ed25519）账户
- **多链支持**：Ethereum / Base / BSC（Testnet + Mainnet 切换）；Solana 地址派生与 SOL 余额
- **实时行情**：Binance WebSocket 推送 + CoinGecko 快照，30+ 交易对价格跳动
- **Token 详情**：K 线图（Canvas 自绘蜡烛图）、实时价、近期成交
- **Swap**：Li.Fi 聚合器报价 → 预构建交易 → 本地签名 → 广播
- **ERC-20**：余额查询、Approve 授权、Transfer 发送
- **交易引擎**：Nonce / Gas 估算、EIP-155 签名、回执轮询、Room 落库 + Flow 自动刷新
- **安全存储**：Android Keystore AES-256/GCM 加密助记词，BiometricPrompt 每次解密，截屏保护、剪贴板敏感数据自动清理
- **MVI 单向数据流**：`UiState` / `UiIntent` / `UiEvent` 三要素统一约束全项目页面
- **可观测的错误模型**：`ChainFlowResult` + `AppError` 统一错误语义，网络 / 链上 / 本地分类处理

### 尚未实现（Roadmap）

- WalletConnect 连接 DApp
- 行情离线缓存（当前行情仅内存态）
- 性能基准体系（冷启动 / 千级列表 / WS 压力测试面板）
- 单元测试 / 集成测试 / CI 流水线

---

<!-- 截图占位：把图片放到 docs/screenshots/ 后取消注释即可
## 截图

| Home | Markets | Token Detail |
|---|---|---|
| ![](docs/screenshots/home.png) | ![](docs/screenshots/markets.png) | ![](docs/screenshots/token-detail.png) |

| Swap | Wallet | Activity |
|---|---|---|
| ![](docs/screenshots/swap.png) | ![](docs/screenshots/wallet.png) | ![](docs/screenshots/activity.png) |
-->

## 架构

**Clean Architecture + Multi-module + MVI**，依赖单向向下：

```
┌──────────────────────────────────────────────────────────┐
│ :app          应用组装层（Navigation / Theme / Lifecycle） │
├──────────────────────────────────────────────────────────┤
│ :feature:*    表现层（Screen + ViewModel，MVI）            │
│   home / market / token / swap / wallet / activity /     │
│   settings                                               │
├──────────────────────────────────────────────────────────┤
│ :data:*       数据层（Repository 编排）                    │
│   wallet / market / transaction                          │
├──────────────────────────────────────────────────────────┤
│ :blockchain:* 链抽象层（派生 / RPC / 签名 / 广播）         │
│   core / evm / solana                                    │
├──────────────────────────────────────────────────────────┤
│ :core:*       基础设施层（MVI / 网络 / 安全 / UI）         │
│   common / network / security / ui                       │
└──────────────────────────────────────────────────────────┘
```

强制依赖规则：

| 规则 | 说明 |
|---|---|
| 只允许向下依赖 | `feature → data → blockchain → core` |
| Feature 之间零依赖 | 页面间跳转由 `:app` 注入回调 |
| UI 不直接访问 RPC / WS | 必须经 Repository |
| 私钥不进入 API 层 | 签名只发生在 `blockchain` 层，Repository 不持有密钥字段 |

### 三条关键链路

**实时行情**

```
Binance WS → WebSocketManager（重连 / 心跳 / 订阅重放 / 去重）
           → BinancePriceStream（进程级单例）
           → StateFlow<Map<symbol, Ticker>> → Repository → Compose 重组
```

**交易**

```
UiIntent(Swap/Send Confirmed) → BiometricPrompt → WalletRepository.unlock()
  → blockchain 层（构建 → 本地签名 → 广播）
  → TransactionRepository（Room PENDING + 回执轮询）→ Activity 页自动刷新为 CONFIRMED
```

**钱包安全**

```
助记词（仅内存瞬时存在）
  → KeyStoreCipher（Keystore AES-256/GCM）→ EncryptedPrefs（落盘密文）
  → 使用时解密 → 派生 EVM + Solana 账户
```

---

## 项目结构

```
ChainFlow/
├── app/                       # 入口：Application / MainActivity / NavHost / 底部 5 Tab
├── core/
│   ├── common/                # MVI 基类、协程调度器、Result / Error 模型
│   ├── network/               # Retrofit + OkHttp + WebSocketManager
│   ├── security/              # Keystore 加密、生物识别、安全防护
│   └── ui/                    # Dark Trading Terminal 主题与通用组件
├── blockchain/
│   ├── core/                  # Chain 定义、地址 / 余额 / 交易模型
│   ├── evm/                   # web3j：钱包派生 / RPC / ERC-20 / 交易引擎
│   └── solana/                # SolanaKt：钱包派生 / RPC / SOL 转账
├── data/
│   ├── wallet/                # 钱包仓库（创建 / 导入 / 解锁 / 余额）
│   ├── market/                # 行情仓库 + 实时价聚合
│   └── transaction/           # Swap 仓库（Li.Fi）+ 交易历史（Room）
├── feature/                   # home / market / token / swap / wallet / activity / settings
└── docs/                      # 架构与安全文档
```

---

## 技术栈

| 领域 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.3.x、Jetpack Compose（Material 3） |
| 架构 | Clean Architecture、多模块、MVI、StateFlow / Channel |
| 构建 | Gradle 9.6、AGP 9.4.1（内置 Kotlin）、Version Catalog |
| DI | Hilt 2.60.x（KSP） |
| 网络 | Retrofit 3 + OkHttp 5 + WebSocket |
| 本地存储 | Room 2.8、DataStore、EncryptedPrefs（Keystore AES-GCM 密文） |
| 安全 | Android Keystore（AES-256/GCM）、BiometricPrompt |
| 链 | web3j 6（EVM）、SolanaKt 2.1（Solana，经 JitPack） |
| 数据源 | Binance WebSocket、CoinGecko、Li.Fi（无需 API Key） |

---

## 快速开始

### 环境要求

- macOS / Linux / Windows
- JDK 17 或更高（推荐 21）
- Android Studio 最新稳定版（需支持 AGP 9.4）
- Android SDK：`compileSdk 37`、`minSdk 26`、`targetSdk 37`
- 设备或模拟器：Android 8.0（API 26）以上，**带指纹 / 面容的实体机体验最佳**（交易签名依赖生物识别）

### 构建与运行

```bash
git clone https://github.com/<your-name>/ChainFlow.git
cd ChainFlow

# JDK 路径（本地生成，勿提交）
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # macOS 示例

./gradlew :app:assembleDebug          # 构建 Debug APK
./gradlew :app:installDebug           # 安装到已连接设备
./gradlew test                        # 单元测试
```

首次构建会从 `google()` / `mavenCentral()` / `jitpack.io` 拉取依赖（SolanaKt 仅发布在 JitPack），需要网络可访问这些仓库。

### 网络环境（Testnet / Mainnet）

- 默认 **Testnet**：Ethereum Sepolia、Base Sepolia、BSC Testnet
- 在「设置」页可切换到 **Mainnet**，切换需显式确认风险
- RPC 使用公共端点（publicnode / 官方公共 RPC），无内置 API Key，因此有速率限制

> ⚠️ 做真实 Mainnet 交易前请先用小额资金验证：链 ID、Nonce、Gas 与收款地址。

---

## 文档

完整设计与实现细节见 [`docs/`](docs)：

| 文档 | 内容 |
|---|---|
| [architecture.md](docs/architecture.md) | 架构总览、依赖规则、数据流全景 |
| [wallet-security.md](docs/wallet-security.md) | Keystore 加密、助记词体系、生物识别 |
| [threat-model.md](docs/threat-model.md) | 威胁模型：Root、Hook、剪贴板、截屏、RPC 攻击 |
| [evm-transaction.md](docs/evm-transaction.md) | EVM 派生 / RPC / 签名 / ERC-20 / 交易引擎 |
| [solana-transaction.md](docs/solana-transaction.md) | Solana 派生 / 签名 / 广播 |
| [swap.md](docs/swap.md) | Li.Fi 报价 → 预构建 → 签名链路 |
| [websocket.md](docs/websocket.md) | WS Manager：重连、心跳、订阅重放、去重 |
| [data-layer.md](docs/data-layer.md) | Repository 职责与存储选型矩阵 |
| [ui-layer.md](docs/ui-layer.md) | MVI 骨架、主题、页面组织 |
| [performance.md](docs/performance.md) | 性能设计现状、测量方法与压测方案 |
| [PRD.md](docs/PRD.md) | 完整 MVP 产品需求文档 |

---

## Roadmap

| Phase | 内容 | 状态 |
|---|---|---|
| 1 | Android 基座：Compose / MVI / 多模块 / Navigation / DI | ✅ |
| 2 | 钱包：EVM 派生 / Keystore / 生物识别 / 收发 | ✅ |
| 3 | 行情：Token 列表 / 详情 / K 线 / WebSocket | ✅ |
| 4 | 交易：Swap / Approve / 签名 / 回执监控 | ✅ |
| 5 | Web3：Solana；WalletConnect | 🟡 Solana 已完成，WalletConnect 待做 |
| 6 | 工程化：测试 / CI / 性能基准 | ⏳ |

---

## 贡献

欢迎 Issue 与 PR。提交前请：

1. 保持模块依赖方向（`feature → data → blockchain → core`），不要在 feature 之间互相依赖
2. 新页面遵循 MVI 三要素（`UiState` / `UiIntent` / `UiEvent`）
3. 不在代码中硬编码私钥、助记词、API Key；不改动公共 RPC 的安全假设
4. 保持 Commit 信息可读（建议 Conventional Commits）

---

## 免责声明

本项目按「原样」提供，不构成任何投资建议或担保。因使用本项目产生的资产损失、数据泄漏或其他后果，作者不承担责任。请自行评估风险，优先在 Testnet 上验证。

---

## License

本项目以 [MIT License](LICENSE) 开源，Copyright (c) 2026 Leon。
