package com.leon.chainflow.feature.wallet

import com.leon.chainflow.blockchain.core.model.Chain
import com.leon.chainflow.core.common.mvi.UiState
import com.leon.chainflow.core.common.result.AppError

/**
 * 钱包页界面状态（PRD §12-16 完整流程）
 *
 * 页面形态由 [stage] 驱动：
 * - NO_WALLET：展示「创建 / 导入」引导
 * - BACKUP：展示 12 词助记词（敏感页，需 FLAG_SECURE）
 * - BACKUP_CONFIRM：备份确认（勾选「我已安全备份」）
 * - IMPORT：输入助记词导入
 * - OVERVIEW：钱包首页（总资产 + 余额 + Receive/Send 入口）
 */
data class WalletUiState(
    /** 当前页面阶段（见上注释） */
    val stage: Stage = Stage.NO_WALLET,
    /** 是否已有钱包（启动时检查） */
    val hasWallet: Boolean = false,
    /** 新建钱包生成的助记词（仅 BACKUP 阶段非空，展示后清除） */
    val pendingMnemonic: String? = null,
    /** 用户输入的导入助记词 */
    val importInput: String = "",
    /** 备份确认勾选状态 */
    val backupConfirmed: Boolean = false,
    /** 钱包地址（OVERVIEW 展示） */
    val address: String? = null,
    /** 原生币余额（ETH 计价字符串，OVERVIEW 展示） */
    val balance: String? = null,

    /** SOL 余额（同助记词派生的 Solana 地址，Phase 5） */
    val solBalance: String? = null,
    /** 当前链（展示网络名） */
    val chain: Chain = Chain.BASE,
    /** 是否 Mainnet（Testnet/Mainnet 徽章） */
    val isMainnet: Boolean = false,
    /** Send 表单：收款地址 */
    val sendTo: String = "",

    /** Send 表单：金额（字符串输入，提交时校验） */
    val sendAmount: String = "",

    /** Send 表单：手续费预估（ETH 计价，加载后展示） */
    val sendFeeEstimate: String? = null,

    /** 删除钱包确认弹窗是否可见（OVERVIEW 阶段，TopBar 菜单触发） */
    val showDeleteDialog: Boolean = false,

    /** 删除确认弹窗内「我已备份助记词」勾选（防止误删无法恢复） */
    val deleteBackupConfirmed: Boolean = false,

    /** 加载中标记 */
    val isLoading: Boolean = false,
    /** 错误信息（Snackbar 展示；null = 无错误） */
    val error: AppError? = null,
) : UiState {

    /** 钱包页内部阶段流转（与导航路由区分：这是页面内部状态机） */
    enum class Stage {
        NO_WALLET,        // 无钱包引导
        BACKUP,           // 展示助记词（敏感）
        BACKUP_CONFIRM,   // 备份确认勾选
        IMPORT,           // 导入输入
        OVERVIEW,         // 钱包总览（默认，有钱包时）
    }
}
