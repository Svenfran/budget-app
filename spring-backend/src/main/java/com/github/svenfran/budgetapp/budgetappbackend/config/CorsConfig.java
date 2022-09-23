package com.github.svenfran.budgetapp.budgetappbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("Origin", "Access-Control-Allow-Origin", "Content-Type",
                                "Accept", "Authorization", "Origin, Accept", "X-Requested-With",
                                "Access-Control-Request-Method", "Access-Control-Request-Headers")
                        .allowedOrigins("http://localhost", "http://localhost:8100");
            }
        };
    }
}
