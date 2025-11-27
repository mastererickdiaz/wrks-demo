package com.example.orderservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.orderservice.security.config.HttpSignatureProperties;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableConfigurationProperties(HttpSignatureProperties.class)
public class OrderServiceApplication {
    public static void main(String[] args) {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}