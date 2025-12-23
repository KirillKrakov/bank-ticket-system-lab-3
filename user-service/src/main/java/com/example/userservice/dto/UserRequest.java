package com.example.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class UserRequest {
    @Size(min = 1, max = 100)
    private String username;

    @Email
    @Size(min = 1, max = 255)
    private String email;

    @Size(min = 8, max = 100)
    private String password;

    // Геттеры и сеттеры
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}