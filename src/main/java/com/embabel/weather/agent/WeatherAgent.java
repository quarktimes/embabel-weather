package com.embabel.weather.agent;

import com.embabel.agent.api.common.Ai;
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
import java.util.stream.Collectors;

/**
 * Embabel Agent — 3 天天气预报助手
 * <p>
 * 流程：parseQuery → geocode → forecast → analyze
 * 每一步在完成时记录审计轨迹（页面可见）。
 * LLM 调用通过 Embabel {@link Ai} 接口路由到 DeepSeek。
 */
@Slf4j
@Component
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
    //  入口方法
    // ═══════════════════════════════════════════════

    /**
     * 执行完整的天气查询 Agent 流程
     *
     * @param userInput 用户自然语言输入
     * @return WeatherContext 包含所有中间结果
     */
    public WeatherContext execute(String userInput) {
        WeatherContext ctx = new WeatherContext();
        ctx.setOriginalInput(userInput);
        String traceId = auditService.startTrace(userInput);
        ctx.setTraceId(traceId);

        try {
            // Step 1: 解析自然语言（纯城市名跳过 LLM，否则调 DeepSeek）
            long t1 = System.currentTimeMillis();
            ParsedQuery parsed = parseQuery(userInput);
            long d1 = System.currentTimeMillis() - t1;
            ctx.setParsedQuery(parsed);
            auditService.record(traceId, 1, "parseQuery",
                    userInput, parsed.cityName() + " / " + parsed.timeContext() + " / " + parsed.intent(), d1, true, null);

            if (!parsed.isValid()) {
                String err = "未能识别出城市名，请直接输入城市名，如「北京」";
                ctx.setErrorMessage(err);
                auditService.record(traceId, 1, "parseQuery", userInput, null, d1, false, err);
                return ctx;
            }

            // Step 2: 地理编码
            long t2 = System.currentTimeMillis();
            GeoLocation geo = openWeatherClient.geocoding(parsed.cityName());
            long d2 = System.currentTimeMillis() - t2;
            ctx.setCoordinates(geo);
            auditService.record(traceId, 2, "geocode",
                    parsed.cityName(), geo.name() + " (" + geo.lat() + ", " + geo.lon() + ")", d2, true, null);

            // Step 3: 获取 3 天预报
            long t3 = System.currentTimeMillis();
            List<DayForecast> forecasts = openWeatherClient.forecast(geo.lat(), geo.lon());
            long d3 = System.currentTimeMillis() - t3;
            ctx.setForecasts(forecasts);
            auditService.record(traceId, 3, "forecast",
                    geo.name() + " 坐标", forecasts.size() + " 天数据", d3, true, null);

            // Step 4: AI 分析
            long t4 = System.currentTimeMillis();
            String analysis = generateAnalysis(parsed, forecasts);
            long d4 = System.currentTimeMillis() - t4;
            ctx.setAiAnalysis(analysis);
            String analysisPreview = analysis != null
                    ? analysis.substring(0, Math.min(analysis.length(), 60)) + "..."
                    : "无分析";
            auditService.record(traceId, 4, "analyze",
                    parsed.cityName() + " 天气数据", analysisPreview, d4, true, null);

        } catch (IllegalArgumentException e) {
            log.warn("[Agent] 城市未找到: {}", e.getMessage());
            ctx.setErrorMessage(e.getMessage());
            auditService.record(traceId, 0, "error", userInput, null, 0, false, e.getMessage());
        } catch (Exception e) {
            log.error("[Agent] 执行异常", e);
            ctx.setErrorMessage("天气查询暂时不可用，请稍后重试");
            auditService.record(traceId, 0, "error", userInput, null, 0, false, e.getMessage());
        }

        // 附加审计记录到上下文（页面展示用）
        ctx.setAuditRecords(auditService.getTrace(traceId));
        return ctx;
    }

    // ═══════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════

    /** 自然语言 → 结构化解析（纯城市名跳过 LLM） */
    public ParsedQuery parseQuery(String userInput) {
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

    /** 生成 AI 天气分析 + 下雨提醒 */
    public String generateAnalysis(ParsedQuery query, List<DayForecast> forecasts) {
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
