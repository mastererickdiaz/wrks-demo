package com.example.orderservice.model;

import java.math.BigDecimal;

public record OrderLineItem(String productName, Integer quantity, BigDecimal unitPrice) {

    public BigDecimal subtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
