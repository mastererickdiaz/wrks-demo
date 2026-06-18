package com.example.orderservice.service;

import com.example.orderservice.dto.OrderLineItemRequest;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.exception.InvalidOrderTransitionException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.UserInactiveException;
import com.example.orderservice.exception.UserNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatusHistoryEntry;
import com.example.orderservice.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private UserClientService userClientService;

    @Mock
    private OrderNotificationService orderNotificationService;

    private OrderService orderService;

    private OrderRequest request;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(userClientService, orderNotificationService);
        request = new OrderRequest(1L, List.of(
                new OrderLineItemRequest("Teclado mecánico", 2, BigDecimal.valueOf(89.90))));
    }

    @Test
    void creaPedidoCuandoElUsuarioExisteYEstaActivo() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(true).build());

        Order order = orderService.createOrder(request);

        assertThat(order.status()).isEqualTo(Order.OrderStatus.PENDING);
        assertThat(order.userId()).isEqualTo(1L);
    }

    @Test
    void rechazaPedidoCuandoUserServiceLanzaUserNotFoundException() {
        when(userClientService.getUserById(1L)).thenThrow(new UserNotFoundException(1L));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void rechazaPedidoCuandoElUsuarioExisteYEstaInactivo() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(false).build());

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(UserInactiveException.class);
    }

    @Test
    void creaPedidoEnModoDegradadoCuandoUserServiceNoEstaDisponible() {
        // UserClientService.getUserFallback marca active=null para señalar
        // infraestructura caída, distinto de un rechazo de negocio (FR-008 vs FR-011).
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Usuario no disponible - Fallback")
                        .email("fallback@example.com").active(null).build());

        Order order = orderService.createOrder(request);

        assertThat(order.status()).isEqualTo(Order.OrderStatus.PENDING);
    }

    private Long crearPedidoActivo() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(true).build());
        return orderService.createOrder(request).id();
    }

    @Test
    void transicionValidaActualizaEstadoYRegistraHistorial() {
        Long orderId = crearPedidoActivo();

        Order updated = orderService.transitionStatus(orderId, Order.OrderStatus.CONFIRMED);

        assertThat(updated.status()).isEqualTo(Order.OrderStatus.CONFIRMED);
        List<OrderStatusHistoryEntry> history = orderService.getHistory(orderId);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).fromStatus()).isEqualTo(Order.OrderStatus.PENDING);
        assertThat(history.get(0).toStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    @Test
    void transicionConSaltoDeEstadoSeRechaza() {
        Long orderId = crearPedidoActivo();

        assertThatThrownBy(() -> orderService.transitionStatus(orderId, Order.OrderStatus.SHIPPED))
                .isInstanceOf(InvalidOrderTransitionException.class);
        assertThat(orderService.getHistory(orderId)).isEmpty();
    }

    @Test
    void transicionDesdeEstadoTerminalSeRechaza() {
        Long orderId = crearPedidoActivo();
        orderService.transitionStatus(orderId, Order.OrderStatus.CONFIRMED);
        orderService.transitionStatus(orderId, Order.OrderStatus.SHIPPED);
        orderService.transitionStatus(orderId, Order.OrderStatus.DELIVERED);

        assertThatThrownBy(() -> orderService.transitionStatus(orderId, Order.OrderStatus.CANCELLED))
                .isInstanceOf(InvalidOrderTransitionException.class);
    }

    @Test
    void transicionSobrePedidoInexistenteLanzaOrderNotFoundException() {
        assertThatThrownBy(() -> orderService.transitionStatus(999L, Order.OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void historialVacioParaPedidoSinTransiciones() {
        Long orderId = crearPedidoActivo();

        assertThat(orderService.getHistory(orderId)).isEmpty();
    }

    @Test
    void historialConMultiplesTransicionesEnOrdenCronologico() {
        Long orderId = crearPedidoActivo();
        orderService.transitionStatus(orderId, Order.OrderStatus.CONFIRMED);
        orderService.transitionStatus(orderId, Order.OrderStatus.SHIPPED);

        List<OrderStatusHistoryEntry> history = orderService.getHistory(orderId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).toStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(history.get(1).toStatus()).isEqualTo(Order.OrderStatus.SHIPPED);
    }

    @Test
    void historialDePedidoInexistenteLanzaOrderNotFoundException() {
        assertThatThrownBy(() -> orderService.getHistory(999L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void transicionValidaInvocaLaNotificacion() {
        Long orderId = crearPedidoActivo();

        orderService.transitionStatus(orderId, Order.OrderStatus.CONFIRMED);

        verify(orderNotificationService).send(any());
    }

    @Test
    void fallaDeNotificacionNoAfectaLaTransicionYaAplicada() {
        Long orderId = crearPedidoActivo();
        doThrow(new RuntimeException("webhook caído")).when(orderNotificationService).send(any());

        Order updated = orderService.transitionStatus(orderId, Order.OrderStatus.CONFIRMED);

        assertThat(updated.status()).isEqualTo(Order.OrderStatus.CONFIRMED);
        assertThat(orderService.getHistory(orderId)).hasSize(1);
    }

    @Test
    void noSeNotificaCuandoElUsuarioEstaInactivo() {
        Long orderId = crearPedidoActivo();
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(false).build());

        Order updated = orderService.transitionStatus(orderId, Order.OrderStatus.CONFIRMED);

        assertThat(updated.status()).isEqualTo(Order.OrderStatus.CONFIRMED);
        verify(orderNotificationService, never()).send(any());
    }

    @Test
    void creaPedidoConVariasLineasCalculaElTotalCorrectamente() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(true).build());
        OrderRequest multiItemRequest = new OrderRequest(1L, List.of(
                new OrderLineItemRequest("Teclado mecánico", 1, BigDecimal.valueOf(89.90)),
                new OrderLineItemRequest("Mouse", 2, BigDecimal.valueOf(19.90)),
                new OrderLineItemRequest("Mousepad", 1, BigDecimal.valueOf(9.90))));

        Order order = orderService.createOrder(multiItemRequest);

        assertThat(order.items()).hasSize(3);
        assertThat(order.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(89.90 + 2 * 19.90 + 9.90));
    }

    @Test
    void creaPedidoConUnaSolaLinea() {
        Order order = crearPedidoActivoYDevolverOrden();

        assertThat(order.items()).hasSize(1);
        assertThat(order.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(2 * 89.90));
    }

    @Test
    void preservaElOrdenDeLasLineasEnviado() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(true).build());
        OrderRequest multiItemRequest = new OrderRequest(1L, List.of(
                new OrderLineItemRequest("Primero", 1, BigDecimal.ONE),
                new OrderLineItemRequest("Segundo", 1, BigDecimal.ONE),
                new OrderLineItemRequest("Tercero", 1, BigDecimal.ONE)));

        Order order = orderService.createOrder(multiItemRequest);

        assertThat(order.items()).extracting("productName")
                .containsExactly("Primero", "Segundo", "Tercero");
    }

    @Test
    void rechazaPedidoMultiProductoCuandoElUsuarioNoExisteIgualQueAntes() {
        when(userClientService.getUserById(1L)).thenThrow(new UserNotFoundException(1L));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(UserNotFoundException.class);
    }

    private Order crearPedidoActivoYDevolverOrden() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(true).build());
        return orderService.createOrder(request);
    }

    @Test
    void getOrderByIdDevuelveTodasLasLineasYElTotal() {
        Order created = crearPedidoActivoYDevolverOrden();

        Order found = orderService.getOrderById(created.id());

        assertThat(found.items()).isEqualTo(created.items());
        assertThat(found.totalPrice()).isEqualByComparingTo(created.totalPrice());
    }

    @Test
    void getAllOrdersNoMezclaLineasEntrePedidos() {
        when(userClientService.getUserById(1L)).thenReturn(
                User.builder().id(1L).name("Ana Torres").email("ana@example.com").active(true).build());
        Order first = orderService.createOrder(request);
        OrderRequest secondRequest = new OrderRequest(1L, List.of(
                new OrderLineItemRequest("Monitor", 1, BigDecimal.valueOf(199.90))));
        Order second = orderService.createOrder(secondRequest);

        List<Order> all = orderService.getAllOrders();

        assertThat(all).hasSize(2);
        Order foundFirst = all.stream().filter(o -> o.id().equals(first.id())).findFirst().orElseThrow();
        Order foundSecond = all.stream().filter(o -> o.id().equals(second.id())).findFirst().orElseThrow();
        assertThat(foundFirst.items()).isEqualTo(first.items());
        assertThat(foundSecond.items()).isEqualTo(second.items());
    }
}
