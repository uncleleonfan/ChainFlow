# Swap 链路（data:transaction + feature:swap）

> 对应 PRD §17/§18。模块路径 `data/transaction/.../SwapRepository.kt` + `feature/swap/`。
> 前置阅读：[evm-transaction.md](evm-transaction.md)（交易引擎）、[websocket.md](websocket.md)（行情）。

## 1. 边界：不自建 DEX

MVP 明确不做 AMM/路由算法（PRD §36），全部委托 **Li.Fi 聚合器**：

| 环节 | 责任方 | 说明 |
|---|---|---|
| 报价 / 路由 / 价格影响 | Li.Fi `GET /quote` | 免 Key，国内直连可用 |
| 交易 calldata 构建 | Li.Fi `GET /step/transaction` | 返回 `transactionRequest`（to/data/value/gas/1559 费率） |
| nonce 补齐 / 签名 / 广播 | 本机 `EvmTransactionService` | **私钥永不离开设备**（PRD §13.1 红线） |
| 上链状态 | RPC receipt 轮询（主）+ Li.Fi `GET /status`（辅） | PRD §20 |

## 2. 端到端时序

```
用户输入金额
   │  (600ms 防抖)
   ▼
SwapRepository.getQuote ──→ Li.Fi /quote
   │  SwapQuote（toAmount / rate / gasUsd / toolName）
   ▼
UI 展示 Rate / Gas / Route
   │  点击 Swap（PRD §17）
   ▼
SwapUiEvent.RequireBiometric ──→ BiometricPrompt（UI 层）
   │  通过
   ▼
WalletRepository.unlock()（解密 → 派生，不缓存私钥）
   ▼
SwapRepository.getStepTransaction ──→ Li.Fi /step/transaction
   ▼
EvmTransactionService.sendPrebuiltTransaction
   ├─ eth_getTransactionCount（pending）
   ├─ EIP-1559（有 1559 费率）或 Legacy 回退
   ├─ TransactionEncoder.signMessage（本地签名，EIP-155）
   └─ eth_sendRawTransaction → txHash
   ▼
TransactionRepository.recordAndMonitor（Room 落 PENDING + 应用级轮询）
   ▼
Activity 页 Flow 自动刷新 CONFIRMED / FAILED
```

## 3. Li.Fi 协议要点

### 3.1 报价参数

| 参数 | 取值 | 陷阱 |
|---|---|---|
| `fromChain` / `toChain` | `chain.activeChainId(isMainnet)` | **必须按环境区分**（Base=8453 / Base Sepolia=84532） |
| `fromToken` / `toToken` | 合约地址；原生币用 `0x000…000` | USDC 地址随网络不同（见 §3.3） |
| `fromAmount` | 最小单位（wei 等），字符串 | 精度必须按该 Token 的 `decimals` 换算 |
| `fromAddress` | 当前钱包地址 | 影响路由可用性 |
| `slippage` | **小数比例**（0.005 = 0.5%） | 不是 bps！UI 用 bps（50）需 `bps / 10_000` |

### 3.2 预构建交易参数

`/step/transaction` 要求把 `/quote` 返回的整个 step 对象序列化后作为 `step` query 传回。实现上：

1. `LiFiQuoteDto` 重新序列化为 `JsonObject`
2. 向 `action` 注入 `fromAddress` / `toAddress`（单链 Swap 收款人即自己）
3. URL 编码后作为 `step` 参数提交

这一步的产物 `LiFiTransactionRequest` 已含 `to/data/value/gasLimit/maxFeePerGas/maxPriorityFeePerGas`，本地只需补 nonce。

### 3.3 地址常量（Demo 交易对 ETH ↔ USDC）

| 常量 | 值 | 说明 |
|---|---|---|
| `NATIVE_TOKEN` | `0x0000…0000` | Li.Fi 原生币占位地址 |
| `BASE_USDC_MAINNET` | `0x833589fC…bdA02913` | Base 官方 USDC |
| `BASE_USDC_TESTNET` | `0x036CbD53…f3dCF7e` | Base Sepolia USDC（**与主网不同**） |

`usdcAddress(isMainnet)` 统一取址 —— Testnet 与 Mainnet 混用会导致「报价成功但交易 revert」。

### 3.4 404 语义转译（可操作性优先）

Li.Fi 404 = 无可用路由。Testnet 上几乎必然（测试网 DEX 流动性枯竭），因此：

```
404 + Testnet → "Testnet 流动性不足，无法获取报价\n请在设置中切换 Mainnet 后重试"
404 + Mainnet → "暂无可用路由，请调整金额后重试"
```

日志只写 `error.toString()`，不含地址/金额以外的敏感信息（实际也不含私钥）。

## 4. 报价模型映射（`LiFiQuoteDto → SwapQuote`）

| 字段 | 来源 | 处理 |
|---|---|---|
| `fromSymbol` / `toSymbol` | `action.fromToken/toToken.symbol` | 缺失即抛错（进入 `AppError.Unknown`） |
| `toAmount` | `estimate.toAmount` | `BigDecimal(raw).movePointLeft(toToken.decimals)` |
| `fromAmount` | 用户输入 | 原样保留，用于算汇率 |
| `rate` | `toAmount / fromAmount` | 8 位小数，`HALF_UP` |
| `toolName` | `toolDetails.name ?: key` | 展示路由来源（如 Nordstern Finance） |
| `gasCostUsd` | `estimate.gasCosts[0].amountUSD` | 仅展示，不参与签名 |
| `raw` | 原始 DTO | 换取 step 交易时整体回传 |

