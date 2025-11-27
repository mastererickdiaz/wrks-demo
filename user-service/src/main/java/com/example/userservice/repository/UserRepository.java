package com.example.userservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.userservice.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByActiveTrue();
    Optional<User> findByIdAndActiveTrue(Long id);
    boolean existsByIdAndActiveTrue(Long id);
}