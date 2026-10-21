package com.leon.chainflow.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp // Markets Tab：行情图表（AutoMirrored 适配 RTL）
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.leon.chainflow.core.ui.theme.ChainFlowTheme
import com.leon.chainflow.feature.activity.ActivityScreen
import com.leon.chainflow.feature.home.HomeScreen
import com.leon.chainflow.feature.market.MarketScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.leon.chainflow.feature.swap.SwapScreen
import com.leon.chainflow.feature.settings.SettingsScreen
import com.leon.chainflow.feature.token.TokenDetailScreen
import com.leon.chainflow.feature.wallet.WalletScreen

/** Token 详情路由（非 Tab）：路径参数 symbol（Binance 交易对，如 ETHUSDT） */
private const val TOKEN_DETAIL_ROUTE = "token/{symbol}"

/**
 * Swap 路由：可选查询参数 from（支付 Token 符号，如 ETH）
 *
 * 用可选参数而非必需路径参数，是为了让底部 Tab 仍能用 "swap" 直接进入
 * （无参数 → 保持默认交易对），同时支持详情页带币种跳转 "swap?from=ETH"。
 */
private const val SWAP_ROUTE = "swap?from={from}"
private const val ARG_SWAP_FROM = "from"

/** 设置页路由（非 Tab，PRD §32 网络切换） */
private const val SETTINGS_ROUTE = "settings"

/**
 * 页面切换动画时长（毫秒）—— 底栏显隐与 NavHost 过渡必须共用同一时长：
 * 两者是独立动画，只有时长与起点一致，视觉上才会「同时」完成。
 */
private const val NAV_ANIM_MS = 200

/**
 * 底部导航 Tab 定义（PRD §6：MVP 共 5 个主 Tab）
 *
 * @param route Navigation Compose 路由名（与 NavHost destination 一致）
 * @param label Tab 标题文案
 * @param icon Tab 图标
 */
enum class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Filled.Home),
    Markets("markets", "Markets", Icons.AutoMirrored.Filled.TrendingUp),
    Swap("swap", "Swap", Icons.Filled.CurrencyExchange),
    Wallet("wallet", "Wallet", Icons.Filled.Wallet),
    Activity("activity", "Activity", Icons.Filled.ReceiptLong),
}

/**
 * App 根组件：主题 + Scaffold(底部导航) + NavHost
 *
 * 导航策略：
 * - 5 个 Tab 顶级路由平铺在 NavHost 中；
 * - Tab 切换使用 saveState/restoreState 组合，保留各 Tab 的
 *   滚动位置与 ViewModel 状态（多返回栈体验）。
 */
