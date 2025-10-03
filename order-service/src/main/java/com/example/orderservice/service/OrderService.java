package com.example.orderservice.service;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.User;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserClientService userClientService;
    private final AtomicLong idCounter = new AtomicLong(1);
    private final List<Order> orders = new ArrayList<>();

    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @Retry(name = "orderService")
    public Order createOrder(OrderRequest request) {
        log.info("Creando orden para usuario: {}", request.getUserId());
        
        // Obtener usuario usando el servicio con Circuit Breaker
        User user = userClientService.getUserById(request.getUserId());
        
        // Validar que el usuario esté activo (si no está en fallback)
        if (user.getActive() == null || !user.getActive()) {
            log.warn("Usuario inactivo o en modo fallback, procediendo con la orden");
        }
        
        // Crear orden
        Order order = Order.builder()
                .id(idCounter.getAndIncrement())
                .userId(request.getUserId())
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        
        orders.add(order);
        log.info("Orden creada exitosamente: {}", order.getId());
        return order;
    }

    public Order createOrderFallback(OrderRequest request, Exception e) {
        log.error("Fallback para crear orden, causa: {}", e.getMessage());
        
        // Crear orden básica en modo fallback
        return Order.builder()
                .id(-1L)
                .userId(request.getUserId())
                .productName(request.getProductName())
                .quantity(request.getQuantity())
                .price(request.getPrice())
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders);
    }

    public Order getOrderById(Long id) {
        return orders.stream()
                .filter(order -> order.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}