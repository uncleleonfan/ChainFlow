# UI 层（core:common MVI + core:ui + feature:*）

> 对应 PRD §5/§6/§35。模块路径 `core/common`（MVI 骨架）、`core/ui`（主题与组件）、`feature/*`（页面）。

## 1. MVI 骨架

`core/common/.../mvi/MviViewModel.kt` 是全项目唯一的 ViewModel 基类：

```kotlin
abstract class MviViewModel<S : UiState, I : UiIntent, E : UiEvent>(initialState: S) : ViewModel() {
    val state: StateFlow<S>                 // 唯一渲染源
    val events: Flow<E>                     // Channel.receiveAsFlow()
    fun dispatch(intent: I)                 // viewModelScope.launch { onIntent(intent) }
    protected fun updateState(reducer: S.() -> S)  // MutableStateFlow.update（原子）
    protected fun sendEvent(event: E)       // trySend
    protected abstract suspend fun onIntent(intent: I)
}
```

### 1.1 数据流

```
Compose 交互
   │ dispatch(UiIntent)
   ▼
onIntent()（suspend，viewModelScope）
   ├─ updateState { copy(...) } ──→ StateFlow<UiState> ──→ collectAsStateWithLifecycle ──→ 重组
   └─ sendEvent(UiEvent) ─────────→ Channel<UiEvent> ────→ LaunchedEffect collect ──→ 导航/Toast/生物识别
```

### 1.2 三要素的语义边界

| 要素 | 语义 | 消费方式 | 选择理由 |
|---|---|---|---|
| `UiState` | 不可变界面快照 | `StateFlow` | 可重复消费：旋转屏幕/进程恢复后重放安全 |
| `UiIntent` | 用户意图（动词短语） | `dispatch` | View 层唯一职责是「描述发生了什么」 |
| `UiEvent` | 一次性副作用 | `Channel` | 导航/弹窗不能重放，否则旋转屏幕会二次跳转 |

### 1.3 两个实现细节

1. `updateState` 走 `MutableStateFlow.update {}`：并发 Intent（如快速切换排序 + 刷新）不会丢失更新。
2. `Channel.UNLIMITED` + `trySend`：发送不挂起，避免 `sendEvent` 被迫变成 suspend；事件量级（导航/提示）远低于内存上限。

> 注意：`events` 是冷流，`LaunchedEffect(Unit)` 收集期间到达的事件才会被消费；页面离开时的事件会留在 Channel 中（下次进入可能补偿消费）。因此**不要用 Event 传递必须持久化的状态**（那属于 State）。

## 2. 状态建模原则

### 2.1 只存「原始输入」，展示值用派生属性

```kotlin
data class MarketUiState(
    val tokens: List<TokenListItem> = emptyList(),
    val livePrices: Map<String, Ticker> = emptyMap(),
    val searchQuery: String = "",
    val sort: MarketSort = MarketSort.VOLUME,
) : UiState {
    val displayList: List<TokenListItem> get() = /* 过滤 + 排序 + 实时价覆盖 */
}
```

好处：搜索/排序/实时价三者互不干扰，不存在「改了 A 忘了同步 B」的组合爆炸。代价是每次读取都重算（见 §8）。

### 2.2 页面内状态机（避免导航爆炸）

钱包页 5 种形态不做 5 个路由，而是一个 `stage` 字段：

```
NO_WALLET ──创建──→ BACKUP ──确认备份──→ OVERVIEW
     └──导入──→ IMPORT ──────────────↗
BACKUP/IMPORT ──返回──→ NO_WALLET（同时清空 pendingMnemonic / importInput）
```

这是「同一业务实体的不同阶段」，用状态机比用导航更合适（返回键语义、敏感数据清理都在一处控制）。

### 2.3 错误放 State 还是 Event？

| 场景 | 选择 | 示例 |
|---|---|---|
| 页面级失败，需要占位/重试 | State `error: AppError?` | Market 列表加载失败 → `ErrorView` |
| 瞬时提示，不改变页面结构 | Event | 「地址格式无效」「交易已取消」Snackbar |

## 3. 主题（Dark Trading Terminal）

