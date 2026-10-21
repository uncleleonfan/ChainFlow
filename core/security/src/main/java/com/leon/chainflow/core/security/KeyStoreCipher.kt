package com.leon.chainflow.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore 加密器（PRD §13.1 Private Key 安全存储的核心）
 *
 * 加密架构：
 * ```
 * Android Keystore（硬件安全模块，密钥不可导出）
 *        └── AES-256 主密钥（别名 chainflow_master_key）
 *               └── AES/GCM 加密钱包数据（助记词 / 私钥）
 *                      └── 密文 + IV 落盘（EncryptedPrefs）
 * ```
 *
 * 安全特性：
 * 1. 密钥生成与使用都发生在 Keystore（TEE/StrongBox）内部，即使 App 被逆向
 *    也无法读取原始密钥字节；
 * 2. GCM 模式自带完整性校验（认证标签），密文被篡改时解密直接失败；
 * 3. setRandomizedEncryptionRequired = true（默认）：每次加密使用随机 IV，
 *    相同明文加密后的密文不同，防重放/模式分析。
 *
 * 注意：所有日志只记录操作结果，绝不记录明文或密钥材料。
 */
@Singleton
class KeyStoreCipher @Inject constructor() {

    /** Keystore 中主密钥的别名（全局唯一，代表本 App 的钱包加密密钥） */
    private val keyAlias = "chainflow_master_key"

    /** GCM 认证标签长度（位）：128 位是 NIST 推荐值 */
    private val gcmTagLengthBits = 128

    /** 加密结果：密文与 IV 一起返回（解密时 IV 必须与密文配对使用） */
    data class EncryptedData(val ciphertext: ByteArray, val iv: ByteArray)

    /**
     * 获取（或首次创建）Keystore 中的 AES 主密钥。
     * 密钥属性：
     * - PURPOSE_ENCRYPT | PURPOSE_DECRYPT：仅用于加解密
     * - BLOCK_MODE_GCM + PADDING_NONE：GCM 是流式认证加密，无需填充
     */
    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // 已存在则直接复用（密钥重建会导致历史密文永久无法解密）
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        // 首次使用：在 Keystore 内生成新密钥（密钥材料不离开安全硬件）
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256) // AES-256
                .setRandomizedEncryptionRequired(true) // 强制随机 IV
                .build(),
        )
        return generator.generateKey()
    }

    /**
     * 加密敏感数据（助记词 / 私钥）
     *
     * @param plaintext 明文（建议先转为 ByteArray，如 String.toByteArray）
     * @return [EncryptedData] 密文 + 本次加密的随机 IV
     */
    fun encrypt(plaintext: ByteArray): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedData(
            ciphertext = cipher.doFinal(plaintext),
            iv = cipher.iv, // GCM 随机生成的 12 字节 IV
        )
    }

    /**
     * 解密敏感数据
     *
     * @param encrypted 加密时保存的密文 + IV
     * @return 明文；密文被篡改 / 密钥不匹配时抛 AEADBadTagException
     */
    fun decrypt(encrypted: EncryptedData): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(gcmTagLengthBits, encrypted.iv))
        return cipher.doFinal(encrypted.ciphertext)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
