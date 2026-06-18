package com.example.orderservice.exception;

import com.example.orderservice.model.Order;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapeaUserNotFoundExceptionA404() {
        ResponseEntity<String> response = handler.handleUserNotFound(new UserNotFoundException(99L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("99");
    }

    @Test
    void mapeaUserInactiveExceptionA400() {
        ResponseEntity<String> response = handler.handleUserInactive(new UserInactiveException(7L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("7");
    }

    @Test
    void mapeaOrderNotFoundExceptionA404() {
        ResponseEntity<String> response = handler.handleOrderNotFound(new OrderNotFoundException(5L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("5");
    }

    @Test
    void mapeaInvalidOrderTransitionExceptionA400() {
        ResponseEntity<String> response = handler.handleInvalidOrderTransition(
                new InvalidOrderTransitionException(Order.OrderStatus.SHIPPED, Order.OrderStatus.PENDING));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("SHIPPED").contains("PENDING");
    }
}
