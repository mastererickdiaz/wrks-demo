package com.example.userservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class AppConfig {
    private Encryption encryption;
    private H2 h2;
    
    @Data
    public static class Encryption {
        private String key;
    }
    
    @Data
    public static class H2 {
        private Console console;
        
        @Data
        public static class Console {
            private String password;
        }
    }
}