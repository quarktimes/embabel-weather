package com.embabel.weather.agent;

import com.embabel.weather.client.OpenWeatherClient;
import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import com.embabel.weather.model.ParsedQuery;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 3 天天气预报助手
 * <p>
 * 流程：parseQuery → geocode → forecast → analyze
 * parseQuery / analyze 调用 DeepSeek API（OpenAI 兼容协议）
 */
@Slf4j
@Component
public class WeatherAgent {

    private static final String DEEPSEEK_URL = "https://api.deepseek.com/chat/completions";

    @Autowired
    private OpenWeatherClient openWeatherClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${DEEPSEEK_API_KEY}")
    private String deepseekApiKey;

    private final RestClient restClient = RestClient.create();

    // ═══════════════════════════════════════════════
    //  入口方法
    // ═══════════════════════════════════════════════

    /**
     * 执行完整的天气查询流程
     *
     * @param userInput 用户自然语言输入
     * @return WeatherContext 包含所有中间结果
     */
    public WeatherContext execute(String userInput) {
        WeatherContext ctx = new WeatherContext();
        ctx.setOriginalInput(userInput);
        long start = System.currentTimeMillis();

        try {
            // Step 1: 解析自然语言
            ParsedQuery parsed = parseQuery(userInput);
            ctx.setParsedQuery(parsed);
            log.info("[Agent] parseQuery → city={}, time={}, intent={}",
                    parsed.cityName(), parsed.timeContext(), parsed.intent());

            if (!parsed.isValid()) {
                ctx.setErrorMessage("未能识别出城市名，请直接输入城市名，如「北京」");
                return ctx;
            }

            // Step 2: 地理编码
            GeoLocation geo = openWeatherClient.geocoding(parsed.cityName());
            ctx.setCoordinates(geo);
            log.info("[Agent] geocode → {} ({}, {})", geo.name(), geo.lat(), geo.lon());

            // Step 3: 获取 3 天预报
            List<DayForecast> forecasts = openWeatherClient.forecast(geo.lat(), geo.lon());
            ctx.setForecasts(forecasts);
            log.info("[Agent] forecast → {} 天数据", forecasts.size());

            // Step 4: AI 分析
            String analysis = generateAnalysis(parsed, forecasts);
            ctx.setAiAnalysis(analysis);
            log.info("[Agent] analyze → 完成");

        } catch (IllegalArgumentException e) {
            log.warn("[Agent] 城市未找到: {}", e.getMessage());
            ctx.setErrorMessage(e.getMessage());
        } catch (Exception e) {
            log.error("[Agent] 执行异常", e);
            ctx.setErrorMessage("天气查询暂时不可用，请稍后重试");
        }

        long elapsed = System.currentTimeMillis() - start;
        log.info("[Agent] 总耗时 {}ms", elapsed);
        return ctx;
    }

    // ═══════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════

    /**
     * Action 1: 自然语言 → 结构化解析
     * <p>
     * 调用 DeepSeek 从用户输入中提取城市名、时间、意图。
     */
    public ParsedQuery parseQuery(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return ParsedQuery.unknown(userInput);
        }

        String trimmed = userInput.trim();
        // 纯城市名直接作为城市名，无需 LLM
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
            String jsonText = callDeepSeek(prompt);
            JsonNode json = objectMapper.readTree(extractJson(jsonText));
            String city = getJsonText(json, "city");
            String time = getJsonText(json, "time");
            String intent = getJsonText(json, "intent");

            if (city == null || city.isBlank()) {
                return new ParsedQuery(userInput, trimmed, time, intent);
            }
            return new ParsedQuery(userInput, city, time, intent);

        } catch (Exception e) {
            log.warn("[Agent] LLM 解析失败，将整段输入作为城市名: {}", e.getMessage());
            return new ParsedQuery(userInput, trimmed, "today", "forecast");
        }
    }

    /**
     * Action 4: 生成 AI 天气分析 + 下雨提醒
     */
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
            return callDeepSeek(prompt);
        } catch (Exception e) {
            log.warn("[Agent] LLM 分析失败，使用默认文本: {}", e.getMessage());
            if (anyRain) {
                return "未来 3 天有雨，出门记得带伞 🌂";
            }
            return null;
        }
    }

    // ═══════════════════════════════════════════════
    //  DeepSeek API 调用
    // ═══════════════════════════════════════════════

    /**
     * 调用 DeepSeek Chat API（OpenAI 兼容协议）
     */
    private String callDeepSeek(String userPrompt) {
        var request = new DeepSeekRequest("deepseek-chat", userPrompt, 0.7, 2000);

        DeepSeekResponse response = restClient.post()
                .uri(DEEPSEEK_URL)
                .header("Authorization", "Bearer " + deepseekApiKey)
                .header("Content-Type", "application/json")
                .body(request)
                .retrieve()
                .body(DeepSeekResponse.class);

        if (response != null && response.choices() != null && !response.choices().isEmpty()) {
            return response.choices().get(0).message().content();
        }
        throw new RuntimeException("DeepSeek 返回空结果");
    }

    // ─── DeepSeek API DTO ───

    private record DeepSeekRequest(
            String model,
            List<Message> messages,
            double temperature,
            int max_tokens
    ) {
        DeepSeekRequest(String model, String userContent, double temperature, int maxTokens) {
            this(model, List.of(new Message("user", userContent)), temperature, maxTokens);
        }
    }

    private record Message(String role, String content) {}

    private record DeepSeekResponse(
            List<Choice> choices
    ) {}

    private record Choice(
            Message message
    ) {}

    // ═══════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════

    /** 判断是否为纯城市名（中文 2-4 字，不带问句关键词） */
    private boolean isPlainCityName(String text) {
        if (text.length() < 2 || text.length() > 6) return false;
        String[] keywords = {"吗", "么", "？", "?", "天气", "下雨", "温度", "多少", "怎么", "如何"};
        for (String kw : keywords) {
            if (text.contains(kw)) return false;
        }
        return text.matches("^[\\u4e00-\\u9fff]+$");
    }

    /** 从 LLM 回复中提取 JSON（去除可能的 markdown 包裹） */
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
