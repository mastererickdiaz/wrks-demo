package com.example.orderservice.model;

import java.time.LocalDateTime;

public record OrderStatusHistoryEntry(
        Long orderId,
        Order.OrderStatus fromStatus,
        Order.OrderStatus toStatus,
        LocalDateTime changedAt) {
}
