// =============================================================================
// :core:ui 模块
// 职责：全局 Dark Trading Terminal 主题 + 通用 Compose 组件（价格文本、状态页等）
// 依赖 :core:common 是为了复用其中的 MVI 类型（UiState 等）
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler) // Compose 编译器插件（版本随 Kotlin）
}

android {
    namespace = "com.leon.chainflow.core.ui"

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
        // 启用 Compose：本模块提供 @Composable 组件
        compose = true
    }
}

dependencies {
    api(project(":core:common")) // 通用组件可能需要引用 MVI 状态类型

    // ---- Compose（版本由 BOM 统一管理） ----
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material.icons)

    // ---- 调试工具（Previews 支持） ----
    debugApi(libs.compose.ui.tooling)
    api(libs.compose.ui.tooling.preview)
}
