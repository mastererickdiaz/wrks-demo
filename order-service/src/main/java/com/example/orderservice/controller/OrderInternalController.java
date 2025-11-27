package com.example.orderservice.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.orderservice.model.Order;
import com.example.orderservice.service.OrderService;

@RestController
@RequestMapping("/api/internal/orders")
public class OrderInternalController {

  private static final Logger log = LoggerFactory.getLogger(OrderInternalController.class);

  private final OrderService orderService;

  public OrderInternalController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping("/user/{userId}")
  @PreAuthorize("hasAuthority('SERVICE')")
  public ResponseEntity<List<Order>> getUserOrdersInternal(@PathVariable Long userId) {
    log.info("Solicitud interna para obtener las órdenes del usuario con ID: {}", userId);
    List<Order> orders = orderService.getUserOrders(userId);
    log.info("Encontradas {} órdenes para el usuario con ID: {}", orders.size(), userId);
    return ResponseEntity.ok(orders);
  }
}
