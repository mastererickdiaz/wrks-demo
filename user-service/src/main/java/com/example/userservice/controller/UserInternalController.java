package com.example.userservice.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.userservice.service.UserService;

@RestController
@RequestMapping("/api/internal/users")
public class UserInternalController {

  private static final Logger log = LoggerFactory.getLogger(UserInternalController.class);

  private final UserService userService;

  public UserInternalController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/{id}/exists")
  @PreAuthorize("hasAuthority('SERVICE')")
  public ResponseEntity<Boolean> userExistsInternal(@PathVariable Long id) {
    log.info("Verificando internamente si existe el usuario con ID: {}", id);
    boolean exists = userService.userExists(id);
    log.info("La verificación interna para el usuario ID: {} resultó en: {}", id, exists);
    return ResponseEntity.ok(exists);
  }
}
