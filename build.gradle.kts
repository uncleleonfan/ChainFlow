// =============================================================================
// 根项目构建脚本
// 只声明各插件版本（apply false），具体模块在各自的 build.gradle.kts 中应用
// 注意：AGP 9 内置 Kotlin，无需声明 kotlin-android 插件
// =============================================================================
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
