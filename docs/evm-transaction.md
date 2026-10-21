# EVM 交易引擎（blockchain:evm）

> 对应 PRD §19/§20/§21。模块路径 `blockchain/evm/`，4 个核心类。

## 1. 模块结构

```
blockchain/evm/
├── wallet/EvmWalletFactory.kt    助记词 → BIP-44 派生 → Credentials
├── rpc/EvmRpcClient.kt           JSON-RPC over OkHttp（最小方法集）
├── tx/EvmTransactionService.kt   交易引擎：构建→签名→广播→监控
└── erc20/Erc20Client.kt          ERC-20 ABI 编码 + eth_call
```

## 2. EvmWalletFactory —— HD 钱包派生

```
SecureRandom 128bit entropy
   → BIP-39 mnemonic（12 词）
   → generateSeed(mnemonic, "")：PBKDF2-HMAC-SHA512，2048 轮
   → Bip32ECKeyPair.generateKeyPair(seed)：BIP-32 主密钥对
   → deriveKeyPair(m/44'/60'/0'/0/0)：硬化路径逐级派生
   → Credentials（secp256k1 私钥 → keccak256 公钥 → 0x 地址）
```

导入校验：`MnemonicUtils.validateMnemonic()`（词表 + 校验和），失败即拒。
派生在 `Dispatchers.Default`（CPU 密集，PBKDF2 2048 轮）。

## 3. EvmRpcClient —— JSON-RPC 最小方法集

自实现而非 web3j HttpService 的原因：**透明可控**（超时/错误映射/方法覆盖都在本层），且避免 web3j core 的重依赖（已排除 vertx/netty/AWS）。

实现的方法（PRD §21 要求全集）：

| 方法 | 用途 | 关键点 |
|---|---|---|
| `eth_getBalance` | 原生币余额 | hex → BigInteger |
| `eth_chainId` | 链校验 | 防 Testnet/Mainnet 混连 |
| `eth_getTransactionCount` | nonce | **pending 模式**：含内存池交易，防 nonce 冲突 |
| `eth_gasPrice` / `eth_estimateGas` | 费率 | 组合出 FeeEstimate |
| `eth_sendRawTransaction` | 广播签名交易 | 返回 txHash |
| `eth_getTransactionReceipt` | 回执 | status 0x1/0x0 判定成败 |
| `eth_call` | 只读合约调用 | ERC-20 数据源 |

工程约束：单次调用 15s 超时（`AppConstants.RPC_TIMEOUT_MS`）；错误对象统一转 `AppError.Rpc`（保留 code 供日志）。

## 4. EvmTransactionService —— 交易引擎（PRD §20 状态机的实现载体）

### 4.1 状态机

```
Created → Signing → Signed → Broadcasting → PENDING ─┬→ CONFIRMED
                                    ↑（轮询 receipt） └→ FAILED
```

### 4.2 sendNativeTransfer（ETH 转账）

```
nonce(pending) + gasPrice + estimateGas
  → RawTransaction（Legacy 类型）
  → TransactionEncoder.signMessage(tx, activeChainId, credentials)  ← EIP-155
  → eth_sendRawTransaction → txHash
```

### 4.3 sendPrebuiltTransaction（Li.Fi 预构建，Swap 用）

Li.Fi 返回的 `transactionRequest` 已含 `to/data/gasLimit/maxFeePerGas/maxPriorityFeePerGas`，
本方法只补 nonce 并按费率类型分派：

- **EIP-1559**（字段齐全）：`createTransaction(chainId, nonce, gasLimit, to, value, data, maxPriorityFee, maxFee)` —— Base 等现代链默认
- **Legacy 回退**：实时拉 `eth_gasPrice` 构建

### 4.4 关键正确性：chainId 必须按环境区分（踩坑记录）

`Chain.activeChainId(isMainnet)`：EIP-155 签名嵌入的 chainId 若与目标网络不符，节点直接拒绝（invalid chain id）。曾因 Chain 枚举只有 mainnet chainId 导致 Base Sepolia 交易全部失败 —— 修复为每链双 chainId。

### 4.5 monitorTransaction —— 回执轮询

- 间隔 3s，超时 120s（`AppConstants.TX_POLL_*`）
- 超时返回 PENDING（用户可去浏览器查看），不误报失败
- `currentCoroutineContext().isActive`：调用方取消（页面销毁）即停止轮询

## 5. Erc20Client —— 手写 ABI 编码

不引入 web3j `Contract` 运行时代理，直接用 `DefaultFunctionEncoder` 编码 calldata：

| 方法 | 编码 | 执行方式 |
|---|---|---|
| balanceOf(address) | → uint256 | eth_call（只读） |
| decimals() / symbol() | → uint256 / string | eth_call |
| transfer(to, amount) | → bool | 作为交易 data 广播 |
| approve(spender, amount) | → bool | 交易（Swap 授权，Phase 4 预留） |
| allowance(owner, spender) | → uint256 | eth_call（授权前置检查） |

`symbol()` 的动态 string 返回值按 ABI 规则手工解码（32B offset + 32B length + payload）。

**Native vs ERC-20 的语义边界**（PRD §19）：原生币转账 value 携带金额、data 为空；ERC-20 转 value=0、金额编码在 transfer calldata —— 上层（Send/Swap）按 token 类型选路。
