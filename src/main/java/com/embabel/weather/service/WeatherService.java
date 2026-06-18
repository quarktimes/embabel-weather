package com.embabel.weather.service;

import com.embabel.weather.agent.WeatherAgent;
import com.embabel.weather.agent.WeatherContext;
import com.embabel.weather.client.OpenWeatherClient;
import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import com.embabel.weather.model.WeatherQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 天气查询服务
 * <p>
 * 职责：编排 Agent 流程 + 缓存 + 降级兜底。
 * 先走 Embabel Agent（含 DeepSeek 分析），
 * Agent/LLM 异常时静默降级为直调 OpenWeather（无 AI 分析）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private final WeatherAgent weatherAgent;
    private final OpenWeatherClient openWeatherClient;

    /**
     * 查询天气 —— 对同一输入有 10 分钟缓存
     *
     * @param userInput 用户输入，如 "北京" / "明天上海会下雨吗？"
     * @return WeatherQueryResult（可能含错误信息）
     */
    @Cacheable(value = "weather", key = "#userInput", unless = "#result.days().isEmpty()")
    public WeatherQueryResult queryWeather(String userInput) {
        long start = System.currentTimeMillis();

        if (userInput == null || userInput.isBlank()) {
            return errorResult("请输入城市或天气问题", start);
        }

        // ── 主线：Embabel Agent ──
        WeatherContext ctx = weatherAgent.execute(userInput);

        // Agent 成功获取到天气数据
        if (ctx.getForecasts() != null && !ctx.getForecasts().isEmpty()) {
            return buildResult(ctx, start);
        }

        // Agent 失败但有明确的业务错误信息（如城市不存在）
        if (ctx.getErrorMessage() != null && isBusinessError(ctx.getErrorMessage())) {
            return errorResult(ctx.getErrorMessage(), start);
        }

        // ── 降级：Agent 异常（LLM 超时等），直调 OpenWeather ──
        log.warn("[Service] Agent 异常，降级为直调 OpenWeather");
        String rawInput = userInput.trim();
        try {
            GeoLocation geo = openWeatherClient.geocoding(rawInput);
            List<DayForecast> forecasts = openWeatherClient.forecast(geo.lat(), geo.lon());

            if (forecasts.isEmpty()) {
                return errorResult("获取天气数据失败", start);
            }

            boolean anyRain = forecasts.stream().anyMatch(DayForecast::hasRain);
            long elapsed = System.currentTimeMillis() - start;
            return new WeatherQueryResult(geo.name(), forecasts, anyRain, null, false, elapsed);

        } catch (IllegalArgumentException e) {
            return errorResult("未找到城市：" + rawInput, start);
        } catch (Exception e) {
            log.error("[Service] 降级路径也失败了", e);
            return errorResult("天气服务暂时不可用，请稍后重试", start);
        }
    }

    // ── 内部方法 ──

    private WeatherQueryResult buildResult(WeatherContext ctx, long start) {
        String cityName = ctx.getCoordinates() != null ? ctx.getCoordinates().name() : "";
        boolean anyRain = ctx.getForecasts().stream().anyMatch(DayForecast::hasRain);
        long elapsed = System.currentTimeMillis() - start;
        return new WeatherQueryResult(cityName, ctx.getForecasts(), anyRain,
                ctx.getAiAnalysis(), false, elapsed);
    }

    private WeatherQueryResult errorResult(String message, long start) {
        long elapsed = System.currentTimeMillis() - start;
        // 用 errorMessage 属性传给上层，但 WeatherQueryResult 没有单独错误字段，
        // 约定：cityName 为 null、days 为空列表 = 错误，errorMessage 通过 log 查看
        // Controller 根据 days.isEmpty() 判断
        return new WeatherQueryResult("", List.of(), false, message, false, elapsed);
    }

    /** 判断是否为业务错误（城市不存在等），而非系统异常 */
    private boolean isBusinessError(String msg) {
        return msg.contains("未找到城市") || msg.contains("未能识别");
    }
}
