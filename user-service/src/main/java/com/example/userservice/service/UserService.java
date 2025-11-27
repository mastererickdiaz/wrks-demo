package com.example.userservice.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        log.info("Buscando todos los usuarios activos");
        return userRepository.findByActiveTrue();
    }

    public Optional<User> findById(Long id) {
        log.info("Buscando usuario activo por ID: {}", id);
        return userRepository.findByIdAndActiveTrue(id);
    }

    public User save(User user) {
        log.info("Guardando o actualizando usuario con email: {}", user.getEmail());
        return userRepository.save(user);
    }

    public void deleteById(Long id) {
        log.info("Iniciando desactivación para el usuario con ID: {}", id);
        userRepository.findById(id).ifPresent(user -> {
            log.info("Usuario encontrado, procediendo a desactivar ID: {}", id);
            user.setActive(false);
            userRepository.save(user);
            log.info("Usuario con ID: {} ha sido desactivado", id);
        });
    }

    public boolean userExists(Long id) {
        log.info("Verificando existencia de usuario activo con ID: {}", id);
        return userRepository.existsByIdAndActiveTrue(id);
    }
}