@Composable
fun ChainFlowApp() {
    ChainFlowTheme {

        val navController = rememberNavController()

        // 当前路由：非 Tab 页（settings / token 详情等）自动全屏 —— 不渲染底栏
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isTabDestination = BottomTab.entries.any { tab ->
            currentDestination.matchesTabRoute(tab.route)
        }

        Scaffold(
            bottomBar = {
                // 底栏只属于 5 个 Tab 路由。
                // 用 AnimatedVisibility（与 NavHost 同 tween 时长）让「底栏收起」与「页面淡入」
                // 在同一帧开始、同一帧结束；高度渐变也会同步收缩 Scaffold 的 contentPadding，
                // 避免内容先跳、底栏后消失（或反之）的错位感。
                AnimatedVisibility(
                    visible = isTabDestination,
                    enter = expandVertically(animationSpec = tween(NAV_ANIM_MS)) +
                        fadeIn(animationSpec = tween(NAV_ANIM_MS)),
                    exit = shrinkVertically(animationSpec = tween(NAV_ANIM_MS)) +
                        fadeOut(animationSpec = tween(NAV_ANIM_MS)),
                ) {
                    NavigationBar {
                        BottomTab.entries.forEach { tab ->
                            // 通过 hierarchy 判断当前 Tab 是否处于选中态
                            // （支持未来子路由也高亮父 Tab，例如 token/detail 高亮 Markets）
                            val selected = currentDestination.matchesTabRoute(tab.route)

                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigateToTab(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = BottomTab.Home.route,
                // consumeWindowInsets：外层 Scaffold 已把系统栏/底栏占位算进 padding，
                // 标记为「已消费」，否则每个页面自己的 Scaffold 会再加一次系统栏 inset
                // —— 表现为内容与底栏之间多出一条空白（以及状态栏下重复留白）。
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                // 显式覆盖 Navigation 默认的 700ms 淡入淡出：
                // 默认时长与底栏的 200ms 收展不同步，是「先隐藏后跳转」错位感的根因。
                enterTransition = { fadeIn(tween(NAV_ANIM_MS)) },
                exitTransition = { fadeOut(tween(NAV_ANIM_MS)) },
                popEnterTransition = { fadeIn(tween(NAV_ANIM_MS)) },
                popExitTransition = { fadeOut(tween(NAV_ANIM_MS)) },
            ) {
                // ---- 5 个 Tab 路由（各 feature 模块暴露的 Screen） ----
                composable(BottomTab.Home.route) {
                    HomeScreen(
                        // 导航回调由外部注入：Feature 模块不感知具体导航实现（解耦）
                        onNavigateToSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onTokenClick = { symbol -> navController.navigate("token/$symbol") },
                    )
                }
                composable(BottomTab.Markets.route) {
                    MarketScreen(
                        onTokenClick = { symbol -> navController.navigate("token/$symbol") },
                    )
                }
                composable(
                    route = SWAP_ROUTE,
                    arguments = listOf(
                        // 可选参数：底部 Tab 直接进入时缺省为空串（走默认 ETH→USDC）
                        navArgument(ARG_SWAP_FROM) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    SwapScreen(payToken = entry.arguments?.getString(ARG_SWAP_FROM).orEmpty())
                }
                composable(BottomTab.Wallet.route) { WalletScreen() }
                composable(BottomTab.Activity.route) { ActivityScreen() }

                // ---- Token 详情页（非 Tab 路由，PRD §9 核心页面） ----
                composable(
                    route = TOKEN_DETAIL_ROUTE,
                    arguments = listOf(
                        navArgument("symbol") { type = NavType.StringType },
                    ),
                ) { entry ->
                    TokenDetailScreen(
                        symbol = entry.arguments?.getString("symbol").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onNavigateToSwap = { baseAsset ->
                            // 带上当前币种：Swap 页据此预选支付 Token（而不是固定 ETH）
                            navController.navigate("swap?from=$baseAsset") {
                                // 从详情进 Swap：回到 Tab 根，避免返回链嵌套
                                popUpTo(navController.graph.findStartDestination().id)
                                // 不启用 launchSingleTop：复用旧 entry 会沿用上一次的参数/ViewModel，
                                // 导致「换了币种再进来仍显示旧交易对」——这里的 popUpTo 已保证不会堆栈
                            }
                        },
                    )
                }
                // ---- 设置页（非 Tab 路由，PRD §32） ----
                composable(SETTINGS_ROUTE) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
            } // NavHost 结束
        }
    }
}

/**
 * 判断当前 destination 是否属于某个 Tab 路由
 *
 * 带可选参数的 destination（如 Swap 的 "swap?from={from}"）在 `route` 里
 * 保留了参数模式，因此比较时只取 `?` 之前的基础段。
 */
private fun NavDestination?.matchesTabRoute(route: String): Boolean =
    this?.hierarchy?.any { it.route?.substringBefore("?") == route } == true

/**
 * 切换底部 Tab（单实例 + 多返回栈状态保存）
 *
 * ================= 为什么 Home 不参与 saveState / restoreState =================
 *
 * Navigation 的 `NavController.executePopOperations` 在 `saveState = true` 且
 * **非 inclusive** 时，除了保存被弹出的栈，还会把这条保存记录登记到 popUpTo
 * **目标自身**的 id 上（`backStackMap[destination.id] = savedState.firstOrNull()?.id`）。
 *
 * 而 `navigate()` 内部顺序是「先 popUpTo、后 restoreState」，于是同一个调用里：
 * 1. popUpTo(home) { saveState = true } 弹掉当前页，并登记 backStackMap[home] = 刚弹掉的栈
 * 2. restoreState = true 查 backStackMap[home] → 命中 → 把第 1 步刚弹掉的页面恢复回来
 * 结果就是「点 Home 却停在原页面」。
 *
 * 复现路径：Home → 币种详情 → 详情内「Swap」按钮 → 点 Home Tab，页面仍停在 Swap 上
 * （若此前从未切过 Tab，backStackMap[home] 尚为空占位，行为会碰巧正确，问题更隐蔽）。
 *
 * Home 是 startDestination，它之上只会有 token 详情 / settings 这类全屏非 Tab 路由，
 * 本就不该被 Tab 多返回栈保留，因此目标为 Home 时不保存也不恢复：
 * popUpTo 之后栈顶即 Home，再由 launchSingleTop 原地复用（ViewModel 与 compose 状态保留）。
 */
private fun NavHostController.navigateToTab(tab: BottomTab) {
    val startDestination = graph.findStartDestination()
    val isStartDestinationTab = tab.route == startDestination.route
    navigate(tab.route) {
        popUpTo(startDestination.id) {
            saveState = !isStartDestinationTab
        }
        launchSingleTop = true
        restoreState = !isStartDestinationTab
    }
}
