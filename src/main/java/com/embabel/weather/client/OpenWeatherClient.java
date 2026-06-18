package com.embabel.weather.client;

import com.embabel.weather.client.dto.WeatherApiResponse;
import com.embabel.weather.config.WeatherProperties;
import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenWeather API 客户端
 * <p>
 * 职责：调用 OpenWeather 的 Geo 和 Forecast 端点，聚合 3 小时间隔数据为 3 天日报。
 */
@Slf4j
@Component
public class OpenWeatherClient {

    private static final String FORECAST_PATH = "/forecast";
    private static final String GEO_PATH = "/direct";

    /** 天气码区间：毛毛雨/小雨 */
    private static final int DRIZZLE_START = 300;
    private static final int DRIZZLE_END = 321;
    /** 天气码区间：雨/暴雨 */
    private static final int RAIN_START = 500;
    private static final int RAIN_END = 531;

    /** 聚合成 3 天 */
    private static final int FORECAST_DAYS = 3;

    private final RestClient restClient;
    private final WeatherProperties props;

    public OpenWeatherClient(RestClient.Builder builder, WeatherProperties props) {
        this.props = props;
        this.restClient = builder.baseUrl(props.getBaseUrl()).build();
    }

    // ═══════════════════════════════════════════════
    //  公开方法
    // ═══════════════════════════════════════════════

    /**
     * 城市名 → 经纬度
     *
     * @param cityName 城市名，中文或英文
     * @return GeoLocation；找不到时抛出异常
     * @throws IllegalArgumentException 城市不存在
     */
    public GeoLocation geocoding(String cityName) {
        var geoRestClient = RestClient.builder()
                .baseUrl(props.getGeoUrl())
                .build();

        WeatherApiResponse.GeoItem[] items = geoRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(GEO_PATH)
                        .queryParam("q", cityName)
                        .queryParam("limit", 1)
                        .queryParam("appid", props.getApiKey())
                        .build())
                .retrieve()
                .body(WeatherApiResponse.GeoItem[].class);

        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("未找到城市: " + cityName);
        }
        WeatherApiResponse.GeoItem item = items[0];
        return new GeoLocation(item.name(), item.lat(), item.lon(), item.country());
    }

    /**
     * 获取 3 天天气预报（从 5 天逐 3 小时数据中聚合）
     *
     * @param lat 纬度
     * @param lon 经度
     * @return 3 天的日预报列表
     */
    public List<DayForecast> forecast(double lat, double lon) {
        WeatherApiResponse.ForecastResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(FORECAST_PATH)
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("units", props.getUnits())
                        .queryParam("lang", props.getLang())
                        .queryParam("appid", props.getApiKey())
                        .build())
                .retrieve()
                .body(WeatherApiResponse.ForecastResponse.class);

        if (response == null || response.list() == null || response.list().isEmpty()) {
            log.warn("forecast API 返回空数据 lat={}, lon={}", lat, lon);
            return List.of();
        }

        // 按日期分组
        Map<String, List<WeatherApiResponse.ForecastItem>> grouped = response.list().stream()
                .collect(Collectors.groupingBy(item -> toLocalDate(item.dt())));

        // 只取前 FORECAST_DAYS 天
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .limit(FORECAST_DAYS)
                .map(entry -> aggregateDay(entry.getKey(), entry.getValue()))
                .toList();
    }

    // ═══════════════════════════════════════════════
    //  数据聚合
    // ═══════════════════════════════════════════════

    /** 将一天内的 3 小时间隔数据聚合成单日预报 */
    private DayForecast aggregateDay(String dateStr, List<WeatherApiResponse.ForecastItem> items) {
        int tempMax = items.stream()
                .mapToInt(i -> (int) Math.round(i.main().tempMax()))
                .max()
                .orElse(0);

        int tempMin = items.stream()
                .mapToInt(i -> (int) Math.round(i.main().tempMin()))
                .min()
                .orElse(0);

        // 主天气：取白天时段（11:00-14:00）的天气；若无则取频率最高的
        String weather = pickDaytimeWeather(items);
        int weatherCode = pickDaytimeCode(items);
        String icon = pickDaytimeIcon(items);

        // 是否下雨
        boolean hasRain = items.stream().anyMatch(i -> isRainCode(i.weather().get(0).id()));

        // 星期几
        String weekday = toWeekday(dateStr);

        return new DayForecast(dateStr, weekday, tempMax, tempMin, weather, weatherCode, icon, hasRain);
    }

    /** 取白天时段 (11:00-14:00) 的天气描述，否则取出现频率最高的 */
    private String pickDaytimeWeather(List<WeatherApiResponse.ForecastItem> items) {
        Optional<WeatherApiResponse.ForecastItem> daytime = findDaytimeItem(items);
        if (daytime.isPresent()) {
            return daytime.get().weather().get(0).description();
        }
        // 按频率降序
        return items.stream()
                .map(i -> i.weather().get(0).description())
                .collect(Collectors.groupingBy(s -> s, LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("未知");
    }

    /** 取白天时段的天气码（取最严重的那条） */
    private int pickDaytimeCode(List<WeatherApiResponse.ForecastItem> items) {
        Optional<WeatherApiResponse.ForecastItem> daytime = findDaytimeItem(items);
        if (daytime.isPresent()) {
            return daytime.get().weather().get(0).id();
        }
        return items.stream()
                .mapToInt(i -> i.weather().get(0).id())
                .max()
                .orElse(800);
    }

    /** 取白天时段的图标 */
    private String pickDaytimeIcon(List<WeatherApiResponse.ForecastItem> items) {
        Optional<WeatherApiResponse.ForecastItem> daytime = findDaytimeItem(items);
        return daytime.map(i -> i.weather().get(0).icon()).orElse("01d");
    }

    /** 找白天时段 (11:00-14:00) 的数据 */
    private Optional<WeatherApiResponse.ForecastItem> findDaytimeItem(List<WeatherApiResponse.ForecastItem> items) {
        return items.stream().filter(item -> {
            ZonedDateTime zdt = toZonedDateTime(item.dt());
            int hour = zdt.getHour();
            return hour >= 11 && hour <= 14;
        }).findFirst();
    }

    // ═══════════════════════════════════════════════
    //  下雨检测
    // ═══════════════════════════════════════════════

    /** 判断天气码是否为降雨 */
    public static boolean isRainCode(int weatherCode) {
        return (weatherCode >= DRIZZLE_START && weatherCode <= DRIZZLE_END)
                || (weatherCode >= RAIN_START && weatherCode <= RAIN_END);
    }

    // ═══════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════

    private String toLocalDate(long epochSecond) {
        return toZonedDateTime(epochSecond).toLocalDate().toString();
    }

    private ZonedDateTime toZonedDateTime(long epochSecond) {
        return ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault());
    }

    private String toWeekday(String dateStr) {
        var date = java.time.LocalDate.parse(dateStr);
        var dayOfWeek = date.getDayOfWeek();
        // 中文星期
        return switch (dayOfWeek) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }
}
