// =============================================================================
// :blockchain:evm 模块
// 职责：基于 web3j 的 EVM 链实现 ——
//   1. 钱包派生：BIP-39 助记词 → BIP-32/44 HD 派生 → 私钥/地址
//   2. RPC：JSON-RPC over OkHttp（balance / gas / nonce / sendRawTransaction / receipt）
//   3. ERC-20：balanceOf / transfer / approve / allowance / decimals / symbol
//   4. 本地签名：交易构建 → 离线签名（私钥不进入网络层）
// 安全红线：签名只在本地完成，原始私钥绝不出现在任何日志 / 网络请求中
// =============================================================================
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.leon.chainflow.blockchain.evm"

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
    api(project(":blockchain:core")) // 链抽象接口
    implementation(project(":core:common"))

    // ---- Web3 ----
    // 排除说明：web3j:core 默认拖入大量不需要的传递依赖 ——
    //   AWS KMS（云签名，我们只用本地签名）、vertx/netty（其内置异步 HTTP，我们用 OkHttp）。
    // 排除后可显著减小 APK 并避免 META-INF 打包冲突。
    api(libs.web3j.core) { // api：上层模块需要复用 Credentials 等类型
        exclude(group = "software.amazon.awssdk")
        exclude(group = "io.vertx")
        exclude(group = "io.netty")
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "org.apache.httpcomponents")
    }
    // web3j 需要的最小 JSON 能力：org.json 随 Android 平台提供，无需额外依赖

    // ---- 网络（web3j 底层使用 OkHttp） ----
    implementation(libs.okhttp)

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)

    // ---- Hilt ----
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
