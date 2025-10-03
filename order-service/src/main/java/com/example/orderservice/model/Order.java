package com.example.orderservice.model;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    private Long id;
    private Long userId;
    private String productName;
    private Integer quantity;
    private BigDecimal price;
    private OrderStatus status;
    private LocalDateTime createdAt;
    
    public enum OrderStatus {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
}