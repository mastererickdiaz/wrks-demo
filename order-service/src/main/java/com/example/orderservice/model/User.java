package com.example.orderservice.model;

import lombok.Builder;

@Builder
public record User(Long id, String name, String email, String phone, Boolean active) {
}
