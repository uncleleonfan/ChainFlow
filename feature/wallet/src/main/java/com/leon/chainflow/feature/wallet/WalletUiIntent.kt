package com.leon.chainflow.feature.wallet

import com.leon.chainflow.core.common.mvi.UiIntent

/**
 * 钱包页用户意图（PRD §12-16）
 */
sealed interface WalletUiIntent : UiIntent {
    /** 进入页面：检查是否有钱包并加载资产 */
    data object Load : WalletUiIntent

    /** 点击「创建钱包」→ 生成助记词进入备份阶段 */
    data object CreateWalletClicked : WalletUiIntent

    /** 助记词备份页：「我已抄写并安全保存」勾选状态变化 */
    data class BackupConfirmChanged(val checked: Boolean) : WalletUiIntent

    /** 备份确认页点击「完成」→ 正式落盘创建钱包 */
    data object BackupConfirmed : WalletUiIntent

    /** 点击「导入钱包」→ 进入导入输入页 */
    data object ImportClicked : WalletUiIntent

    /** 导入输入变化 */
    data class ImportInputChanged(val text: String) : WalletUiIntent

    /** 提交导入 */
    data object ImportSubmitted : WalletUiIntent

    /** 返回上一阶段（页面内部返回） */
    data object BackClicked : WalletUiIntent

    /** 点击「Receive」→ 复制地址并提示 */
    data object ReceiveClicked : WalletUiIntent

    /** 点击「Send」→ 进入发送流程（Phase 2 简化：地址+金额对话框） */
    data object SendClicked : WalletUiIntent

    /** Send 表单：收款地址输入 */
    data class SendToChanged(val value: String) : WalletUiIntent

    /** Send 表单：金额输入 */
    data class SendAmountChanged(val value: String) : WalletUiIntent

    /** Send 表单：点击「确认发送」（触发生物识别 → 签名 → 广播） */
    data object SendConfirmed : WalletUiIntent

    /** 轮询刷新交易状态 / 手动刷新余额 */
    data object RefreshBalance : WalletUiIntent

    /** 点击「删除钱包」（本地移除，不动链上资产）—— 切换二次确认弹窗显隐 */
    data object DeleteWalletClicked : WalletUiIntent

    /** 删除弹窗内「我已备份」勾选变化（与创建流程的勾选是两个独立状态） */
    data class DeleteBackupConfirmChanged(val checked: Boolean) : WalletUiIntent

    /** 在确认弹窗中点击「确认删除」（危险操作，仅当用户勾选已备份时可用） */
    data object DeleteWalletConfirmed : WalletUiIntent
}
