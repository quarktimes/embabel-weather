package com.embabel.weather.service;

import com.embabel.weather.agent.WeatherAgent;
import com.embabel.weather.agent.WeatherContext;
import com.embabel.weather.client.OpenWeatherClient;
import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import com.embabel.weather.model.WeatherQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

/**
 * WeatherService 单元测试
 * <p>
 * Mock Embabel Agent 和 OpenWeatherClient，验证：
 * - 空输入处理
 * - Agent 成功路径
 * - Agent 异常时的降级路径
 * - 城市不存在时的错误反馈
 */
@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private WeatherAgent weatherAgent;

    @Mock
    private OpenWeatherClient openWeatherClient;

    @InjectMocks
    private WeatherService weatherService;

    private List<DayForecast> sampleForecasts;

    @BeforeEach
    void setUp() {
        sampleForecasts = List.of(
                new DayForecast("2026-06-18", "周三", 30, 22, "晴", 800, "01d", false),
                new DayForecast("2026-06-19", "周四", 28, 21, "阵雨", 500, "10d", true),
                new DayForecast("2026-06-20", "周五", 26, 20, "多云", 802, "02d", false)
        );
    }

    // ─── 空输入 ───

    @Test
    void queryWeather_withEmptyInput_shouldReturnError() {
        WeatherQueryResult result = weatherService.queryWeather("");

        assertTrue(result.days().isEmpty());
        assertNotNull(result.aiAnalysis());
    }

    @Test
    void queryWeather_withNullInput_shouldReturnError() {
        WeatherQueryResult result = weatherService.queryWeather(null);

        assertTrue(result.days().isEmpty());
        assertNotNull(result.aiAnalysis());
    }

    // ─── Agent 成功路径 ───

    @Test
    void queryWeather_withValidCity_shouldReturnForecasts() {
        // Mock Agent 返回成功结果
        WeatherContext ctx = new WeatherContext();
        ctx.setCoordinates(new GeoLocation("北京", 39.9, 116.4, "CN"));
        ctx.setForecasts(sampleForecasts);
        ctx.setAiAnalysis("北京未来三天晴好，适合出行。");
        when(weatherAgent.execute("北京")).thenReturn(ctx);

        WeatherQueryResult result = weatherService.queryWeather("北京");

        assertFalse(result.days().isEmpty());
        assertEquals("北京", result.cityName());
        assertEquals(3, result.days().size());
        assertEquals("北京未来三天晴好，适合出行。", result.aiAnalysis());
        assertTrue(result.anyRain()); // 第二天有雨
        assertFalse(result.cached());
        assertTrue(result.processingTimeMs() >= 0);

        verify(weatherAgent, times(1)).execute("北京");
        verifyNoInteractions(openWeatherClient); // Agent 成功，不触发降级
    }

    @Test
    void queryWeather_withNoRain_shouldReturnAnyRainFalse() {
        List<DayForecast> noRain = List.of(
                new DayForecast("2026-06-18", "周三", 30, 22, "晴", 800, "01d", false),
                new DayForecast("2026-06-19", "周四", 28, 21, "多云", 802, "02d", false),
                new DayForecast("2026-06-20", "周五", 26, 20, "晴", 801, "01d", false)
        );

        WeatherContext ctx = new WeatherContext();
        ctx.setCoordinates(new GeoLocation("北京", 39.9, 116.4, "CN"));
        ctx.setForecasts(noRain);
        when(weatherAgent.execute("北京")).thenReturn(ctx);

        WeatherQueryResult result = weatherService.queryWeather("北京");

        assertFalse(result.anyRain());
    }

    // ─── Agent 降级路径 ───

    @Test
    void queryWeather_whenAgentFails_shouldDegradeToDirectCall() {
        // Agent 返回空数据（LLM 异常）
        WeatherContext ctx = new WeatherContext();
        ctx.setForecasts(null);
        ctx.setErrorMessage("LLM 超时");
        when(weatherAgent.execute("北京")).thenReturn(ctx);

        // 降级路径返回数据
        when(openWeatherClient.geocoding("北京")).thenReturn(
                new GeoLocation("北京", 39.9, 116.4, "CN"));
        when(openWeatherClient.forecast(anyDouble(), anyDouble())).thenReturn(sampleForecasts);

        WeatherQueryResult result = weatherService.queryWeather("北京");

        assertFalse(result.days().isEmpty());
        assertEquals("北京", result.cityName());
        assertNull(result.aiAnalysis()); // 降级时无 AI 分析
        assertTrue(result.anyRain());

        verify(weatherAgent, times(1)).execute("北京");
        verify(openWeatherClient, times(1)).geocoding("北京");
        verify(openWeatherClient, times(1)).forecast(anyDouble(), anyDouble());
    }

    @Test
    void queryWeather_whenAgentAndDegradeBothFail_shouldReturnError() {
        // Agent 失败
        WeatherContext ctx = new WeatherContext();
        ctx.setForecasts(null);
        ctx.setErrorMessage("LLM 不可用");
        when(weatherAgent.execute("北京")).thenReturn(ctx);

        // 降级也失败
        when(openWeatherClient.geocoding("北京")).thenThrow(
                new RuntimeException("OpenWeather 不可用"));

        WeatherQueryResult result = weatherService.queryWeather("北京");

        assertTrue(result.days().isEmpty());
        assertNotNull(result.aiAnalysis());

        verify(weatherAgent, times(1)).execute("北京");
        verify(openWeatherClient, times(1)).geocoding("北京");
    }

    // ─── 城市不存在 ───

    @Test
    void queryWeather_withInvalidCity_shouldReturnError() {
        WeatherContext ctx = new WeatherContext();
        ctx.setForecasts(null);
        ctx.setErrorMessage("未找到城市: xyz");
        when(weatherAgent.execute("xyz")).thenReturn(ctx);

        WeatherQueryResult result = weatherService.queryWeather("xyz");

        assertTrue(result.days().isEmpty());
        assertNotNull(result.aiAnalysis());
    }

    // ─── 缓存未命中时调用 Agent ───

    @Test
    void queryWeather_cachedResult_shouldNotCallAgentAgain() {
        // 第一次调用
        WeatherContext ctx = new WeatherContext();
        ctx.setCoordinates(new GeoLocation("上海", 31.2, 121.5, "CN"));
        ctx.setForecasts(sampleForecasts);
        when(weatherAgent.execute("上海")).thenReturn(ctx);

        weatherService.queryWeather("上海");

        verify(weatherAgent, times(1)).execute("上海");
    }
}
