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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
@Agent(name = "weatherAgent", description = "3天天气预报助手，查询天气并提醒带伞")
public class WeatherAgent {

    @Autowired
    private OpenWeatherClient openWeatherClient;

    @Autowired
    private Ai ai;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentAuditService auditService;

    // ═══════════════════════════════════════════════
    //  Goal
    // ═══════════════════════════════════════════════

    /** Service 调用的快捷入口（无 OperationContext 时） */
    public WeatherContext execute(String userInput) {
        return execute(null, userInput);
    }

    @Action(description = "回答用户天气查询")
    @AchievesGoal(description = "回答用户天气查询")
    public WeatherContext execute(OperationContext ctx, String userInput) {
        WeatherContext result = new WeatherContext();
        result.setOriginalInput(userInput);
        String traceId = auditService.startTrace(userInput);
        result.setTraceId(traceId);

        try {
            ParsedQuery parsed = auditParsedQuery(ctx, userInput, traceId);
            result.setParsedQuery(parsed);
            if (!parsed.isValid()) {
                result.setErrorMessage("未能识别出城市名，请直接输入城市名，如「北京」");
                return result;
            }

            GeoLocation geo = auditStep(traceId, 2, "geocode", parsed.cityName(),
                    () -> geocode(ctx, parsed.cityName()),
                    g -> g.name() + " (" + g.lat() + ", " + g.lon() + ")");
            result.setCoordinates(geo);

            List<DayForecast> forecasts = auditStep(traceId, 3, "forecast", geo.name(),
                    () -> forecast(ctx, geo.lat(), geo.lon()),
                    f -> f.size() + " 天数据");
            result.setForecasts(forecasts);

            String analysis = auditStep(traceId, 4, "analyze", parsed.cityName() + " 天气数据",
                    () -> analyze(ctx, parsed, forecasts),
                    a -> a != null ? a.substring(0, Math.min(a.length(), 60)) + "..." : "无分析");
            result.setAiAnalysis(analysis);

        } catch (IllegalArgumentException e) {
            log.warn("[Agent] 城市未找到: {}", e.getMessage());
            result.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            log.error("[Agent] 执行异常", e);
            result.setErrorMessage("天气查询暂时不可用，请稍后重试");
        }

        result.setAuditRecords(auditService.getTrace(traceId));
        return result;
    }

    // ═══════════════════════════════════════════════
    //  GOAP 子步骤
    // ═══════════════════════════════════════════════

    @Action(description = "从自然语言中提取城市名、时间和意图")
    public ParsedQuery parseQuery(OperationContext ctx, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return ParsedQuery.unknown(userInput);
        }
        String trimmed = userInput.trim();
        if (isPlainCityName(trimmed)) {
            return new ParsedQuery(trimmed, trimmed, "today", "forecast");
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
                """.formatted(userInput);

        try {
            String jsonText = ai.withDefaultLlm().generateText(prompt);
            JsonNode json = objectMapper.readTree(extractJson(jsonText));
            String city = getJsonText(json, "city");
            String time = getJsonText(json, "time");
            String intent = getJsonText(json, "intent");
            if (city == null || city.isBlank()) {
                return new ParsedQuery(userInput, trimmed, time, intent);
            }
            return new ParsedQuery(userInput, city, time, intent);
        } catch (Exception e) {
            log.warn("[Agent] LLM 解析失败: {}", e.getMessage());
            return new ParsedQuery(userInput, trimmed, "today", "forecast");
        }
    }

    @Action(description = "将城市名转换为经纬度坐标")
    public GeoLocation geocode(OperationContext ctx, String cityName) {
        return openWeatherClient.geocoding(cityName);
    }

    @Action(description = "获取 3 天天气预报")
    public List<DayForecast> forecast(OperationContext ctx, double lat, double lon) {
        return openWeatherClient.forecast(lat, lon);
    }

    @Action(description = "生成 AI 天气分析和下雨提醒")
    public String analyze(OperationContext ctx, ParsedQuery query, List<DayForecast> forecasts) {
        if (forecasts == null || forecasts.isEmpty()) return null;

        boolean anyRain = forecasts.stream().anyMatch(DayForecast::hasRain);

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
    //  审计模板
    // ═══════════════════════════════════════════════

    /** parseQuery 特殊处理（含校验 + 错误审计） */
    private ParsedQuery auditParsedQuery(OperationContext ctx, String userInput, String traceId) {
        long start = System.currentTimeMillis();
        try {
            ParsedQuery parsed = parseQuery(ctx, userInput);
            String output = parsed.cityName() + " / " + parsed.timeContext() + " / " + parsed.intent();
            auditService.record(traceId, 1, "parseQuery", userInput, output,
                    System.currentTimeMillis() - start, true, null);

            if (!parsed.isValid()) {
                auditService.record(traceId, 1, "parseQuery", userInput, null,
                        System.currentTimeMillis() - start, false, "未能识别出城市名");
            }
            return parsed;
        } catch (Exception e) {
            auditService.record(traceId, 1, "parseQuery", userInput, null,
                    System.currentTimeMillis() - start, false, e.getMessage());
            throw e;
        }
    }

    private <T> T auditStep(String traceId, int order, String stepName, String input,
                            Supplier<T> action, Function<T, String> outputFormatter) {
        long start = System.currentTimeMillis();
        try {
            T result = action.get();
            auditService.record(traceId, order, stepName, input,
                    outputFormatter.apply(result),
                    System.currentTimeMillis() - start, true, null);
            return result;
        } catch (Exception e) {
            auditService.record(traceId, order, stepName, input, null,
                    System.currentTimeMillis() - start, false, e.getMessage());
            throw e;
        }
    }

    // ═══════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════

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
}
