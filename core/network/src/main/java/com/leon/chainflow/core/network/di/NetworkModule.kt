package com.leon.chainflow.core.network.di

import com.leon.chainflow.core.network.api.BinanceApi
import com.leon.chainflow.core.network.api.CoinGeckoApi
import com.leon.chainflow.core.network.api.LiFiApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 网络层 Hilt 模块：提供全局单例 Retrofit / OkHttpClient / API 实例
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** CoinGecko 基础 URL（公共 API，无 Key；国内需代理） */
    private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"

    /** Binance 公开数据镜像（无地域限制、免 Key、免代理直连） */
    private const val BINANCE_VISION_BASE_URL = "https://data-api.binance.vision/api/v3/"

    /** Li.Fi Swap 聚合器（国内直连可用，免 Key） */
    private const val LIFI_BASE_URL = "https://li.quest/v1/"

    /**
     * 行情 REST 专用 OkHttp：
     * - 读超时 20s（公共 API 冷启动可能较慢）
     * - retryOnConnectionFailure：单次连接失败自动重试（TCP 层）
     */
    @Provides
    @Singleton
    @Named("coingecko")
    fun provideCoinGeckoClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * kotlinx-serialization 解析器：
     * ignoreUnknownKeys —— CoinGecko 响应字段非常多，只解析 DTO 声明的字段
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true // null → 默认值，避免个别币种缺字段导致整页解析失败
    }

    /** Li.Fi Swap 聚合器 API（Phase 4：quote / 预构建交易 / 状态查询） */
    @Provides
    @Singleton
    fun provideLiFiApi(
        @Named("coingecko") client: OkHttpClient, // 复用超时配置
        json: Json,
    ): LiFiApi = Retrofit.Builder()
        .baseUrl(LIFI_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(LiFiApi::class.java)

    /**
     * Binance 公开行情 API（列表/K线主数据源 —— 国内直连可用）
     * 复用全局 Json 解析器与专用 OkHttpClient（无鉴权无需额外拦截器）
     */
    @Provides
    @Singleton
    fun provideBinanceApi(
        @Named("coingecko") client: OkHttpClient, // 同一套超时配置即可
        json: Json,
    ): BinanceApi = Retrofit.Builder()
        .baseUrl(BINANCE_VISION_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(BinanceApi::class.java)

    @Provides
    @Singleton
    fun provideCoinGeckoApi(
        @Named("coingecko") client: OkHttpClient,
        json: Json,
    ): CoinGeckoApi = Retrofit.Builder()
        .baseUrl(COINGECKO_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CoinGeckoApi::class.java)
}
