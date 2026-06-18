package com.embabel.weather.model;

/**
 * 地理编码结果（城市名 → 经纬度）
 */
public record GeoLocation(
        String name,
        double lat,
        double lon,
        String country
) {}
