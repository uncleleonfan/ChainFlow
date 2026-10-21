package com.leon.chainflow.blockchain.evm.wallet

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * EVM 钱包工厂（PRD §12.1 创建流程）
 *
 * 实现标准 HD 钱包派生链路：
 * ```
 * SecureRandom 生成 128bit entropy
 *        ↓ BIP-39
 * 12 词助记词（展示给用户备份）
 *        ↓ BIP-39 PBKDF2（ passphrase="" ）
 * 512bit Seed
 *        ↓ BIP-32/44 派生路径 m/44'/60'/0'/0/0
 * EVM 私钥 → 公钥 → 0x 地址
 * ```
 *
 * 安全说明：
 * - SecureRandom 是密码学安全的随机源（不用 Random！）
 * - 派生过程全程内存操作，明文私钥不落盘、不打日志
 */
@Singleton
class EvmWalletFactory @Inject constructor() {

    /**
     * BIP-44 标准派生路径：m/44'/60'/0'/0/0
     * 44' = HD 钱包用途，60' = Ethereum 币种，0' = 首个账户，0/0 = 外部链首个地址
     */
    private val derivationPath = intArrayOf(
        44 or Bip32ECKeyPair.HARDENED_BIT,
        60 or Bip32ECKeyPair.HARDENED_BIT,
        0 or Bip32ECKeyPair.HARDENED_BIT,
        0,
        0,
    )

    /**
     * 创建全新钱包（PRD §12：Generate entropy → Mnemonic → Seed → Private Key → Address）
     *
     * @return 助记词 + 派生出的凭证（调用方负责：助记词展示备份后加密存储）
     */
    fun createNewWallet(): NewWallet {
        // 1. 生成密码学安全的 128bit 熵 → 12 词助记词（BIP-39）
        val entropy = ByteArray(ENTROPY_BITS_128 / 8)
        SecureRandom().nextBytes(entropy)
        val mnemonic = MnemonicUtils.generateMnemonic(entropy)

        // 2. 由助记词派生凭证（与导入路径复用同一逻辑，保证一致性）
        val credentials = credentialsFromMnemonic(mnemonic)
        return NewWallet(mnemonic = mnemonic, credentials = credentials)
    }

    /**
     * 由助记词恢复钱包（PRD §12 Import / Restore）
     *
     * @param mnemonic 用户输入的 12 词助记词（空格分隔，大小写不敏感）
     * @throws IllegalArgumentException 助记词校验失败（词不在 BIP-39 词表 / 校验和错误）
     */
    fun importFromMnemonic(mnemonic: String): Credentials {
        // 规范化：去除首尾空格 + 统一小写（BIP-39 词表全小写）
        val normalized = mnemonic.trim().lowercase()
        require(MnemonicUtils.validateMnemonic(normalized)) {
            "助记词校验失败：请检查单词拼写与顺序"
        }
        return credentialsFromMnemonic(normalized)
    }

    /**
     * BIP-39 助记词 → BIP-44 派生凭证（创建与导入共用的核心派生逻辑）
     *
     * 派生细节：
     * - MnemonicUtils.generateDeterministicSeed：PBKDF2-HMAC-SHA512，2048 轮迭代
     * - Bip32ECKeyPair.generateKeyPair(seed)：主密钥对
     * - deriveKeyPair(master, path)：按硬化/非硬化路径逐级派生
     */
    private fun credentialsFromMnemonic(mnemonic: String): Credentials {
        // PBKDF2-HMAC-SHA512（2048 轮）：助记词 → 512bit 种子（passphrase 为空串）
        val seed = MnemonicUtils.generateSeed(mnemonic, "")
        val masterKeyPair = Bip32ECKeyPair.generateKeyPair(seed)
        val derivedKeyPair = Bip32ECKeyPair.deriveKeyPair(masterKeyPair, derivationPath)
        return Credentials.create(derivedKeyPair)
    }

    /** 创建结果：助记词需要给用户备份，凭证用于派生地址 */
    data class NewWallet(
        val mnemonic: String,       // 12 词，空格分隔
        val credentials: Credentials, // 派生出的账户凭证（地址 credentials.address）
    )

    private companion object {
        const val ENTROPY_BITS_128 = 128 // 128bit → 12 词（PRD §12 要求 12-word）
    }
}
