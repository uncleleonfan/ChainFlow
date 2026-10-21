package com.leon.chainflow.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 生物识别认证器（PRD §13.2）
 *
 * 用途：进入钱包 / 签名交易前的二次认证（指纹 / 面容 / 设备 PIN 兜底）。
 *
 * 设计说明：
 * - 通过回调而非 suspend 封装，保持与官方 API 一致的心智模型；
 * - BIOMETRIC_WEAK 即可满足需求（本地数据保护场景，非支付级）；
 * - 认证失败/取消的错误码原样透传给 UI 决定提示方式。
 */
@Singleton
class BiometricAuthenticator @Inject constructor() {

    /**
     * 检查设备是否可用于生物识别
     *
     * @return true 表示可以弹出 BiometricPrompt（含「设备凭据」兜底能力）
     */
    fun canAuthenticate(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /**
     * 发起生物识别认证（挂起函数封装：认证结果以返回值形式给出）
     *
     * @param title 对话框标题（如「签名交易」）
     * @param subtitle 副标题（如「需要验证身份以继续」）
     * @param negativeText 取消按钮文案
     * @return true = 认证成功；false = 用户取消 / 失败次数超限等
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        negativeText: String = "取消",
    ): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        // Prompt 必须用 FragmentActivity 的 ContextCompat 主线程 Executor
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    // 认证成功：恢复协程并返回 true
                    if (continuation.isActive) continuation.resumeWith(Result.success(true))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 错误（用户取消 / 锁定 / 硬件故障）：视为认证未通过
                    if (continuation.isActive) continuation.resumeWith(Result.success(false))
                }

                // 单次失败不打断（系统会自动允许重试），不结束协程
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText) // BIOMETRIC_WEAK 必须提供负向按钮
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()

        prompt.authenticate(info)

        // 协程被取消时（如页面退出）取消认证对话框
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    private companion object {
        // 弱生物识别（指纹/面容）+ 设备凭据（PIN/图案）兜底
        val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
