package com.example.orderservice.service;

import com.example.orderservice.dto.NotificationEvent;
import com.example.orderservice.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.http.HttpMethod;

class OrderNotificationServiceTest {

    private static final String WEBHOOK_URL = "http://test-webhook/order-status";

    private NotificationEvent buildEvent() {
        return new NotificationEvent(1L, "ana@example.com",
                Order.OrderStatus.PENDING, Order.OrderStatus.CONFIRMED, LocalDateTime.now());
    }

    @Test
    void enviaElEventoAlWebhookCuandoEstaHabilitadoYResponde2xx() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrderNotificationService service = new OrderNotificationService(builder, true, WEBHOOK_URL);

        server.expect(requestTo(WEBHOOK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess());

        service.send(buildEvent());

        server.verify();
    }

    @Test
    void noLanzaExcepcionCuandoElWebhookFalla() {
        // Sin contexto de Spring (instanciación directa), @CircuitBreaker/@Retry
        // no se aplican (limitación de AOP con `new`); por eso se prueba el método
        // de fallback directamente, que es lo que Resilience4j invoca en producción
        // cuando send() falla.
        RestClient.Builder builder = RestClient.builder();
        OrderNotificationService service = new OrderNotificationService(builder, true, WEBHOOK_URL);

        service.sendFallback(buildEvent(), new RuntimeException("timeout"));
        // Si no lanza excepción, la prueba pasa: FR-006 se cumple en el camino de fallback.
    }

    @Test
    void simulaConLogCuandoElWebhookEstaDeshabilitado() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrderNotificationService service = new OrderNotificationService(builder, false, WEBHOOK_URL);

        service.send(buildEvent());

        // No debe haberse intentado ninguna llamada HTTP real.
        server.verify();
    }

    @Test
    void elWebhookConErrorDelServidorPropagaExcepcionSinAop() {
        // Documenta el comportamiento sin AOP: la propia llamada HTTP lanza si
        // el servidor responde error y no hay un proxy de Resilience4j que la
        // intercepte. En producción, @CircuitBreaker + fallbackMethod evitan que
        // esto llegue al llamador (OrderService).
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OrderNotificationService service = new OrderNotificationService(builder, true, WEBHOOK_URL);

        server.expect(requestTo(WEBHOOK_URL)).andRespond(withServerError());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () -> service.send(buildEvent()));
    }
}
