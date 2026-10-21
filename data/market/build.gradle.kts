// =============================================================================
// :data:market 模块
// 职责：行情数据仓库 —— 组合 CoinGecko REST（列表/OHLC/趋势）
//      与 Binance WebSocket（实时价格，PRD §10 数据流）
//      输出统一 Flow 供 UI 消费：MarketApi → WebSocket → Manager → Flow → StateFlow → VM
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.data.market"

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
    implementation(project(":core:common"))
    implementation(project(":core:network"))

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- ProcessLifecycleOwner：WS 连接绑定应用前后台生命周期（非页面） ----
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)

    // ---- 序列化（解析 Binance WS JSON 帧） ----
    implementation(libs.kotlinx.serialization.json)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
