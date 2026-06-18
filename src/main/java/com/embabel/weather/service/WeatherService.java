package com.embabel.weather.service;

import com.embabel.agent.core.Agent;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.weather.agent.WeatherAgent;
import com.embabel.weather.client.OpenWeatherClient;
import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import com.embabel.weather.model.WeatherQueryResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 天气查询服务
 * <p>
 * 主线：通过 AgentPlatform 执行 GOAP agent，引擎自主串联 Action 链。
 * 降级：Agent 异常时直调 OpenWeather（无 AI 分析）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final String AGENT_NAME = "weatherAgent";

    private final AgentPlatform agentPlatform;
    private final OpenWeatherClient openWeatherClient;

    @Cacheable(value = "weather", key = "#userInput", unless = "#result.days().isEmpty()")
    public WeatherQueryResult queryWeather(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return errorResult("请输入城市或天气问题");
        }

        // ── 主线：通过 GOAP 引擎执行 Agent ──
        try {
            Agent agent = agentPlatform.agents().stream()
                    .filter(a -> AGENT_NAME.equals(a.getName()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Agent not found: " + AGENT_NAME));

            var input = new WeatherAgent.UserQuery(userInput);
            var process = agentPlatform.createAgentProcessFrom(agent, ProcessOptions.DEFAULT, input);

            CompletableFuture<?> future = agentPlatform.start(process);
            future.get(); // 等待 Agent 执行完成

            WeatherQueryResult result = process.resultOfType(WeatherQueryResult.class);
            if (result != null && !result.days().isEmpty()) {
                return result;
            }

            // Agent 返回了空结果（如城市不存在）
            if (result != null && result.aiAnalysis() != null
                    && isBusinessError(result.aiAnalysis())) {
                return new WeatherQueryResult("", List.of(), false,
                        result.aiAnalysis(), false, 0, null, null);
            }

        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                return errorResult(cause.getMessage());
            }
            log.warn("[Service] Agent 执行异常: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[Service] Agent 执行异常: {}", e.getMessage());
        }

        // ── 降级 ──
        return fallback(userInput);
    }

    private WeatherQueryResult fallback(String userInput) {
        log.warn("[Service] 降级为直调 OpenWeather");
        try {
            GeoLocation geo = openWeatherClient.geocoding(userInput.trim());
            List<DayForecast> forecasts = openWeatherClient.forecast(geo.lat(), geo.lon());

            if (forecasts.isEmpty()) {
                return errorResult("获取天气数据失败");
            }

            boolean anyRain = forecasts.stream().anyMatch(DayForecast::hasRain);
            return new WeatherQueryResult(geo.name(), forecasts, anyRain, null,
                    false, 0, null, null);

        } catch (IllegalArgumentException e) {
            return errorResult(e.getMessage());
        } catch (Exception e) {
            log.error("[Service] 降级路径也失败了", e);
            return errorResult("天气服务暂时不可用，请稍后重试");
        }
    }

    private WeatherQueryResult errorResult(String message) {
        return new WeatherQueryResult("", List.of(), false, message, false, 0, null, null);
    }

    private boolean isBusinessError(String msg) {
        return msg.contains("未找到城市") || msg.contains("未能识别");
    }
}
