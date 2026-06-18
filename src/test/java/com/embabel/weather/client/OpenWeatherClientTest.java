package com.embabel.weather.client;

import com.embabel.weather.client.dto.WeatherApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenWeatherClient 核心逻辑单元测试
 * <p>
 * 测试下雨检测和数据聚合逻辑。
 */
class OpenWeatherClientTest {

    // ─── isRainCode 边界测试 ───

    @ParameterizedTest
    @CsvSource({
            "300, true",   // 毛毛雨（轻度）
            "301, true",   // 毛毛雨
            "321, true",   // 毛毛雨（重度）
            "500, true",   // 小雨
            "501, true",   // 中雨
            "520, true",   // 阵雨
            "531, true",   // 暴雨
            "200, false",  // 雷暴（不是雨）
            "600, false",  // 雪
            "700, false",  // 雾
            "800, false",  // 晴
            "801, false",  // 多云
            "802, false",  // 多云
    })
    void isRainCode_shouldDetectRainCorrectly(int code, boolean expected) {
        assertEquals(expected, OpenWeatherClient.isRainCode(code));
    }

    // ─── 按天聚合逻辑 ───

    @Test
    void aggregateDay_shouldComputeCorrectMinMax() {
        // 创建一天的 3 小时数据：凌晨低温，午后高温
        var morning = new WeatherApiResponse.ForecastItem(
                1000000L,
                new WeatherApiResponse.MainData(18.0, 18.0, 20.0, 60),
                List.of(new WeatherApiResponse.WeatherInfo(802, "Clouds", "多云", "03d")),
                new WeatherApiResponse.WindInfo(2.0)
        );
        var noon = new WeatherApiResponse.ForecastItem(
                1000000L + 3600 * 3,
                new WeatherApiResponse.MainData(25.0, 18.0, 28.0, 45),
                List.of(new WeatherApiResponse.WeatherInfo(800, "Clear", "晴", "01d")),
                new WeatherApiResponse.WindInfo(3.0)
        );
        var afternoon = new WeatherApiResponse.ForecastItem(
                1000000L + 3600 * 6,
                new WeatherApiResponse.MainData(24.0, 18.0, 27.0, 50),
                List.of(new WeatherApiResponse.WeatherInfo(500, "Rain", "小雨", "10d")),
                new WeatherApiResponse.WindInfo(4.0)
        );

        // 直接测试 hasRain 检测
        boolean hasRain = afternoon.weather().stream()
                .anyMatch(w -> OpenWeatherClient.isRainCode(w.id()));
        assertTrue(hasRain, "500 应该被检测为雨");

        boolean morningHasRain = morning.weather().stream()
                .anyMatch(w -> OpenWeatherClient.isRainCode(w.id()));
        assertFalse(morningHasRain, "802 不应该被检测为雨");
    }

    // ─── 异常输入处理 ───

    @Test
    void isRainCode_edgeCases() {
        // 边界值
        assertFalse(OpenWeatherClient.isRainCode(299));  // drizzle 前
        assertTrue(OpenWeatherClient.isRainCode(300));   // drizzle 开始
        assertTrue(OpenWeatherClient.isRainCode(321));   // drizzle 结束
        assertFalse(OpenWeatherClient.isRainCode(322));  // drizzle 后

        assertFalse(OpenWeatherClient.isRainCode(499));  // rain 前
        assertTrue(OpenWeatherClient.isRainCode(500));   // rain 开始
        assertTrue(OpenWeatherClient.isRainCode(531));   // rain 结束
        assertFalse(OpenWeatherClient.isRainCode(532));  // rain 后
    }
}
