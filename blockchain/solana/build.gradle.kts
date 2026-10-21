// =============================================================================
// :blockchain:solana 模块
// 职责（PRD §23 Solana 第二阶段）：
//   1. 钱包派生：BIP-39 助记词 → ed25519 Keypair（Solana CLI 路径 m/44'/501'/0'/0'）
//   2. RPC：SOL 余额 / 最近区块哈希 / 发送交易 / 确认状态（publicnode 直连）
//   3. 签名与广播：SystemProgram.transfer（SOL 转账）
//   4. SPL Token：余额查询（Phase 5 基础集）
// 库选型：SolanaKt（KMP，内置 BIP39，与 EVM 侧同一套助记词体验）
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.blockchain.solana"

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
    api(project(":blockchain:core"))
    implementation(project(":core:common"))

    // ---- Solana（SolanaKt：KMP，RPC/签名/BIP39 全内置） ----
    // 排除旧版 BouncyCastle：web3j 已引入 bcprov-jdk18on，
    // 旧版 jdk15to18 与之产生 Duplicate class 冲突（统一用 18on 新版）
    api(libs.solanakt) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15to18")
    }

    // 显式引入新版 BC（TweetNaclFast 依赖的加密原语由 18on 提供）
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
