package com.example.orderservice.dto;

import com.example.orderservice.model.Order;

import java.time.LocalDateTime;

public record NotificationEvent(
        Long orderId,
        String recipientEmail,
        Order.OrderStatus fromStatus,
        Order.OrderStatus toStatus,
        LocalDateTime occurredAt) {
}
