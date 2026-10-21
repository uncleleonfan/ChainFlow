// =============================================================================
// :feature:swap 模块
// 职责：Swap 兑换页（报价 / 滑点 / 路由 / 授权 / 签名）—— Phase 4 实现
// 模板说明：其余 feature 模块（wallet/activity）结构与此完全一致
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.feature.swap"

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
    implementation(project(":core:security"))   // 生物识别
    implementation(project(":core:network"))    // LiFiStepDto（预构建交易模型）
    implementation(project(":blockchain:core")) // Chain 模型
    implementation(project(":blockchain:evm"))  // EvmTransactionService
    implementation(project(":data:wallet"))     // 解锁/网络
    implementation(project(":data:transaction")) // Swap + 交易历史仓库

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
