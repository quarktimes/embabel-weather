package com.embabel.weather.service;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeatherServiceTest {

    @Mock
    private AgentPlatform agentPlatform;

    @Mock
    private Agent agent;

    @Mock
    private AgentProcess agentProcess;

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

    @Test
    void queryWeather_withEmptyInput_shouldReturnError() {
        WeatherQueryResult result = weatherService.queryWeather("");
        assertTrue(result.days().isEmpty());
    }

    @Test
    void queryWeather_withNullInput_shouldReturnError() {
        WeatherQueryResult result = weatherService.queryWeather(null);
        assertTrue(result.days().isEmpty());
    }

    @Test
    void queryWeather_whenAgentSucceeds_shouldReturnResult() throws Exception {
        when(agentPlatform.agents()).thenReturn(List.of(agent));
        when(agent.getName()).thenReturn("weatherAgent");
        when(agentPlatform.createAgentProcessFrom(any(), any(), any())).thenReturn(agentProcess);
        when(agentPlatform.start(any())).thenReturn(CompletableFuture.completedFuture(agentProcess));

        var expected = new WeatherQueryResult("北京", sampleForecasts, true,
                "北京明天有雨", false, 100, "trace-1", List.of());
        when(agentProcess.resultOfType(WeatherQueryResult.class)).thenReturn(expected);

        WeatherQueryResult result = weatherService.queryWeather("北京");

        assertFalse(result.days().isEmpty());
        assertEquals("北京", result.cityName());
        assertEquals(3, result.days().size());
        assertTrue(result.anyRain());
    }

    @Test
    void queryWeather_whenAgentFails_shouldDegradeToDirectCall() throws Exception {
        when(agentPlatform.agents()).thenReturn(List.of(agent));
        when(agent.getName()).thenReturn("weatherAgent");
        when(agentPlatform.createAgentProcessFrom(any(), any(), any())).thenReturn(agentProcess);
        when(agentPlatform.start(any())).thenReturn(CompletableFuture.completedFuture(agentProcess));
        when(agentProcess.resultOfType(WeatherQueryResult.class))
                .thenReturn(new WeatherQueryResult("", List.of(), false, null, false, 0, null, null));

        // Fallback
        when(openWeatherClient.geocoding("北京"))
                .thenReturn(new GeoLocation("Beijing", 39.9, 116.4, "CN"));
        when(openWeatherClient.forecast(anyDouble(), anyDouble())).thenReturn(sampleForecasts);

        WeatherQueryResult result = weatherService.queryWeather("北京");

        assertFalse(result.days().isEmpty());
        assertEquals("Beijing", result.cityName());
    }
}
