package com.embabel.weather.controller;

import com.embabel.weather.model.WeatherQueryResult;
import com.embabel.weather.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/")
    public String showHome() {
        return "index";
    }

    @GetMapping("/weather")
    public String queryWeather(
            @RequestParam("q") String query,
            Model model) {

        model.addAttribute("query", query);

        if (query == null || query.isBlank()) {
            model.addAttribute("error", "请输入城市或天气问题");
            return "index";
        }

        WeatherQueryResult result = weatherService.queryWeather(query);
        model.addAttribute("result", result);

        return "index";
    }
}
