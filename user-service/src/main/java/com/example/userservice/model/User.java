package com.example.userservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    private String phone;

    @Builder.Default
    private Boolean active = true;

    // Constructor sin argumentos manual (no generado por @NoArgsConstructor):
    // Lombok, cuando coexisten @NoArgsConstructor y @Builder.Default, NO aplica
    // el valor por defecto en el constructor sin argumentos (solo en el
    // builder), así que Jackson dejaría `active` en null al deserializar un
    // POST que no incluya ese campo.
    public User() {
        this.active = true;
    }

    // @Builder se coloca en este constructor (no en la clase) y se deja
    // package-private a propósito: con el flag -parameters del compilador,
    // Jackson (Spring Boot 4 / Jackson 3) detecta cualquier constructor PÚBLICO
    // que cubra todas las propiedades como "creator implícito" y lo usaría en
    // vez del constructor sin argumentos + setters, saltándose el default.
    // Al no ser público, Jackson lo ignora y usa el constructor sin argumentos.
    @Builder
    User(Long id, String name, String email, String phone, Boolean active) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.active = active;
    }
}