### 3.1 色板

| 语义 | 值 | 用途 |
|---|---|---|
| Background | `#0A0E14` | 页面底色（近黑） |
| Surface / SurfaceVariant | `#111722` / `#1A2230` | 卡片 / 输入框与悬浮层 |
| Primary | `#2DD4BF` | 主按钮、选中态、图表强调 |
| Secondary | `#60A5FA` | 次级强调 |
| TextPrimary/Secondary/Tertiary | `#F1F5F9` / `#94A3B8` / `#64748B` | 三级文字层次 |
| PriceUp / PriceDown | `#34D399` / `#F87171` | **涨绿跌红**（Crypto 惯例，区别于股市） |
| Buy / Sell | `#10B981` / `#EF4444` | BUY/SELL 标签 |
| Warning / Error / Outline | `#FBBF24` / `#EF4444` / `#1E293B` | 风险提示 / 失败 / 分割线 |

### 3.2 语义色注入

Material3 的 `ColorScheme` 没有「涨跌」概念，因此额外提供：

```kotlin
val LocalChainFlowColors = staticCompositionLocalOf { ChainFlowSemanticColors() }
// 使用：LocalChainFlowColors.current.priceUp
```

`staticCompositionLocalOf` 用于**几乎不变**的值：读取时不建立订阅，比 `compositionLocalOf` 少一层开销。

主题**强制暗色**（`DarkColorScheme` 恒定），`isSystemInDarkTheme()` 参数仅为未来亮色方案预留 —— PRD §35 明确要求 Trading Terminal 风格。

### 3.3 字体

| 槽位 | 字体 | 用途 |
|---|---|---|
| `displayLarge/Small` | Monospace Bold/SemiBold 36/28sp | 总资产、详情页价格 |
| `titleLarge/Medium` | 默认 18/16sp SemiBold/Medium | 区块标题、卡片标题 |
| `bodyLarge/Medium/Small` | 15/13/11sp | 列表主文字、次级说明 |
| `labelLarge` | **Monospace** 15sp | 列表价格 —— 等宽保证实时跳动时不抖 |
| `labelMedium` | **Monospace** 13sp | 涨跌幅、BUY/SELL |

等宽数字是本项目的关键视觉决策：价格每秒变化时，若字宽不等，整行会左右抖动。

## 4. 通用组件（core:ui）

| 组件 | 职责 | 约定 |
|---|---|---|
| `PriceText` | 价格格式化 | ≥1 保留 2 位（千分位），<1 保留 8 位（小币种） |
| `PriceChangeText` | 涨跌幅 | 自动 `+/-` 前缀、null → `0.00%` 中性色 |
| `TradeSideTag` | BUY/SELL 标签 | 半透明底色（alpha 0.12）+ 4dp 圆角 |
| `LoadingView` | 全屏加载 | 仅首屏使用，避免局部闪烁 |
| `ErrorView` | 错误 + 重试 | 文案来自 `AppError.messageToUser`，不暴露技术细节 |
| `EmptyView` | 空状态 | 可带一个操作按钮（如「创建钱包」） |

业务 Composable **禁止硬编码颜色与价格格式**，必须走主题与组件 —— 这是「看起来像交易 App 而非 Demo」的最低成本手段。

## 5. 页面装配模板

所有 Screen 结构一致：

```kotlin
@Composable
fun XxxScreen(navCallback: () -> Unit, viewModel: XxxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()   // 生命周期感知收集
    LaunchedEffect(Unit) { viewModel.dispatch(XxxUiIntent.Load) }
    LaunchedEffect(Unit) { viewModel.events.collect { /* 导航/提示 */ } }
    Scaffold(topBar = { TopAppBar(title = { Text("…") }) }) { padding -> /* 内容 */ }
}
```

`collectAsStateWithLifecycle` 在 STOPPED 时停止收集：后台不做无谓重组，回前台自动恢复最新值。

### 5.1 页面清单

