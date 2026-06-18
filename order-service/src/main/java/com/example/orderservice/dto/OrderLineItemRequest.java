package com.example.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderLineItemRequest(
        @NotBlank String productName,
        @Min(1) Integer quantity,
        @NotNull @Min(0) BigDecimal unitPrice) {
}
