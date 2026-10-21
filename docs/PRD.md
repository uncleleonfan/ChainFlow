
# ChainFlow Android — MVP PRD

## 1. 产品概览

### 1.1 项目名称

**ChainFlow**

定位：

> An open-source Android Web3 trading terminal.

这是一个面向 Android 的轻量级 Web3 Trading Wallet，核心能力包括：

- 非托管 Wallet
- EVM 链支持
- Solana 支持
- Token 行情
- Token 详情
- 实时价格
- Portfolio
- Swap
- 交易签名
- 链上交易状态
- WalletConnect
- Android 安全存储

# 2. 产品目标

## 2.1 MVP 必须证明的能力

项目完成后，用户应该能够：

```text
创建 Wallet
    ↓
查看资产
    ↓
查看 Token 行情
    ↓
查看 Token 详情
    ↓
查看实时价格
    ↓
连接 DApp
    ↓
进行 Swap
    ↓
本地签名
    ↓
广播交易
    ↓
查看交易状态
```

最终必须能够完成至少一次**真实 Mainnet 交易**。

同时提供 Testnet 模式，方便开发和测试。

---

# 3. MVP 范围

## 3.1 支持的 Blockchain

第一阶段：

### EVM

优先：

- Ethereum
- Base
- BSC

其中：

**Base 作为主要测试和 Demo Chain。**

原因：

- Gas 较低
- 真实交易成本低
- EVM 开发成熟
- 适合 Demo

### Solana

第二优先级：

- SOL
- SPL Token

Solana 可以在 MVP 后半段加入。

---

# 4. 技术栈

## Android

必须使用：

- Kotlin
- Jetpack Compose
- Coroutines
- Flow / StateFlow
- ViewModel
- MVI
- Navigation Compose
- Room
- Retrofit / OkHttp
- WebSocket
- Hilt / Koin
- Android Keystore
- BiometricPrompt

建议：

```text
minSdk >= 26
targetSdk = latest stable
```

---

# 5. Architecture

采用：

**Clean Architecture + Multi-module + MVI**

推荐结构：

```text
chainflow/
│
├── app/
│
├── core/
│   ├── common/
│   ├── network/
│   ├── database/
│   ├── security/
│   └── ui/
│
├── blockchain/
│   ├── core/
│   ├── evm/
│   └── solana/
│
├── data/
│   ├── market/
│   ├── wallet/
│   ├── token/
│   └── transaction/
│
└── feature/
    ├── home/
    ├── wallet/
    ├── token/
    ├── market/
    ├── swap/
    ├── activity/
    └── settings/
```

要求：

- Feature 与 Feature 尽量解耦
- Blockchain Layer 与 UI 解耦
- Data Source 与 Domain Layer 解耦
- UI 不直接访问 RPC
- Private Key 不进入 Repository / API Layer

---

# 6. 页面结构

MVP 只需要 5 个主要 Tab：

```text
Home
Markets
Swap
Wallet
Activity
```

---

# 7. Home

## 7.1 目标

提供市场入口。

页面：

```text
┌───────────────────────┐
│ ChainFlow          ⚙   │
│                       │
│ Market Overview       │
│                       │
│ BTC     $xxx   +2.3%  │
│ ETH     $xxx   +3.1%  │
│ SOL     $xxx   -1.2%  │
│                       │
│ Trending Tokens       │
│                       │
│ TOKEN A       +42%    │
│ TOKEN B       +28%    │
│ TOKEN C       +19%    │
│                       │
│ Smart Money           │
│                       │
│ Wallet A    +$42K     │
│ Wallet B    +$18K     │
└───────────────────────┘
```

MVP 不要求完整 Smart Money 算法。

可以使用公开 API 数据生成：

- Trending
- Top Gainers
- Top Volume

---

# 8. Markets

## 8.1 Token List

展示：

- Token Logo
- Symbol
- Price
- 24H Change
- Volume
- Liquidity
- Market Cap

支持：

- Search
- Sort
- Pull to Refresh

---

# 9. Token Detail

这是核心页面之一。

点击 Token：

```text
Token

ETH
Ethereum

$4,620.32
+3.82%

[1H] [4H] [1D] [1W]

Candlestick Chart

Volume
Liquidity
Market Cap

Transactions

BUY
SELL
BUY
BUY

[Swap]
```

