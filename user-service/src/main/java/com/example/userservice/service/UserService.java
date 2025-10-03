package com.example.userservice.service;

import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<User> findAll() {
        return userRepository.findByActiveTrue();
    }

    public Optional<User> findById(Long id) {
        return userRepository.findByIdAndActiveTrue(id);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public void deleteById(Long id) {
        userRepository.findById(id).ifPresent(user -> {
            user.setActive(false);
            userRepository.save(user);
        });
    }
}