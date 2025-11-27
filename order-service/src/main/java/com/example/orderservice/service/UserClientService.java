package com.example.orderservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.orderservice.client.UserServiceClient;
import com.example.orderservice.model.User;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

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
        return new User(userId, "Usuario no disponible - Fallback", "fallback@example.com", null,
                false);
    }

    @CircuitBreaker(name = "userService", fallbackMethod = "userExistsFallback")
    @Retry(name = "userService")
    public boolean userExists(Long userId) {
        log.info("Verificando si existe el usuario con ID: {}", userId);
        boolean exists = userServiceClient.userExists(userId);
        log.info("La verificación del usuario con ID: {} resultó en: {}", userId, exists);
        return exists;
    }

    public boolean userExistsFallback(Long userId, Exception e) {
        log.warn("Fallback para la verificación de existencia del usuario ID: {}, causa: {}", userId, e.getMessage());
        return false; // Asumir que el usuario no existe si el servicio no responde
    }
}
