package com.embabel.weather.agent;

import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.weather.client.OpenWeatherClient;
import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import com.embabel.weather.model.ParsedQuery;
import com.embabel.weather.model.WeatherQueryResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Embabel Agent — GOAP 自主规划天气预报
 * <p>
 * 引擎根据 Action 的输入/输出类型自动串联：
 *   UserQuery → extractCity → geocode → forecast → reply
 */
@Slf4j
@Agent(name = "weatherAgent", description = "3天天气预报助手，查询天气并提醒带伞")
public class WeatherAgent {

    /** 用户输入的包装类型 —— GOAP 引擎以此启动链路 */
    public record UserQuery(String text) {}

    @Autowired
    private OpenWeatherClient openWeatherClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentAuditService auditService;

    // ═══════════════════════════════════════════════
    //  Action 1: 城市名提取
    // ═══════════════════════════════════════════════

    @Action(description = "从自然语言中提取城市名、时间和意图")
    public ParsedQuery extractCity(OperationContext ctx, UserQuery query) {
        String input = query.text();
        long start = System.currentTimeMillis();
        String traceId = getTraceId(ctx);

        if (input == null || input.isBlank()) {
            auditService.record(traceId, 1, "extractCity", input, null, 0, false, "输入为空");
            return ParsedQuery.unknown(input);
        }

        String trimmed = input.trim();
        if (isPlainCityName(trimmed)) {
            var result = new ParsedQuery(trimmed, trimmed, "today", "forecast");
            auditService.record(traceId, 1, "extractCity", input,
                    result.cityName() + " / " + result.timeContext() + " / " + result.intent(),
                    System.currentTimeMillis() - start, true, null);
            ctx.set("traceId", traceId);
            return result;
        }

        String prompt = """
                你是一个中文实体提取助手。从用户的天气查询中提取以下信息：

                用户输入：%s

                请提取：
                - city: 提到的城市名（中文，如"北京""上海""广州"）
                - time: 时间上下文（"今天"/"明天"/"后天"/"这周末"/空）
                - intent: 查询意图（"current"当前天气/"forecast"预报/"rain_check"是否有雨）

                以 JSON 格式返回：{"city": "...", "time": "...", "intent": "..."}
                找不到对应字段则返回空字符串。
                """.formatted(input);

        try {
            var ai = ctx.ai();
            String jsonText = ai.withDefaultLlm().generateText(prompt);
            JsonNode json = objectMapper.readTree(extractJson(jsonText));
            String city = getJsonText(json, "city");
            String time = getJsonText(json, "time");
            String intent = getJsonText(json, "intent");
            var result = (city == null || city.isBlank())
                    ? new ParsedQuery(input, trimmed, time, intent)
                    : new ParsedQuery(input, city, time, intent);

            auditService.record(traceId, 1, "extractCity", input,
                    result.cityName() + " / " + result.timeContext() + " / " + result.intent(),
                    System.currentTimeMillis() - start, true, null);
            return result;
        } catch (Exception e) {
            log.warn("[Agent] LLM 解析失败: {}", e.getMessage());
            var result = new ParsedQuery(input, trimmed, "today", "forecast");
            auditService.record(traceId, 1, "extractCity", input,
                    result.cityName() + " / " + result.timeContext() + " / " + result.intent(),
                    System.currentTimeMillis() - start, true, null);
            return result;
        }
    }

    // ═══════════════════════════════════════════════
    //  Action 2: 地理编码
    // ═══════════════════════════════════════════════

    @Action(description = "将城市名转换为经纬度坐标")
    public GeoLocation geocode(OperationContext ctx, ParsedQuery parsed) {
        long start = System.currentTimeMillis();
        String traceId = getTraceId(ctx);
        GeoLocation result = openWeatherClient.geocoding(parsed.cityName());
        auditService.record(traceId, 2, "geocode", parsed.cityName(),
                result.name() + " (" + result.lat() + ", " + result.lon() + ")",
                System.currentTimeMillis() - start, true, null);
        return result;
    }

