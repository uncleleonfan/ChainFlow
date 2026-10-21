// =============================================================================
// :feature:activity 模块
// 职责：活动页（交易历史列表 / 状态 / Block Explorer 跳转）—— Phase 4 实现数据
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.feature.activity"

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
    implementation(project(":blockchain:core"))  // Chain（Explorer URL）
    implementation(project(":data:transaction")) // 交易历史仓库

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
