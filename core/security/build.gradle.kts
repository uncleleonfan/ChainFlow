// =============================================================================
// :core:security 模块
// 职责（PRD §13 安全要求）：
//   1. Android Keystore 生成硬件级加密密钥
//   2. 用该密钥加密钱包敏感数据（助记词/私钥）后落盘 —— 明文永不落盘
//   3. BiometricPrompt 封装（指纹/面容）
//   4. 安全防护：剪贴板超时清空、敏感页截屏阻断
// 安全红线（对应 threat-model）：
//   - 本模块输出的日志不得包含任何密钥明文
//   - 加密数据只能存入 EncryptedPrefs，禁止 SharedPreferences 明文
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.core.security"

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

    // AGP 9 内置 Kotlin：编译选项统一走 kotlin 块
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(project(":core:common")) // AppError / Result 模型

    // ---- AndroidX ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // ---- 生物识别 ----
    implementation(libs.androidx.biometric)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
}