    // ═══════════════════════════════════════════════
    //  Action 3: 天气预报
    // ═══════════════════════════════════════════════

    @Action(description = "获取 3 天天气预报")
    public List<DayForecast> forecast(OperationContext ctx, GeoLocation geo) {
        long start = System.currentTimeMillis();
        String traceId = getTraceId(ctx);
        List<DayForecast> result = openWeatherClient.forecast(geo.lat(), geo.lon());
        auditService.record(traceId, 3, "forecast", geo.name(),
                result.size() + " 天数据", System.currentTimeMillis() - start, true, null);
        return result;
    }

    // ═══════════════════════════════════════════════
    //  Goal: 最终回复
    // ═══════════════════════════════════════════════

    @Action
    @AchievesGoal(description = "回答用户天气问题")
    public WeatherQueryResult reply(OperationContext ctx, ParsedQuery parsed,
                                     List<DayForecast> forecasts, Ai ai) {
        long start = System.currentTimeMillis();
        String traceId = getTraceId(ctx);

        boolean anyRain = forecasts.stream().anyMatch(DayForecast::hasRain);
        String analysis = generateAnalysis(parsed, forecasts, ai, anyRain);

        auditService.record(traceId, 4, "reply", parsed.cityName(),
                analysis != null ? preview(analysis) : "无分析",
                System.currentTimeMillis() - start, true, null);

        String cityName = ctx.get("geocode") instanceof GeoLocation g
                ? g.name() : parsed.cityName();

        return new WeatherQueryResult(cityName, forecasts, anyRain, analysis,
                false, System.currentTimeMillis() - start,
                traceId, auditService.getTrace(traceId));
    }

    // ═══════════════════════════════════════════════
    //  AI 分析
    // ═══════════════════════════════════════════════

    private String generateAnalysis(ParsedQuery query, List<DayForecast> forecasts,
                                     Ai ai, boolean anyRain) {
        if (forecasts == null || forecasts.isEmpty()) return null;

        String forecastText = forecasts.stream()
                .map(d -> String.format("%s（%s）：%s，%d~%d°C%s",
                        d.weekday(), d.date(), d.weather(),
                        d.tempMin(), d.tempMax(),
                        d.hasRain() ? " 🌧" : ""))
                .collect(Collectors.joining("\n"));

        String prompt = """
                你是一个友好的天气预报助手。基于以下 3 天的天气数据，用中文给出简短分析。

                重点关注：
                1. 整体天气趋势
                2. 是否有雨（如果有，明确指出时段）
                3. 如果某天有雨，强调带伞提醒
                4. 温度变化提醒（如有大幅波动）

                天气数据：
                城市：%s
                %s

                用户关注的天气问题（如果有）：%s

                要求：回答不超过 120 字，口语化、亲切，使用 emoji。
                """.formatted(query.cityName(), forecastText, query.originalInput());

        try {
            return ai.withDefaultLlm().generateText(prompt);
        } catch (Exception e) {
            log.warn("[Agent] LLM 分析失败: {}", e.getMessage());
            if (anyRain) return "未来 3 天有雨，出门记得带伞 🌂";
            return null;
        }
    }

    // ═══════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════

    private String getTraceId(OperationContext ctx) {
        return ctx.getProcessContext().getAgentProcess().getId();
    }

    private boolean isPlainCityName(String text) {
        if (text.length() < 2 || text.length() > 6) return false;
        String[] keywords = {"吗", "么", "？", "?", "天气", "下雨", "温度", "多少", "怎么", "如何"};
        for (String kw : keywords) {
            if (text.contains(kw)) return false;
        }
        return text.matches("^[\\u4e00-\\u9fff]+$");
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        String t = text.trim();
        if (t.startsWith("```json")) t = t.substring(7);
        else if (t.startsWith("```")) t = t.substring(3);
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.trim();
    }

    private String getJsonText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null ? "" : value.asText("");
    }

    private static String preview(String text) {
        if (text == null) return "无分析";
        return text.substring(0, Math.min(text.length(), 60)) + "...";
    }
}
