package com.example.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.userservice.security.config.HttpSignatureProperties;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(HttpSignatureProperties.class)
public class UserServiceApplication {
    public static void main(String[] args) {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
        SpringApplication.run(UserServiceApplication.class, args);
    }
}