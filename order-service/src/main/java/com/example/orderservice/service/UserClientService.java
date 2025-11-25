package com.example.orderservice.service;

import com.example.orderservice.client.UserServiceClient;
import com.example.orderservice.model.User;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserClientService {

    private static final Logger log = LoggerFactory.getLogger(UserClientService.class);

    private final UserServiceClient userServiceClient;

    public UserClientService(UserServiceClient userServiceClient) {
        this.userServiceClient = userServiceClient;
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "getUserFallback")
    @Retry(name = "userService")
    public User getUserById(Long userId) {
        log.info("Obteniendo usuario con ID: {}", userId);
        User user = userServiceClient.getUserById(userId);
        log.info("Usuario obtenido: {}", user.getName());
        return user;
    }

    public User getUserFallback(Long userId, Exception e) {
        log.warn("Fallback para usuario ID: {}, causa: {}", userId, e.getMessage());
        return new User(userId, "Usuario no disponible - Fallback", "fallback@example.com", null, false);
    }
}