| Tab / 路由 | Screen | 数据源 | 备注 |
|---|---|---|---|
| Home | `HomeScreen` | WS 实时 + REST 涨幅榜 | WS 连接徽章；Top Movers 取涨幅前 10 |
| Markets | `MarketScreen` | REST 快照 + WS 覆盖 | 搜索/排序/下拉刷新，`LazyColumn(key = symbol)` |
| Swap | `SwapScreen` | Li.Fi quote | 防抖报价、滑点 chips、生物识别 |
| Wallet | `WalletScreen` | RPC + Keystore | 5 阶段状态机、FLAG_SECURE、Send 对话框 |
| Activity | `ActivityScreen` | Room Flow | 状态徽章、点击跳 Block Explorer |
| `token/{symbol}` | `TokenDetailScreen` | REST + WS + klines | Canvas 自绘 K 线、周期切换 |
| `settings` | `SettingsScreen` | DataStore | 链选择 + Mainnet 风险弹窗 |

## 6. 导航与 Feature 解耦

```
:app  ChainFlowApp
 ├─ BottomTab 枚举（route/label/icon）
 ├─ NavigationBar + NavHost（5 个 Tab 平铺）
 └─ 非 Tab 路由：token/{symbol}、settings
```

- **Feature 之间零依赖**：`HomeScreen` 提供 `onTokenClick/onNavigateToSettings` 参数，由 `:app` 注入 `navController.navigate(...)`
- Tab 切换标准写法：`popUpTo(startDestination) { saveState = true } + launchSingleTop + restoreState`，保留各 Tab 滚动位置
- 选中态用 `destination.hierarchy.any { it.route == tab.route }`，为将来子路由高亮父 Tab 留余地
- 从 Token 详情进 Swap 时 `popUpTo(start)`，避免返回链嵌套

## 7. 敏感交互的 UI 职责

安全能力在 `core:security`，**触发时机在 UI 层**：

| 场景 | UI 行为 |
|---|---|
| 助记词展示（`Stage.BACKUP`） | `LaunchedEffect(state.stage)` 开 `FLAG_SECURE`；离开阶段立即关闭 |
| 复制地址 | `SecurityGuard.copySensitiveToClipboard()`（45s 后清空） |
| Send / Swap 确认 | 先 `RequireBiometric` 事件 → `BiometricPrompt` → 通过后才调 `executeXxxAfterBiometric()` |
| 删除钱包 | 二次确认弹窗 + 「我已备份助记词」勾选 |

为什么生物识别放在 UI 而不是 ViewModel：`BiometricPrompt` 需要 `FragmentActivity`，且必须由前台窗口承载；ViewModel 只负责「认证成功后做什么」。

## 8. 渲染性能约定

| 约定 | 位置 | 原因 |
|---|---|---|
| `items(key = { it.symbol })` | Market / Home | Map 更新时只重组变化的行 |
| WS 价覆盖而非替换列表 | `MarketScreen.TokenRow` | 避免整表 diff |
| K 线纯 Canvas 单帧绘制 | `CandlestickChart` | 200 根 K 线 <1ms，无第三方图表库 |
| `verticalScroll` 用于短页 | Swap / TokenDetail | 长列表一律 `LazyColumn` |
| 派生列表在 State 中计算 | `displayList` | 单一入口，避免 UI 里散落过滤逻辑 |

已知代价：`displayList` 是 `get()`，每次重组都会重新过滤+排序（30 条量级无感）；若清单扩到千级，需要改为在 ViewModel 中 `combine` 后用 `stateIn` 缓存。

## 9. 当前边界

- 文案硬编码中文（无 `strings.xml` 国际化），MVP 明确单语言
- 无 Compose Preview 体系（`ui-tooling-preview` 已引入但未编写 `@Preview`）
- `WalletScreen` 内 `remember { SecurityGuard(context) }` 手工构造而非 Hilt 注入 —— 与既有基类风格不一致，后续应改为构造参数注入
- 无设计 token 文件（间距/圆角散落在 Composable 内，当前统一 8/16/24dp 三级）
- 无深浅色切换、无无障碍（contentDescription 仅覆盖图标按钮）
- 无 `@Stable/@Immutable` 标注：`MarketUiState` 含 `List/Map`，Compose 会按 `equals` 比较，当前数据量下无问题，大列表时建议加 `ImmutableList`
