package com.embabel.weather.model;

import java.util.List;

/**
 * 天气查询聚合结果 —— 传给 Thymeleaf 渲染
 *
 * @param cityName       城市名
 * @param days           3 天预报列表
 * @param anyRain        3 天内是否有雨
 * @param aiAnalysis     DeepSeek 生成的分析文字（可能为 null）
 * @param cached         是否来自缓存
 * @param processingTimeMs 处理耗时
 */
public record WeatherQueryResult(
        String cityName,
        List<DayForecast> days,
        boolean anyRain,
        String aiAnalysis,
        boolean cached,
        long processingTimeMs
) {}
