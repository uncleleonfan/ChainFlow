// =============================================================================
// :feature:wallet 模块
// 职责：钱包页（资产总览 / 收发 / 创建-导入钱包）—— Phase 2 实现
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.feature.wallet"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk {
            version = release(26)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // ---- 内部模块依赖 ----
    implementation(project(":core:common")) // MVI 基类
    implementation(project(":core:ui"))     // 主题与通用组件
    implementation(project(":core:security")) // BiometricPrompt / SecurityGuard（截屏防护、剪贴板）
    implementation(project(":blockchain:core")) // 链 / 网络 / 交易状态模型
    implementation(project(":blockchain:evm"))  // EvmTransactionService / EvmWalletFactory（Send 与创建流程）
    implementation(project(":data:wallet"))     // 钱包仓库（创建/导入/余额/发送）
    implementation(project(":data:transaction")) // Send 交易历史记录

    // ---- Compose（版本由 core:ui 传递的 BOM 管理） ----
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.compose)       // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)     // viewModel()

    // ---- Hilt（ksp 编译期生成组件） ----
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose) // hiltViewModel()
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
}