**为什么保留 `raw`**：Li.Fi 的报价与预构建交易强绑定（路由 id、手续费结构、Permit2 数据都在里面），只回传金额会拿不到可执行 calldata。

## 5. 执行链路细节

### 5.1 费率与字段解析

```kotlin
// Li.Fi 可能返回十进制或 0x 十六进制字符串
BigInteger(it.removePrefix("0x"), if (it.startsWith("0x")) 16 else 10)
```

| 字段 | 缺省 | 后果 |
|---|---|---|
| `value` | `BigInteger.ZERO` | 纯 ERC-20 Swap 时 value 必须为 0 |
| `gasLimit` | `"200000"` | 兜底值；路由复杂时可能不足（见 §7） |
| 1559 费率 | `null` → Legacy 回退 | 实时拉 `eth_gasPrice` |

### 5.2 UI 状态与防抖

`SwapUiState` 只保存原始输入与报价，展示值全部是**派生属性**（`receiveAmount/rateText/gasText`），避免状态冗余。

报价触发规则：
- 金额输入：`delay(600)` 防抖，仅最后一次请求
- 方向切换 / 滑点变更：立即重新报价
- 非法/空金额：不请求（`toDoubleOrNull() <= 0` 直接返回）

滑点档位：`10 / 50 / 100 / 300 bps`（0.1% / 0.5% / 1% / 3%）。

### 5.3 执行前置校验

| 校验 | 位置 | 行为 |
|---|---|---|
| 是否有报价 | `SwapViewModel.onSwapConfirmed` | Snackbar「请先输入金额获取报价」 |
| 是否执行中 | 同上 | 直接 return（防重复点击） |
| 钱包可解锁 | `executeSwapAfterBiometric` | 「钱包解锁失败」 |
| step 是否含 to/data | 同上 | `AppError.TransactionFailed("获取交易数据失败")` |

### 5.4 落库与监控

```kotlin
transactionRepository.recordAndMonitor(
    txHash, type = "SWAP", chain, environment,
    fromSymbol = quote.fromSymbol, fromAmount = quote.fromAmount.toPlainString(),
    toSymbol = quote.toSymbol, toAmount = quote.toAmount.toPlainString(),
    counterparty = txReq.to,     // 路由合约地址
)
```

注意：`toAmount` 记录的是**报价预估**而非实际到手数量（实际值需要解析 receipt 里的 Transfer 日志或查 Li.Fi `receiving`，Phase 6 再做）。

## 6. 正确性要点（踩坑记录）

1. **chainId 双值**：EIP-155 签名里的 chainId 与目标网络不符 → 节点直接拒绝（invalid chain id）。`Chain.activeChainId(isMainnet)` 是唯一取值入口。
2. **slippage 单位**：Li.Fi 要小数比例，传 50 会被当成 5000% 直接拒绝（400）。
3. **USDC 地址分环境**：Testnet 用主网地址会先报「无路由」，若同一链号下混用则更隐蔽地 revert。
4. **step 必须整体回传**：只传 `fromAmount/toAmount` 会 400/422。
5. **JSON 解析器**：`SwapRepository` 内部用 `Json.Default` 做 DTO→JsonObject 往返（不是注入的宽松解析器），因此 DTO 字段必须完整声明 —— 新增字段时两边都要改。

## 7. 当前边界（MVP）

- ⏳ **未执行 approve / Permit2 授权前置步骤**：当前只签主交易。`ETH → USDC`（卖原生币）不需要授权，可完整跑通；`USDC → ETH`（卖 ERC-20）在 allowance 不足时会 revert。修复路径：检查 `Erc20Client.allowance()` → `approve()` 并等待确认，或在 Li.Fi 响应中解析 approval transaction。
- ⏳ **价格影响（Price Impact）未展示**：PRD §17 UI 有此行，Li.Fi quote 的 `estimate` 未直接给出，需按流动性/中间价自算，MVP 省略。
- ⏳ **报价过期未校验**：报价后长时间停留再点确认，可能已滑出容忍范围（链上会 revert）。建议记录 `quoteTimestamp`，超过 15s 强制重报价。
- ⏳ **Token 选择器未接入 UI**：可交易币种由 `SwapRepository` 的静态白名单（`findToken`：ETH / WETH / USDC / DAI / cbBTC / AERO）决定；从 Token 详情页「Swap」进入会带上该币种符号（路由 `swap?from=ETH`），不在白名单的币种（行情列表里多数 CEX 币不在 Base 链上）保持默认交易对并 Snackbar 提示。Token 选择器（Li.Fi `/token`、`/balance` 已封装但未接入）为下一阶段。
- ✅ **`fromAmount` 精度**：`getQuote` 按 `SwapToken.decimals` 换算 `fromAmountRaw`（USDC=6、cbBTC=8 不再被当作 18 位）。
- ⏳ **无 MEV/滑点链上保护**：依赖 Li.Fi 路由与 slippage 参数，PRD §36 已明确 MVP 不做。
- ⏳ **`getStatus` 未接入 UI**：跨链/桥接场景才需要聚合器状态，单链 Swap 以 RPC receipt 为准。

## 8. 手工验证清单（Base 主网 Demo，PRD §33）

1. Settings 切到 `Base + Mainnet`（必须经过风险弹窗）
2. 入金少量 ETH
3. 输入 `0.001` → 600ms 后出现 Rate / Gas / Route
4. 点击 Swap → 生物识别 → 广播成功提示（txHash 前 18 位）
5. Activity 页立即出现 `SWAP / PENDING`，数秒后变 `✓ 已确认`
6. 点击该行 → `basescan.org/tx/<hash>` 可查
