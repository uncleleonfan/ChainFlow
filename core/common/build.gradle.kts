// =============================================================================
// :core:common 模块
// 职责：MVI 基类、协程调度器抽象、通用模型 —— 全项目最底层，不依赖任何业务模块
// 注意：纯 Kotlin 库（无 Android UI 依赖），保证可独立进行 JVM 单元测试
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.leon.chainflow.core.common"

    // 与主工程保持一致的 SDK 版本（AGP 9 新 DSL：块状语法）
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk {
            version = release(26) // PRD §4 要求 minSdk >= 26
        }
    }

    // AGP 9 内置 Kotlin：通过 kotlin 块配置编译选项（替代旧 kotlinOptions）
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // ---- 协程：MVI 基类中 ViewModel 依赖 StateFlow/Channel ----
    api(libs.kotlinx.coroutines.android)

    // ---- Lifecycle ViewModel：MviViewModel 的基类 + viewModelScope 来自这里 ----
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.lifecycle.viewmodel.compose)

    // ---- Hilt（仅注解，供 DispatcherProvider 等基础类使用） ----
    implementation(libs.hilt.android)

    // ---- 单元测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
