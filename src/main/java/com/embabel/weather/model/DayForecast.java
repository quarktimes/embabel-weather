package com.embabel.weather.model;

/**
 * 单日天气预报（从 OpenWeather 5天/3小时数据中按天聚合）
 *
 * @param date        日期 "2026-06-18"
 * @param weekday     星期 "周三"
 * @param tempMax     最高温（当日所有时段最高）
 * @param tempMin     最低温（当日所有时段最低）
 * @param weather     天气描述 "阵雨"
 * @param weatherCode 天气代码（500+ 表示雨）
 * @param icon        图标编码 "10d"
 * @param hasRain     当日是否有降雨
 */
public record DayForecast(
        String date,
        String weekday,
        int tempMax,
        int tempMin,
        String weather,
        int weatherCode,
        String icon,
        boolean hasRain
) {}
