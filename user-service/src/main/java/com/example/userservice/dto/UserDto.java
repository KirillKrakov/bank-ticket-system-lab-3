package com.example.userservice.dto;

import com.example.userservice.model.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public class UserDto {
    private UUID id;

    @Size(min = 1, max = 100)
    private String username;

    @Email
    @Size(min = 1, max = 255)
    private String email;

    private UserRole role;
    private Instant createdAt;

    // Конструктор по умолчанию
    public UserDto() {}

    // Геттеры и сеттеры
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}