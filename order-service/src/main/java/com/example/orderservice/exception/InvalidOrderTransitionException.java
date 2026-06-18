package com.example.orderservice.exception;

import com.example.orderservice.model.Order;
import lombok.Getter;

@Getter
public class InvalidOrderTransitionException extends RuntimeException {

    private final Order.OrderStatus fromStatus;
    private final Order.OrderStatus toStatus;

    public InvalidOrderTransitionException(Order.OrderStatus fromStatus, Order.OrderStatus toStatus) {
        super("Transición inválida: " + fromStatus + " -> " + toStatus);
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }
}
