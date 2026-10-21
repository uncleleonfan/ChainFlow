# 钱包安全架构（core:security + 助记词体系）

> 对应 PRD §12/§13/§28。模块路径 `core/security/`，配套 [threat-model.md](threat-model.md)。

## 1. 威胁模型驱动的安全设计

钱包安全的本质是回答一个问题：**助记词/私钥在什么位置、能被谁读到？**
ChainFlow 的答案是一条「明文最小暴露」链路：

```
明文助记词生命周期（全程 < 1 秒的内存窗口）
 生成（创建流程）──→ 展示备份 ──→ 立即加密落盘 ──→ 内存引用清除
 使用（签名时）  ──→ 解密 ──→ 派生密钥 ──→ 签名 ──→ 丢弃（不缓存）
```

**永不发生**：明文写 SharedPreferences/Room、进 Logcat、上传网络、进 Analytics。

## 2. KeyStoreCipher —— 硬件级加密

`core/security/.../KeyStoreCipher.kt`

### 2.1 加密架构

```
Android Keystore（TEE / StrongBox，密钥材料不可导出）
   └── AES-256 主密钥（alias: chainflow_master_key）
          └── AES/GCM/NoPadding
                 ├── 密文（ciphertext + 128bit 认证标签）
                 └── IV（12 字节，每次加密随机生成）
```

### 2.2 关键属性与原理

| 属性 | 值 | 为什么 |
|---|---|---|
| `PURPOSE_ENCRYPT \| DECRYPT` | 仅加解密 | 密钥不能被导出为其它用途 |
| `BLOCK_MODE_GCM` | 认证加密 | 自带完整性校验：密文被篡改 → 解密直接抛 AEADBadTagException（而非解出垃圾） |
| `setRandomizedEncryptionRequired(true)` | 强制随机 IV | 同一明文每次密文不同，抗重放/模式分析 |
| 密钥生成位置 | Keystore 内部 | 即使 root + dump 应用进程，也拿不到原始密钥字节 |

### 2.3 为什么不用 EncryptedSharedPreferences（security-crypto）？

功能等价（其内核同样是 Keystore + AES/GCM + MasterKey），但手写实现：
1. 明确控制 IV/密文格式（`Base64(iv).Base64(ciphertext)`），跨版本可迁移
2. 不引入额外依赖，且库本身已进入维护模式
3. 面试/展示价值：每一步都可解释

## 3. EncryptedPrefs —— 密文持久化

`core/security/.../EncryptedPrefs.kt`

### 3.1 存储职责划分

| 数据 | 存储 | 理由 |
|---|---|---|
| 加密助记词 | EncryptedPrefs（SP，密文） | 单条小体积 KV；明文永不落盘 |
| 钱包地址 | EncryptedPrefs（SP，明文） | 公开信息，快速读取 |
| 网络偏好（链/Testnet） | DataStore（data:wallet） | 真正的偏好数据，响应式 |
| 交易历史 | Room（data:transaction） | 结构化+查询 |

### 3.2 容错语义

- `loadMnemonic()` 解密失败（密文损坏/密钥重置）→ 返回 `null` 按「无钱包」处理，**不崩溃**
- `saveMnemonic()` 先加密再落盘：任何中断都不会产生明文残留

## 4. BiometricAuthenticator —— 签名前置认证

`core/security/.../BiometricAuthenticator.kt`

- `suspendCancellableCoroutine` 封装官方回调式 API → 调用方以返回值 `Boolean` 获得结果
- `invokeOnCancellation { prompt.cancelAuthentication() }`：协程取消（页面退出）时同步关闭系统弹窗
- Authenticators = `BIOMETRIC_WEAK | DEVICE_CREDENTIAL`：指纹/面容 + PIN 兜底（本地数据保护级别足够，非支付级）
- 无生物识别设备：UI 层直接放行（MVP 决策，Phase 6 可加应用内 PIN）

**触发点**：Send 确认、Swap 确认 —— 拦在「解锁钱包」之前，即签名永远发生在认证之后。

## 5. SecurityGuard —— 三类运行时防护

`core/security/.../SecurityGuard.kt`

| 防护 | 实现 | 原理 |
|---|---|---|
| 截屏阻断 | `FLAG_SECURE`（助记词页 on/off 切换） | 系统级禁止该窗口出现在截屏/录屏/最近任务缩略图 |
| 剪贴板超时 | 复制地址后 45s `clearPrimaryClip()` | 缩短后台应用可窃读的窗口期 |
| 日志守卫 | `logSecure()` 空实现 | 编译期强制走此入口 → 任何构建都不输出敏感日志 |

## 6. 助记词派生体系（双链同源）

```
BIP-39 12 词（128bit entropy）
   ├── EVM:    PBKDF2-HMAC-SHA512(2048轮) → BIP-32 主密钥
   │            └─ m/44'/60'/0'/0/0 → secp256k1 → 0x 地址（web3j）
   └── Solana: SLIP-10 ed25519
                └─ m/44'/501'/0'/0' → ed25519 → Base58 地址（SolanaKt）
```

用户视角：**一份备份，恢复全部链**。安全视角：两条派生路径密钥空间完全独立，单链泄露不影响另一链。

## 7. 创建流程的「先展示后落盘」语义

`WalletViewModel.startCreateFlow()` 只生成助记词放内存（UiState.pendingMnemonic）；
用户完成备份勾选后 `confirmBackup()` 才真正持久化。
中断（退出页面/杀进程）不会产生「已保存但用户从未备份」的钱包 —— 这是防丢币的关键时序设计。
