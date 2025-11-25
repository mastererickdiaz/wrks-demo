package com.example.orderservice.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.exception.UserNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.Product;
import com.example.orderservice.model.User;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final UserClientService userClientService;
    private final ProductClientService productClientService;
    private final AtomicLong idCounter = new AtomicLong(1);
    private final List<Order> orders = new ArrayList<>();

    public OrderService(UserClientService userClientService,
            ProductClientService productClientService) {
        this.userClientService = userClientService;
        this.productClientService = productClientService;
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @Retry(name = "orderService")
    public Order createOrder(OrderRequest request) {
        log.info("Creando orden para usuario: {} y producto: {}", request.getUserId(),
                request.getProductId());

        // Obtener usuario
        User user = userClientService.getUserById(request.getUserId());
        // Si el usuario es null, está inactivo o en modo fallback, no se puede crear la orden.
        if (user == null || user.getId() == null || user.getActive() == null || !user.getActive()) {
            throw new UserNotFoundException(
                    "No se puede crear la orden. Usuario inactivo o no disponible, ID: "
                            + request.getUserId());
        }

        // Obtener producto
        Product product = productClientService.getProductById(request.getProductId());

        // Crear orden
        Order order = new Order(idCounter.getAndIncrement(), request.getUserId(), product.id(),
                product.name(), request.getQuantity(), product.price(), Order.OrderStatus.PENDING,
                LocalDateTime.now());

        orders.add(order);
        log.info("Orden creada exitosamente: {}", order.getId());
        return order;
    }

    public Order createOrderFallback(OrderRequest request, Exception e) {
        log.error("Fallback para crear orden, causa: {}", e.getMessage());

        // Crear orden básica en modo fallback
        return new Order(-1L, request.getUserId(), request.getProductId(), "Producto no disponible",
                request.getQuantity(), null, Order.OrderStatus.PENDING, LocalDateTime.now());
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders);
    }

    public Order getOrderById(Long id) {
        return orders.stream().filter(order -> order.getId().equals(id)).findFirst().orElse(null);
    }

    @CircuitBreaker(name = "orderService", fallbackMethod = "getUserOrdersFallback")
    @Retry(name = "orderService")
    public List<Order> getUserOrders(Long userId) {
        log.info("Buscando órdenes para el usuario con ID: {}", userId);
        // Verificar que el usuario existe antes de buscar sus órdenes.
        User user = userClientService.getUserById(userId);

        // Si el usuario es null o está en modo fallback sin un ID real, podría indicar un problema.
        if (user == null || user.getId() == null) {
            throw new UserNotFoundException(
                    "Usuario no encontrado o servicio no disponible, ID: " + userId);
        }

        return orders.stream().filter(order -> order.getUserId().equals(userId)).toList();
    }

    public List<Order> getUserOrdersFallback(Long userId, Exception e) {
        log.error("Fallback para obtener órdenes del usuario {}, causa: {}", userId,
                e.getMessage());
        return List.of(); // Devolver una lista vacía como fallback
    }
}
