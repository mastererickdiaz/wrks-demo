package com.example.orderservice.service;

import com.example.orderservice.dto.NotificationEvent;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.exception.InvalidOrderTransitionException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.UserInactiveException;
import com.example.orderservice.exception.UserNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderLineItem;
import com.example.orderservice.model.OrderStatusHistoryEntry;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    // Transiciones de estado permitidas (FR-002): clave = estado actual,
    // valor = conjunto de estados destino válidos desde ese estado. Cualquier
    // par no listado aquí (incluyendo SHIPPED/DELIVERED/CANCELLED como origen)
    // se rechaza con InvalidOrderTransitionException.
    private static final Map<Order.OrderStatus, Set<Order.OrderStatus>> VALID_TRANSITIONS = Map.of(
            Order.OrderStatus.PENDING, Set.of(Order.OrderStatus.CONFIRMED, Order.OrderStatus.CANCELLED),
            Order.OrderStatus.CONFIRMED, Set.of(Order.OrderStatus.SHIPPED, Order.OrderStatus.CANCELLED),
            Order.OrderStatus.SHIPPED, Set.of(Order.OrderStatus.DELIVERED)
    );

    private final UserClientService userClientService;
    private final OrderNotificationService orderNotificationService;
    private final AtomicLong idCounter = new AtomicLong(1);
    private final List<Order> orders = new ArrayList<>();
    private final Map<Long, List<OrderStatusHistoryEntry>> historyByOrderId = new ConcurrentHashMap<>();

    @CircuitBreaker(name = "orderService", fallbackMethod = "createOrderFallback")
    @Retry(name = "orderService")
    public Order createOrder(OrderRequest request) {
        log.info("Creando orden para usuario: {}", request.userId());

        // Obtener usuario usando el servicio con Circuit Breaker.
        // UserClientService propaga UserNotFoundException si user-service responde 404
        // (rechazo de negocio, FR-011) y NO la confunde con una caída de infraestructura
        // (fallback, FR-008), que en cambio devuelve un User con active=null.
        User user = userClientService.getUserById(request.userId());

        if (user.active() == null) {
            // user-service no disponible (circuito abierto / timeout): se degrada
            // aceptando el pedido, conforme a FR-008. No es un rechazo de negocio.
            log.warn("user-service no disponible, creando pedido en modo degradado para userId={}", request.userId());
        } else if (!user.active()) {
            // Usuario real pero inactivo: rechazo de negocio explícito (FR-011).
            throw new UserInactiveException(request.userId());
        }

        List<OrderLineItem> items = toItems(request);
        BigDecimal total = calculateTotal(items);

        Order order = Order.builder()
                .id(idCounter.getAndIncrement())
                .userId(request.userId())
                .items(items)
                .totalPrice(total)
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        orders.add(order);
        log.info("Orden creada exitosamente: {} ({} líneas, total {})", order.id(), items.size(), total);
        return order;
    }

    private static List<OrderLineItem> toItems(OrderRequest request) {
        return request.items().stream()
                .map(i -> new OrderLineItem(i.productName(), i.quantity(), i.unitPrice()))
                .toList();
    }

    private static BigDecimal calculateTotal(List<OrderLineItem> items) {
        return items.stream()
                .map(OrderLineItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Mismo motivo que en UserClientService.getUserFallback: el fallback que coincide
    // por tipo de excepción se ejecuta sin importar ignore-exceptions, así que se
    // necesitan overloads explícitos para no enmascarar el rechazo de negocio (FR-011)
    // como una degradación de infraestructura (FR-008).
    public Order createOrderFallback(OrderRequest request, UserNotFoundException e) {
        throw e;
    }

    public Order createOrderFallback(OrderRequest request, UserInactiveException e) {
        throw e;
    }

    public Order createOrderFallback(OrderRequest request, Exception e) {
        log.error("Fallback para crear orden, causa: {}", e.getMessage());

        // Crear orden básica en modo fallback
        List<OrderLineItem> items = toItems(request);
        return Order.builder()
                .id(-1L)
                .userId(request.userId())
                .items(items)
                .totalPrice(calculateTotal(items))
                .status(Order.OrderStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    public List<Order> getAllOrders() {
        return new ArrayList<>(orders);
    }

    public Order getOrderById(Long id) {
        return orders.stream()
                .filter(order -> order.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    public synchronized Order transitionStatus(Long orderId, Order.OrderStatus newStatus) {
        int index = indexOf(orderId);
        Order current = orders.get(index);
        Order.OrderStatus fromStatus = current.status();

        Set<Order.OrderStatus> allowedTargets = VALID_TRANSITIONS.get(fromStatus);
        if (allowedTargets == null || !allowedTargets.contains(newStatus)) {
            log.warn("Transición rechazada para pedido {}: {} -> {}", orderId, fromStatus, newStatus);
            throw new InvalidOrderTransitionException(fromStatus, newStatus);
        }

        Order updated = Order.builder()
                .id(current.id())
                .userId(current.userId())
                .items(current.items())
                .totalPrice(current.totalPrice())
                .status(newStatus)
                .createdAt(current.createdAt())
                .build();
        orders.set(index, updated);

        OrderStatusHistoryEntry entry = new OrderStatusHistoryEntry(orderId, fromStatus, newStatus, LocalDateTime.now());
        historyByOrderId.computeIfAbsent(orderId, id -> new ArrayList<>()).add(entry);

        log.info("Pedido {} transicionó de {} a {}", orderId, fromStatus, newStatus);
        notifyStatusChangeSafely(updated, fromStatus);
        return updated;
    }

    // FR-006: ningún fallo al notificar (usuario no contactable, webhook caído,
    // excepción inesperada) puede revertir ni afectar la transición ya aplicada.
    // El try/catch es una segunda capa de seguridad además del fallbackMethod de
    // OrderNotificationService.send, por si la resolución del usuario falla antes
    // siquiera de llegar a publicar el evento.
    private void notifyStatusChangeSafely(Order order, Order.OrderStatus fromStatus) {
        try {
            User user = userClientService.getUserById(order.userId());
            if (user.active() == null || !user.active()) {
                log.warn("No se notifica el pedido {}: usuario {} no contactable (inactivo o desconocido)",
                        order.id(), order.userId());
                return;
            }
            NotificationEvent event = new NotificationEvent(
                    order.id(), user.email(), fromStatus, order.status(), LocalDateTime.now());
            orderNotificationService.send(event);
        } catch (Exception e) {
            log.warn("No se pudo notificar el pedido {}: {}", order.id(), e.getMessage());
        }
    }

    public List<OrderStatusHistoryEntry> getHistory(Long orderId) {
        indexOf(orderId);
        return new ArrayList<>(historyByOrderId.getOrDefault(orderId, List.of()));
    }

    private int indexOf(Long orderId) {
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).id().equals(orderId)) {
                return i;
            }
        }
        throw new OrderNotFoundException(orderId);
    }
}