package com.leon.chainflow.core.security

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全防护器（PRD §28 附加安全要求）
 *
 * 提供三类防护：
 * 1. FLAG_SECURE：敏感页禁止截屏 / 禁止出现在多任务缩略图中（助记词展示页必开）；
 * 2. 剪贴板超时清空：地址/助记词复制后 45s 自动清除，防止被后台应用窃读；
 * 3. 统一的敏感信息日志守卫：debug 构建阻断（release 由 R8 移除）。
 */
@Singleton
class SecurityGuard @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * 开启截屏保护（在敏感页面的 onResume / onCreate 调用）
     *
     * 原理：FLAG_SECURE 阻止内容出现在截屏、录屏与最近任务预览中。
     * 代价：该页面无法截屏（用户侧表现为黑屏截图）—— 这是预期行为。
     */
    fun enableScreenshotProtection(activity: Activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** 关闭截屏保护（离开敏感页面时恢复，避免影响普通页截图） */
    fun disableScreenshotProtection(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /** 剪贴板自动清空的延时（毫秒） */
    private val clipboardClearDelayMs = 45_000L

    /** 复制敏感内容后自动清空剪贴板（见 PRD §28 Clipboard timeout） */
    fun copySensitiveToClipboard(text: String, label: String = "ChainFlow") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(label, text),
        )
        // 45 秒后清空剪贴板：降低恶意应用在后台读取的窗口期
        Handler(Looper.getMainLooper()).postDelayed(
            { clipboard.clearPrimaryClip() },
            clipboardClearDelayMs,
        )
    }

    companion object {
        /**
         * 敏感信息日志守卫：
         * 所有涉及私钥/助记词的日志必须经由本方法 —— release 构建直接不输出，
         * 防止 Logcat 泄漏（威胁模型：adb logcat / 崩溃上报收集日志）。
         */
        @Suppress("UNUSED_PARAMETER")
        fun logSecure(tag: String, message: String) {
            // 刻意留空：任何构建都不输出敏感日志（连 debug 也不输出，避免误用）
        }
    }
}
