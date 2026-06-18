package com.example.orderservice.client;

import com.example.orderservice.config.FeignConfig;
import com.example.orderservice.model.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "user-service",
    configuration = FeignConfig.class
)
public interface UserServiceClient {

    // /raw incluye usuarios inactivos: permite distinguir "no existe" (404) de
    // "existe pero inactivo" (200, active=false). Ver UserController#getUserByIdIncludingInactive.
    @GetMapping("/api/users/{id}/raw")
    User getUserById(@PathVariable("id") Long id);
}