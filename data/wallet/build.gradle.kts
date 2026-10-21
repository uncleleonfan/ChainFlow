// =============================================================================
// :data:wallet 模块
// 职责：钱包仓库 —— 组合「安全存储 + EVM 派生」提供上层用例：
//   创建钱包 / 导入钱包 / 解锁（解密私钥）/ 查询地址与余额
// 关键安全约束（PRD §13.1）：
//   - 私钥只在内存中以 Credentials 形式短暂存在，用完即弃
//   - 持久化只存「Keystore 加密后的密文」
//   - Private Key 不进入 Repository 以外的 API 层
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.data.wallet"

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
    implementation(project(":core:security"))
    implementation(project(":blockchain:core"))
    implementation(project(":blockchain:evm"))
    implementation(project(":blockchain:solana")) // SOL 余额查询（Phase 5）

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- DataStore（钱包元数据 / 用户网络偏好） ----
    implementation(libs.androidx.datastore.preferences)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
