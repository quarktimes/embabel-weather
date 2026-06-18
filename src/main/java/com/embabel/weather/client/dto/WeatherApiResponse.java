package com.embabel.weather.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * OpenWeather API 请求/响应 DTO 集合
 * <p>
 * 外层类仅做命名空间，所有 Record 以内部类方式引用：
 * {@code WeatherApiResponse.GeoItem}、{@code WeatherApiResponse.ForecastResponse} 等。
 */
public final class WeatherApiResponse {

    private WeatherApiResponse() {}

    // ─── 地理编码 ───

    public record GeoItem(String name, double lat, double lon, String country) {}

    // ─── 5天/3小时预报 ───

    public record ForecastResponse(
            @JsonProperty("list") List<ForecastItem> list,
            @JsonProperty("city") ForecastCity city
    ) {}

    public record ForecastItem(
            @JsonProperty("dt") long dt,
            @JsonProperty("main") MainData main,
            @JsonProperty("weather") List<WeatherInfo> weather,
            @JsonProperty("wind") WindInfo wind
    ) {}

    public record MainData(
            @JsonProperty("temp") double temp,
            @JsonProperty("temp_min") double tempMin,
            @JsonProperty("temp_max") double tempMax,
            @JsonProperty("humidity") int humidity
    ) {}

    public record WeatherInfo(
            @JsonProperty("id") int id,
            @JsonProperty("main") String main,
            @JsonProperty("description") String description,
            @JsonProperty("icon") String icon
    ) {}

    public record WindInfo(
            @JsonProperty("speed") double speed
    ) {}

    public record ForecastCity(
            @JsonProperty("name") String name,
            @JsonProperty("country") String country
    ) {}
}