## 9.1 数据

至少支持：

- Price
- Price Change
- Volume
- Liquidity
- Market Cap
- OHLCV
- Recent Transactions

---

# 10. Real-time Market Data

这是本项目重点。

## 10.1 数据流

```text
Market API
     ↓
WebSocket
     ↓
WebSocket Manager
     ↓
Flow
     ↓
StateFlow
     ↓
ViewModel
     ↓
Compose
```

## 10.2 必须实现

- WebSocket connection
- reconnect
- exponential backoff
- heartbeat
- connection status
- subscription management
- lifecycle awareness
- duplicate event handling
- stale data detection

---

# 11. 高频数据 Demo

为了证明性能能力，加入开发者模式：

```text
Market Data Stress Test
```

提供：

```text
100 events/sec
500 events/sec
1000 events/sec
5000 events/sec
```

数据显示：

```text
Events Received
Events Processed
Dropped Events
CPU
Memory
Frame Time
```

目的不是模拟真实市场，而是证明：

> Android 可以稳定处理高频数据流。

---

# 12. Wallet

## 12.1 创建 Wallet

支持：

```text
Create Wallet
Import Wallet
```

创建：

```text
Generate entropy
      ↓
Mnemonic
      ↓
Seed
      ↓
Private Key
      ↓
Address
```

支持：

- 12-word mnemonic
- Backup confirmation
- Restore

---

# 13. Wallet Security

## 13.1 Private Key

禁止：

- SharedPreferences 明文存储
- Room 明文存储
- Logcat 输出
- 网络上传
- Analytics 上传

使用：

```text
Android Keystore
       ↓
Encryption Key
       ↓
Encrypted Wallet Data
```

## 13.2 Biometric

支持：

- Fingerprint
- Face Unlock（设备支持时）

进入 Wallet / Signing 时可要求：

```text
Biometric Authentication
```

---

# 14. Wallet Home

```text
Total Assets

$12,482.32

24H
+3.82%

Assets

ETH       $4,200
USDC      $3,200
SOL       $2,100

[Receive]
[Send]
[Swap]
```

支持：

- Balance
- Token list
- Send
- Receive
- Swap

---

# 15. Receive

展示：

```text
Ethereum

0x1234...5678

[QR Code]
```

支持：

- Copy Address
- Share Address
- QR Code

---

# 16. Send

流程：

```text
Select Token
      ↓
Recipient
      ↓
Amount
      ↓
Network Fee
      ↓
Transaction Preview
      ↓
Biometric
      ↓
Sign
      ↓
Broadcast
      ↓
Pending
      ↓
Confirmed
```

必须显示：

- To
- Amount
- Gas
- Total
- Network
- Estimated confirmation

---

# 17. Swap

这是 MVP 最重要的 Web3 功能之一。

## 17.1 UI

```text
Swap

You Pay

0.1 ETH

↓

You Receive

~ 462 USDC

Rate
1 ETH = 4620 USDC

Price Impact
0.12%

Slippage
0.5%

Network Fee
$0.32

[Swap]
```

---

# 18. Swap 流程

```text
User Input
    ↓
Get Quote
    ↓
Show Route
    ↓
User Confirmation
    ↓
Build Transaction
    ↓
Check Approval
    ↓
Approve if required
    ↓
Sign
    ↓
Broadcast
    ↓
Monitor
    ↓
Confirmed
```

MVP 不实现自己的 DEX。

使用成熟的 DEX Aggregator / Swap API。

---

# 19. ERC-20

至少支持：

```text
balanceOf
transfer
approve
allowance
decimals
symbol
```

必须理解并正确处理：

```text
Native Token
vs
ERC-20 Token
```

---

# 20. Transaction Engine

建立统一 Transaction Engine：

```text
TransactionRequest
       ↓
TransactionBuilder
       ↓
Signer
       ↓
Broadcaster
       ↓
TransactionMonitor
       ↓
TransactionState
```

状态：

```text
Created
↓
Signing
↓
Signed
↓
Broadcasting
↓
Pending
↓
Confirmed

or

Failed
```

---

# 21. RPC Layer

建立：

```text
RpcClient
RpcProvider
RpcManager
```

支持：

