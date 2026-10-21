// =============================================================================
// :blockchain:core 模块
// 职责：链无关（chain-agnostic）的抽象层 —— 定义统一的地址 / 余额 / 交易模型，
// 让 UI 与 data 层不感知具体链（EVM / Solana）的差异（PRD §5：Blockchain Layer 与 UI 解耦）
// 纯 Kotlin + 协程，无 Android UI 依赖，保证可独立 JVM 单元测试
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.leon.chainflow.blockchain.core"

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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    api(project(":core:common")) // Result / AppError / 常量

    api(libs.kotlinx.coroutines.android)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
