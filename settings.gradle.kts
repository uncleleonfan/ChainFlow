pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // SolanaKt 仅发布在 JitPack
    }
}

rootProject.name = "ChainFlow"

// =============================================================================
// 多模块结构（对应 PRD §5 Clean Architecture 分层）
//   core/*       —— 基础能力层：通用工具 / 网络 / 数据库 / 安全 / UI 组件
//   blockchain/* —— 区块链层：链抽象 + EVM/Solana 实现（与 UI 完全解耦）
//   data/*       —— 数据层：Repository 实现（行情 / 钱包 / Token / 交易）
//   feature/*    —— 功能层：各自独立的页面模块（Feature 之间不互相依赖）
// =============================================================================

// 应用入口模块：只负责 Application / 导航 / 底部 Tab 组装
include(":app")

// ---- core 基础层 ----
include(":core:common")     // MVI 基类、协程调度器、通用模型
include(":core:ui")        // 主题（Dark Trading Terminal）、通用 Compose 组件

// ---- feature 功能层（Phase 1 先建骨架，后续 Phase 逐个填充） ----
include(":feature:home")     // 首页：市场总览 / Trending
include(":feature:market")   // 市场：Token 列表
include(":feature:swap")     // 兑换：Swap 交易
include(":feature:wallet")   // 钱包：资产 / 收发 / 创建导入
include(":feature:activity") // 活动：交易历史
include(":feature:settings") // 设置：网络切换（Testnet/Mainnet，PRD §32）

// ---- core 基础层（Phase 2 新增） ----
include(":core:security")    // Keystore 加密存储 / BiometricPrompt / 安全防护

// ---- blockchain 区块链层（Phase 2 新增） ----
include(":blockchain:core")  // 链抽象：地址 / 余额 / 交易模型与接口
include(":blockchain:evm")   // web3j：EVM 钱包派生 / RPC / 签名 / 广播
include(":blockchain:solana") // SolanaKt：SOL 钱包派生 / RPC / 签名 / 广播（Phase 5）

// ---- data 数据层（Phase 2 新增） ----
include(":data:wallet")      // 钱包仓库：创建 / 导入 / 恢复 / 余额

// ---- Phase 4 新增：交易数据层 ----
include(":data:transaction") // Swap 仓库（Li.Fi）+ 交易历史（Room）

// ---- Phase 3 新增：网络与行情数据层 ----
include(":core:network")   // Retrofit / OkHttp / WebSocket Manager（重连·心跳·订阅）
include(":data:market")    // 行情仓库：CoinGecko REST + Binance WS 实时价
include(":feature:token")  // Token 详情页（K线图 / 实时价 / 近期交易）

// ---- 以下模块在后续 Phase 创建，避免空模块拖慢构建 ----
// include(":data:token")       // 后续：Token 仓库（可并入 data:market）
// include(":data:transaction") // Phase 4
// include(":feature:settings") // Phase 2（网络切换 Testnet/Mainnet）
