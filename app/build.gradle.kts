// =============================================================================
// :app 应用入口模块
// 职责（刻意保持精简，不含业务逻辑）：
//   1. ChainFlowApplication（Hilt 容器启动）
//   2. MainActivity（唯一 Activity，Compose 承载）
//   3. ChainFlowNavHost + 底部 5 Tab 导航（PRD §6）
// =============================================================================
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.leon.chainflow"
        minSdk {
            version = release(26) // PRD §4 要求 minSdk >= 26
        }
        targetSdk {
            version = release(37)
        }
        versionCode = 1
        versionName = "0.1.0" // Phase 1：工程基座

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true // 启用 Jetpack Compose
    }

    // web3j 传递依赖（vertx/netty）携带重复的 META-INF 索引文件，打包时排除
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/DEPENDENCIES" // httpcomponents（web3j 传递）
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
        }
    }
}

dependencies {
    // ---- 内部模块（app 只做组装，所有业务都在 feature/data/blockchain 层） ----
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":feature:home"))
    implementation(project(":feature:market"))
    implementation(project(":feature:swap"))
    implementation(project(":feature:wallet"))
    implementation(project(":feature:activity"))
    implementation(project(":feature:token"))    // Token 详情页（K线/实时价）
    implementation(project(":data:market"))       // BinancePriceStream：应用级 WS 生命周期管理
    implementation(project(":feature:settings"))  // 设置页（网络切换，PRD §32）

    // ---- AndroidX / Compose ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process) // ProcessLifecycleOwner：WS 前后台管理

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 调试工具 ----
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.tooling.preview)

    // ---- 测试 ----
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
