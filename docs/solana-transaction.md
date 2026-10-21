# Solana 交易架构（blockchain:solana）

> 对应 PRD §23。模块路径 `blockchain/solana/`，基于 SolanaKt 2.1.1（JitPack）。

## 1. 与 EVM 的概念映射

理解 Solana 的最快方式是对照熟悉的东西：

| 概念 | EVM | Solana |
|---|---|---|
| 账户模型 | 全局状态（地址→余额/存储） | **一切皆账户**（Account），程序/数据/余额都是账户 |
| 防重放 | nonce（递增计数器） | **Recent Blockhash**（约 90 秒有效期的区块哈希） |
| 签名曲线 | secp256k1 | **ed25519**（快、签名小、无随机数风险） |
| 签名标识 | txHash（哈希） | **signature**（本身就是签名值） |
| 交易结构 | {to, value, data} | **指令列表**（Instruction：program + accounts + data） |
| 转账程序 | EOA→EOA 内建 | **SystemProgram.transfer**（系统程序指令） |
| 确认 | receipt.status | confirmationStatus: processed→confirmed→finalized |

## 2. 双链同助记词派生

```
BIP-39 12 词
   ├── EVM:    m/44'/60'/0'/0/0  → secp256k1（web3j）
   └── Solana: m/44'/501'/0'/0'  → ed25519（SolanaKt HotAccount.fromMnemonic）
```

- 路径 `501'` 是 Solana 的 BIP-44 币种号；`HotAccount` 默认 `BIP44_M_44H_501H_0H_OH` 与 Phantom/Solana CLI 兼容
- passphrase 传空串与 EVM 侧保持一致 —— 一份备份恢复双链

## 3. SolanaService 三大操作

### 3.1 getBalance

`api.getBalance(PublicKey(address))` → lamports（最小单位，10⁻⁹ SOL）→ BigDecimal 移位展示。

### 3.2 sendSol（PRD §23 Send SOL）

```
1. getRecentBlockhash()          ← 防重放（等价 nonce 语义）
2. Transaction + SystemProgram.transfer(from, to, lamports)
3. sendTransaction(tx, [account], blockhash)
   └─ SolanaKt 内部：签名(ed25519) → serialize → sendRawTransaction
```

`SystemProgram.transfer` 的指令编码：`4B program index(2) + 8B lamports(LE)`，
账户元数据 `(from, signer+writable) + (to, writable)`。

### 3.3 isConfirmed（状态轮询）

`getSignatureStatuses([signature])` → `confirmationStatus`：
- `confirmed` / `finalized` → true（完成）
- `processed` → false（已见未确认）
- null → 未上链（继续轮询）

## 4. RPC 端点策略（国内直连实测）

| 端点 | 直连 |
|---|---|
| api.mainnet-beta / devnet.solana.com | ❌ 超时（需代理） |
| **solana-rpc.publicnode.com** | ✅ 主网 |
| **solana-testnet-rpc.publicnode.com** | ✅ 测试网 |

`RPCEndpoint.custom(URL, URL, network)` 构造自定义端点，与 Binance vision 同属「官方/公共镜像直连」数据策略。

## 5. 集成踩坑记录（工程价值）

1. **发布仓库**：SolanaKt 仅在 JitPack；根 artifact 是空聚合 POM，真实代码在子模块
   `com.github.metaplex-foundation.SolanaKt:solana`
2. **BouncyCastle 冲突**：SolanaKt 携带 bcprov-jdk15to18，与 web3j 的 jdk18on 重复类 →
   排除旧版统一 1.80
3. **API 风格**：扩展函数 + Kotlin `Result`（`Api.getBalance()`），与官网文档示例不同；
   依赖解析以 sources jar 反编译为准（`toPublickKey` 的库内拼写也照实使用）
4. **网络类**：`HttpNetworkingRouter` 基于 HttpURLConnection（非 OkHttp），协程取消
   通过 `continuation.invokeOnCancellation { disconnect() }` 桥接

## 6. 当前边界（MVP）

- ✅ SOL 余额 / Send SOL / 签名广播 / 状态轮询（均已实现）
- ⏳ SPL Token 余额与转账：SolanaKt 已有 Token Program 支持，Phase 6+ 按需接入
- ⏳ WalletConnect：签名层就绪，会话层受网络环境限制（见 architecture.md 选型表）
