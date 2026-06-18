package com.embabel.weather.model;

/**
 * 自然语言解析结果 —— Agent 从用户输入中提取的结构化信息
 *
 * @param originalInput 用户原始输入，如 "明天上海会下雨吗？"
 * @param cityName      提取的城市名，如 "上海"
 * @param timeContext   时间上下文，"今天"/"明天"/"后天"
 * @param intent        查询意图，current/forecast/rain_check
 */
public record ParsedQuery(
        String originalInput,
        String cityName,
        String timeContext,
        String intent
) {

    public static ParsedQuery unknown(String input) {
        return new ParsedQuery(input, "", "", "");
    }

    public boolean isValid() {
        return cityName != null && !cityName.isBlank();
    }
}
