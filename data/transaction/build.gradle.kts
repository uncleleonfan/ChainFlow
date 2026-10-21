// =============================================================================
// :data:transaction 模块
// 职责（PRD §17-22）：
//   1. SwapRepository：Li.Fi quote → 预构建交易 →（上层签名广播）→ status
//   2. TransactionRepository：Room 持久化交易历史（Activity 页数据源）
//   3. Room 数据库（交易记录表）
// 安全红线：私钥不进入本模块（签名在 blockchain:evm 完成，这里只编排）
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.data.transaction"

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
    implementation(project(":core:network"))      // LiFiApi
    implementation(project(":blockchain:core"))   // Chain / 状态模型
    implementation(project(":blockchain:evm"))    // EvmTransactionService（回执监控）
    implementation(project(":data:wallet"))       // 读当前网络/地址

    // ---- Room（交易历史持久化） ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- 序列化（Li.Fi step 参数编码） ----
    implementation(libs.kotlinx.serialization.json)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
