package com.example.orderservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class OrderRequest {
    @NotNull
    private Long userId;
    
    @NotBlank
    private String productName;
    
    @Min(1)
    private Integer quantity;
    
    @NotNull
    @Min(0)
    private BigDecimal price;
}