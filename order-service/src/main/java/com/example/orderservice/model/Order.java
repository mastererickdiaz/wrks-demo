package com.example.orderservice.model;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record Order(
        Long id,
        Long userId,
        List<OrderLineItem> items,
        BigDecimal totalPrice,
        OrderStatus status,
        LocalDateTime createdAt) {

    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
}
