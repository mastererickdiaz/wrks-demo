package com.example.orderservice.dto;

import jakarta.validation.constraints.NotNull;

import com.example.orderservice.model.Order;

public record StatusTransitionRequest(@NotNull Order.OrderStatus status) {
}
