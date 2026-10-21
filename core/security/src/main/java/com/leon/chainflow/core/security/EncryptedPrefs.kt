package com.leon.chainflow.core.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密偏好存储（钱包密文的持久化容器）
 *
 * 为什么用 SharedPreferences 而不是 Room？
 * - 钱包密文是「单条、小体积、读写低频」数据，KV 存储更合适；
 * - Room 留给交易历史 / Token 列表等结构化数据（Phase 3+）；
 * - 虽然 SP 本身不加密，但存入的值已经过 [KeyStoreCipher] 加密 ——
 *   即使设备被 root 后导出该文件，攻击者拿到的也只是密文（且 Keystore 密钥不可导出）。
 *
 * 存储格式：Base64(iv) + "." + Base64(ciphertext)
 * 用 "." 分隔 IV 与密文，解密时拆开配对还原。
 */
@Singleton
class EncryptedPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: KeyStoreCipher,
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chainflow_secure_prefs", Context.MODE_PRIVATE)

    /** 存储键定义（集中管理，避免字符串散落） */
    private object Keys {
        const val ENCRYPTED_MNEMONIC = "encrypted_mnemonic"
        const val WALLET_ADDRESS = "wallet_address" // 地址非机密，明文存储便于快速读取
        const val WALLET_CREATED_AT = "wallet_created_at"
    }

    /**
     * 保存加密后的助记词（创建 / 导入钱包时调用）
     *
     * @param mnemonic BIP-39 助记词明文 —— 注意：本方法内部立即加密，
     *                 调用方负责在调用后不再持有明文引用
     */
    fun saveMnemonic(mnemonic: String) {
        val encrypted = cipher.encrypt(mnemonic.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(Keys.ENCRYPTED_MNEMONIC, encodeBase64(encrypted))
            .putLong(Keys.WALLET_CREATED_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * 读取并解密助记词（恢复钱包 / 导出备份时调用）
     *
     * @return 助记词明文；从未创建过钱包或数据损坏时返回 null
     */
    fun loadMnemonic(): String? {
        val encoded = prefs.getString(Keys.ENCRYPTED_MNEMONIC, null) ?: return null
        return runCatching {
            val encrypted = decodeBase64(encoded)
            String(cipher.decrypt(encrypted), Charsets.UTF_8)
        }.getOrNull() // 解密失败（篡改/密钥变更）按「无钱包」处理，不崩溃
    }

    /** 保存钱包地址（公开信息，非机密） */
    fun saveAddress(address: String) {
        prefs.edit().putString(Keys.WALLET_ADDRESS, address).apply()
    }

    /** 读取钱包地址；无钱包时返回 null */
    fun loadAddress(): String? = prefs.getString(Keys.WALLET_ADDRESS, null)

    /** 是否已存在钱包（用于启动时判断进入「创建/导入」还是「钱包首页」） */
    fun hasWallet(): Boolean = prefs.contains(Keys.ENCRYPTED_MNEMONIC)

    /** 钱包创建时间戳（用于「备份提醒」等场景） */
    fun walletCreatedAt(): Long = prefs.getLong(Keys.WALLET_CREATED_AT, 0L)

    /**
     * 删除钱包（危险操作 —— 需由 UI 层二次确认后调用）
     * 场景：用户主动「忘记钱包」或迁移设备后清除本地数据
     */
    fun clearWallet() {
        prefs.edit()
            .remove(Keys.ENCRYPTED_MNEMONIC)
            .remove(Keys.WALLET_ADDRESS)
            .remove(Keys.WALLET_CREATED_AT)
            .apply()
    }

    // ---- Base64 编解码（ByteArray → 可存储的 String） ----

    private fun encodeBase64(data: KeyStoreCipher.EncryptedData): String =
        Base64.encodeToString(data.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(data.ciphertext, Base64.NO_WRAP)

    private fun decodeBase64(encoded: String): KeyStoreCipher.EncryptedData {
        val parts = encoded.split(".")
        require(parts.size == 2) { "密文格式损坏" }
        return KeyStoreCipher.EncryptedData(
            iv = Base64.decode(parts[0], Base64.NO_WRAP),
            ciphertext = Base64.decode(parts[1], Base64.NO_WRAP),
        )
    }
}
