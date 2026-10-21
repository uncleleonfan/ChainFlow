// =============================================================================
// :feature:token 模块
// 职责：Token 详情页（PRD §9）—— 实时价格 / OHLC K 线图 / 市场统计
//      K 线使用 Compose Canvas 自绘（无三方图表库，体现工程能力）
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.feature.token"

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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:network")) // MarketDto / OhlcDto 模型
    implementation(project(":data:market"))  // 行情仓库

    // ---- Compose ----
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
