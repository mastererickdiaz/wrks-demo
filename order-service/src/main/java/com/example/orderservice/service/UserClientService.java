package com.example.orderservice.service;

import com.example.orderservice.client.UserServiceClient;
import com.example.orderservice.model.User;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserClientService {

    private final UserServiceClient userServiceClient;

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
        return User.builder()
                .id(userId)
                .name("Usuario no disponible - Fallback")
                .email("fallback@example.com")
                .active(false)
                .build();
    }
}