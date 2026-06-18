package com.example.orderservice.exception;

import lombok.Getter;

@Getter
public class OrderNotFoundException extends RuntimeException {

    private final Long orderId;

    public OrderNotFoundException(Long orderId) {
        super("Pedido no encontrado: " + orderId);
        this.orderId = orderId;
    }
}