```text
eth_chainId
eth_getBalance
eth_call
eth_estimateGas
eth_getTransactionCount
eth_sendRawTransaction
eth_getTransactionReceipt
```

RPC URL 不允许硬编码私密 Key。

支持：

- RPC timeout
- Retry
- Failover
- Rate limit handling

---

# 22. Activity

显示：

```text
Activity

Swap
-0.1 ETH
+462 USDC
Confirmed

Send
-100 USDC
Confirmed

Receive
+0.5 ETH
Confirmed
```

每笔交易显示：

- Type
- Token
- Amount
- Status
- Time
- Tx Hash

点击进入：

**Block Explorer**

---

# 23. Solana

MVP 第二阶段实现。

支持：

- SOL Balance
- SPL Token
- Send SOL
- Receive SOL
- Transaction signing
- Transaction broadcast
- Transaction status

理解并实现：

```text
Account
Program
Instruction
Transaction
Recent Blockhash
Signer
```

---

# 24. WalletConnect

支持基本 DApp Connection。

流程：

```text
DApp
 ↓
WalletConnect
 ↓
ChainFlow
 ↓
Connection Request
 ↓
User Approves
 ↓
Session
```

至少支持：

- Connect
- Disconnect
- Sign Message
- Send Transaction

---

# 25. Local Database

Room 保存：

```text
Wallet Metadata
Token Metadata
Recent Transactions
Cached Prices
User Preferences
```

**Private Key 不直接以明文存储在 Room。**

---

# 26. Offline Strategy

App 无网络时：

允许查看：

- 上次 Portfolio
- Token Metadata
- Transaction History

明确显示：

```text
Offline
Last updated 2 minutes ago
```

恢复网络后：

```text
Reconnect
 ↓
Refresh
 ↓
Update State
```

---

# 27. Error Handling

所有核心流程必须有：

### Network Error

```text
Unable to connect
[Retry]
```

### RPC Error

```text
RPC temporarily unavailable
Switching provider...
```

### Transaction Failed

```text
Transaction failed

Reason:
Insufficient gas
```

### User Rejected

```text
Transaction cancelled
```

### Insufficient Balance

提前阻止交易。

---

# 28. Security Requirements

必须：

- Android Keystore
- BiometricPrompt
- Encrypted local storage
- No private key logging
- No mnemonic logging
- No private key analytics
- Clipboard timeout
- Screenshot protection on sensitive screens

建议：

- Root detection
- Emulator detection
- Certificate pinning
- Debug build 与 release build 分离

---

# 29. Analytics

不能收集：

- Private Key
- Seed Phrase
- Wallet Secret

可以收集：

```text
App opened
Token viewed
Swap initiated
Swap completed
Transaction failed
RPC error
Screen load time
```

Analytics 必须匿名化。

---

# 30. Performance

必须建立 Benchmark。

测试：

### Cold Start

目标：

```text
< 1.5 sec
```

### Token List

测试：

```text
1,000 tokens
10,000 tokens
```

### WebSocket

测试：

```text
100 events/sec
500 events/sec
1000 events/sec
```

### UI

监控：

- Frame time
- Dropped frames
- Memory
- CPU

---

# 31. Testing

## Unit Test

至少：

- Wallet derivation
- Address generation
- Transaction builder
- Gas calculation
- Token amount conversion
- Swap calculation
- Repository

## Integration Test

至少：

```text
RPC
 ↓
Transaction
 ↓
Signing
 ↓
Broadcast
```

## UI Test

至少：

- Create Wallet
- Import Wallet
- Send
- Swap
- Token Detail

---

# 32. Testnet / Mainnet

App 启动默认：

**Testnet**

用户主动选择：

```text
Network

○ Testnet
○ Mainnet
```

切换 Mainnet 时：

```text
⚠️ Mainnet

You are about to use real assets.

ChainFlow is an open-source educational project.

Proceed?
```

必须明确告知风险。

---

# 33. Mainnet Demo

项目最终必须完成至少一笔真实交易。

建议：

```text
Base Mainnet

ETH
 ↓
USDC
```

交易成功后：

README 放：

- Tx Hash
- Block Explorer
- Screenshot
- Demo Video

注意：不要把任何个人私密信息放进 GitHub。

---

# 34. Developer Mode

提供：

