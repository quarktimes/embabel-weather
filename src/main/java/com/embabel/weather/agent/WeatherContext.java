package com.embabel.weather.agent;

import com.embabel.weather.model.DayForecast;
import com.embabel.weather.model.GeoLocation;
import com.embabel.weather.model.ParsedQuery;
import lombok.Data;

import java.util.List;

/**
 * Agent GOAP 工作内存 —— 在 Actions 之间传递中间状态
 */
@Data
public class WeatherContext {

    private String traceId;
    private String originalInput;
    private ParsedQuery parsedQuery;
    private GeoLocation coordinates;
    private List<DayForecast> forecasts;
    private List<AgentAuditRecord> auditRecords;
    private String aiAnalysis;
    private String errorMessage;
}
