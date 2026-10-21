// =============================================================================
// :core:network 模块
// 职责（PRD §10 实时行情数据流）：
//   1. Retrofit/OkHttp 基础设施（CoinGecko REST 行情）
//   2. WebSocketManager：连接 / 重连（指数退避）/ 心跳 / 订阅管理 /
//      生命周期感知 / 去重 / 陈旧数据检测（PRD §10.2 全项）
//   3. kotlinx-serialization JSON 解析
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.core.network"

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
    api(project(":core:common")) // AppConstants（超时/退避参数）/ Result 模型

    // ---- 网络 ----
    api(libs.okhttp)                    // api：WebSocketManager 暴露 OkHttp 类型
    implementation(libs.okhttp.logging)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)

    // ---- 序列化 ----
    implementation(libs.kotlinx.serialization.json)

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp) // MockWebServer 同包
}
