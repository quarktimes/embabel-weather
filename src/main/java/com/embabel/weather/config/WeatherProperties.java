package com.embabel.weather.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "weather.openweather")
public class WeatherProperties {

    private String apiKey;
    private String baseUrl;
    private String geoUrl;
    private String units = "metric";
    private String lang = "zh_cn";
}