```text
Developer Mode

Market Data Stress Test
RPC Debug
WebSocket Debug
Transaction Debug
Network Monitor
```

可以查看：

```text
WebSocket:
Connected

Messages/sec:
428

RPC:
Base

Latency:
83ms
```

这是为了体现工程能力，而不是普通用户功能。

---

# 35. UI / Design

设计原则：

风格：

- Dark
- Trading Terminal
- High information density
- Compact cards
- Clear positive / negative price movement
- Bottom navigation
- Fast interactions

核心目标：

> 看起来像一个真正的 Crypto Trading App，而不是 Android Demo。

---

# 36. MVP 不做什么

第一版明确不做：

- 自建 DEX
- 自建 Blockchain Node
- 完整 Smart Money 算法
- Copy Trading
- Sniper Bot
- Limit Order
- MEV Protection
- MPC
- NFT
- Lending
- Staking
- Fiat On-ramp
- 多账户复杂管理
- 完整交易所订单簿

这些全部放到后续 Roadmap。

---

# 37. Roadmap

## Phase 1 — Android Foundation

- Kotlin
- Compose
- MVI
- Multi-module
- Navigation
- DI

## Phase 2 — Wallet

- EVM wallet
- Keystore
- Biometric
- Send
- Receive

## Phase 3 — Market

- Token list
- Token detail
- Chart
- WebSocket
- High-frequency stress test

## Phase 4 — Trading

- Swap
- Approval
- Transaction signing
- Transaction monitoring

## Phase 5 — Web3

- Solana
- WalletConnect

## Phase 6 — Engineering

- Performance benchmark
- Security documentation
- Unit tests
- Integration tests
- CI/CD

---

# 38. GitHub README

README 第一屏必须包含：

```text
ChainFlow

Open-source Android Web3 Trading Terminal

Kotlin • Compose • MVI • Coroutines • Flow
EVM • Solana • WebSocket • WalletConnect
Android Keystore • Real On-chain Transactions
```

然后：

### Features

```text
✓ Non-custodial Wallet
✓ EVM
✓ Solana
✓ Real-time Market Data
✓ Token Intelligence
✓ Swap
✓ Local Signing
✓ WalletConnect
✓ Biometric Security
✓ WebSocket
✓ Offline Cache
✓ Performance Benchmark
```

---

# 39. 技术文档

GitHub 中额外提供：

```text
/docs

architecture.md
wallet-security.md
evm-transaction.md
solana-transaction.md
websocket.md
swap.md
performance.md
threat-model.md
```

尤其是：

### `threat-model.md`

说明：

- 攻击面
- Private Key 风险
- Root Device
- Runtime Hooking
- Clipboard
- Screenshot
- Network Attack
- RPC Attack

这会显著提高项目的 Senior Engineer 感。

---

# 40. Demo Video

最终制作 2–3 分钟 Demo：

```text
00:00 Create Wallet

00:20 Portfolio

00:40 Token Detail

01:00 Real-time Price

01:20 Swap

01:45 Sign Transaction

02:00 Blockchain Confirmed

02:15 WalletConnect

02:30 Performance Stress Test
```

重点展示：

> **Real Mainnet Transaction**

---

# 41. 成功标准

项目完成不以“页面做完”为标准。

必须满足：

### Web3

- [ ] 能创建 Wallet
- [ ] 能恢复 Wallet
- [ ] 能读取 Balance
- [ ] 能发送 Token
- [ ] 能签名
- [ ] 能广播
- [ ] 能监控 Transaction
- [ ] 能完成真实 Swap
- [ ] 至少支持一个 EVM Mainnet
- [ ] Solana 基础功能完成
- [ ] WalletConnect 基础功能完成

### Android

- [ ] Kotlin
- [ ] Compose
- [ ] MVI
- [ ] Coroutines
- [ ] Flow
- [ ] Room
- [ ] Retrofit
- [ ] WebSocket
- [ ] Multi-module
- [ ] Unit Test
- [ ] Integration Test

### Security

- [ ] Android Keystore
- [ ] Biometric
- [ ] Encrypted storage
- [ ] No secrets in logs
- [ ] Threat Model

### Performance

- [ ] WebSocket 1000 events/sec stress test
- [ ] Memory benchmark
- [ ] CPU benchmark
- [ ] Cold start benchmark
- [ ] Frame performance

