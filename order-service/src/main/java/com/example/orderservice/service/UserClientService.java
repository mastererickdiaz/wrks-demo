package com.example.orderservice.service;

import com.example.orderservice.client.UserServiceClient;
import com.example.orderservice.exception.UserNotFoundException;
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
        log.info("Usuario obtenido: {}", user.name());
        return user;
    }

    // Resilience4j invoca el fallback cuyo parámetro de excepción coincida de forma
    // más específica con la excepción lanzada, independientemente de la configuración
    // de ignore-exceptions (esa configuración solo afecta las métricas del circuit
    // breaker, no qué fallback se ejecuta). Este overload evita que el rechazo de
    // negocio (FR-011) se enmascare como una caída de infraestructura (FR-008).
    public User getUserFallback(Long userId, UserNotFoundException e) {
        throw e;
    }

    public User getUserFallback(Long userId, Exception e) {
        log.warn("Fallback para usuario ID: {}, causa: {}", userId, e.getMessage());
        // active=null marca explícitamente "infraestructura caída / desconocido",
        // distinto de active=false (usuario real confirmado como inactivo, FR-011).
        return User.builder()
                .id(userId)
                .name("Usuario no disponible - Fallback")
                .email("fallback@example.com")
                .active(null)
                .build();
    }
}