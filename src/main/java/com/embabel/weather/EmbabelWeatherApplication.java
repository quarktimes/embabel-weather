package com.embabel.weather;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class EmbabelWeatherApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbabelWeatherApplication.class, args);
    }
}
