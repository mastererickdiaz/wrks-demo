package com.example.orderservice.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor basicAuthRequestInterceptor() {
        return requestTemplate -> {
            // Headers comunes para todas las requests Feign
            requestTemplate.header("X-Service-Name", "order-service");
            requestTemplate.header("Content-Type", "application/json");
        };
    }
}