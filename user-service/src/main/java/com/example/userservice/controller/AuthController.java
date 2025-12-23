package com.example.userservice.controller;

import com.example.userservice.auth.AuthRequest;
import com.example.userservice.auth.AuthResponse;
import com.example.userservice.auth.JwtService;
import com.example.userservice.exception.UnauthorizedException;
import com.example.userservice.repository.UserRepository;
import com.password4j.Password;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Auth", description = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> login(@RequestBody AuthRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return Mono.error(new UnauthorizedException("Username and password are required"));
        }

        return userRepository.findByUsername(request.getUsername())
                .switchIfEmpty(Mono.error(new UnauthorizedException("Invalid credentials")))
                .flatMap(user -> {
                    boolean ok = Password.check(request.getPassword(), user.getPasswordHash()).withBcrypt();
                    if (!ok) {
                        return Mono.error(new UnauthorizedException("Invalid credentials"));
                    }
                    String token = jwtService.generateToken(user);
                    return Mono.just(new AuthResponse(token));
                });
    }
}