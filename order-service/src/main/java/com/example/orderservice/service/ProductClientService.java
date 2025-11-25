package com.example.orderservice.service;

import com.example.orderservice.client.ProductServiceClient;
import com.example.orderservice.model.Product;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class ProductClientService {

    private static final Logger log = LoggerFactory.getLogger(ProductClientService.class);

    private final ProductServiceClient productServiceClient;

    public ProductClientService(ProductServiceClient productServiceClient) {
        this.productServiceClient = productServiceClient;
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    @Retry(name = "productService")
    public Product getProductById(Long productId) {
        log.info("Obteniendo producto con ID: {}", productId);
        Product product = productServiceClient.getProductById(productId);
        log.info("Producto obtenido: {}", product.name());
        return product;
    }

    public Product getProductFallback(Long productId, Exception e) {
        log.warn("Fallback para producto ID: {}, causa: {}", productId, e.getMessage());
        return new Product(productId, "Producto no disponible - Fallback", "N/A", BigDecimal.ZERO);
    }
}
