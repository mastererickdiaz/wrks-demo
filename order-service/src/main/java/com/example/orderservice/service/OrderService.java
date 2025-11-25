package com.example.orderservice.service;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.Product;
import com.example.orderservice.model.User;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final UserClientService userClientService;
    private final ProductClientService productClientService;
    private final AtomicLong idCounter = new AtomicLong(1);
    private final List<Order> orders = new ArrayList<>();

    public OrderService(UserClientService userClientService, ProductClientService productClientService) {
        this.userClientService = userClientService;
        this.productClientService = productClientService;
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @Retry(name = "orderService")
    public Order createOrder(OrderRequest request) {
        log.info("Creando orden para usuario: {} y producto: {}", request.getUserId(), request.getProductId());

        // Obtener usuario
        User user = userClientService.getUserById(request.getUserId());
        if (user.getActive() == null || !user.getActive()) {
            log.warn("Usuario inactivo o en modo fallback, procediendo con la orden");
        }

        // Obtener producto
        Product product = productClientService.getProductById(request.getProductId());

        // Crear orden
        Order order = new Order(idCounter.getAndIncrement(), request.getUserId(),
                product.id(), product.name(), request.getQuantity(),
                product.price(), Order.OrderStatus.PENDING,
                LocalDateTime.now());

        orders.add(order);
        log.info("Orden creada exitosamente: {}", order.getId());
        return order;
    }

    public Order createOrderFallback(OrderRequest request, Exception e) {
        log.error("Fallback para crear orden, causa: {}", e.getMessage());

        // Crear orden básica en modo fallback
        return new Order(-1L, request.getUserId(),
                request.getProductId(), "Producto no disponible", request.getQuantity(),
                null, Order.OrderStatus.PENDING,
                LocalDateTime.now());
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders);
    }

    public Order getOrderById(Long id) {
        return orders.stream().filter(order -> order.getId().equals(id)).findFirst().orElse(null);
    }
}
