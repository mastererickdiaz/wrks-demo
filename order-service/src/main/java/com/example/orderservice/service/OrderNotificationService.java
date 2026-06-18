package com.example.orderservice.service;

import com.example.orderservice.dto.NotificationEvent;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class OrderNotificationService {

    private final RestClient restClient;
    private final boolean webhookEnabled;
    private final String webhookUrl;

    public OrderNotificationService(
            RestClient.Builder restClientBuilder,
            @Value("${notifications.webhook.enabled:false}") boolean webhookEnabled,
            @Value("${notifications.webhook.url:}") String webhookUrl) {
        this.restClient = restClientBuilder.build();
        this.webhookEnabled = webhookEnabled;
        this.webhookUrl = webhookUrl;
    }

    // Único método público y anotado: debe llamarse siempre desde OTRO bean
    // (OrderService), nunca desde dentro de esta misma clase, porque el AOP
    // de Spring (y por lo tanto @CircuitBreaker/@Retry) no intercepta llamadas
    // internas (this.metodo()), solo llamadas externas a través del proxy.
    @CircuitBreaker(name = "notificationWebhook", fallbackMethod = "sendFallback")
    @Retry(name = "notificationWebhook")
    public void send(NotificationEvent event) {
        if (!webhookEnabled) {
            log.info("Notificación simulada (webhook deshabilitado): pedido {} {} -> {} para {}",
                    event.orderId(), event.fromStatus(), event.toStatus(), event.recipientEmail());
            return;
        }

        restClient.post()
                .uri(webhookUrl)
                .body(event)
                .retrieve()
                .toBodilessEntity();

        log.info("Notificación enviada: pedido {} {} -> {} a {}",
                event.orderId(), event.fromStatus(), event.toStatus(), event.recipientEmail());
    }

    // FR-006: un fallo al notificar nunca debe propagarse; solo se registra.
    public void sendFallback(NotificationEvent event, Exception e) {
        log.error("Fallo al enviar notificación del pedido {}: {}", event.orderId(), e.getMessage());
    }
}
