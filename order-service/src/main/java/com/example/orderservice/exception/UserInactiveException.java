package com.example.orderservice.exception;

import lombok.Getter;

@Getter
public class UserInactiveException extends RuntimeException {

    private final Long userId;

    public UserInactiveException(Long userId) {
        super("Usuario inactivo: " + userId);
        this.userId = userId;
    }
}
