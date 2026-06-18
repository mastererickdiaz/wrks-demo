package com.example.orderservice.exception;

import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException {

    private final Long userId;

    public UserNotFoundException(Long userId) {
        super("Usuario no encontrado: " + userId);
        this.userId = userId;
    }
}
