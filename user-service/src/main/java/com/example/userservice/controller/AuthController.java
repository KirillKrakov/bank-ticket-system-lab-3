package com.example.userservice.controller;

import com.example.userservice.auth.AuthRequest;
import com.example.userservice.auth.AuthResponse;
import com.example.userservice.auth.JwtService;
import com.example.userservice.repository.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Tag(name = "Auth", description = "Authentication")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ReactiveAuthenticationManager authenticationManager;

    public AuthController(UserRepository userRepository, JwtService jwtService, ReactiveAuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> login(@RequestBody AuthRequest request) {
        if (request == null || request.getUsername() == null || request.getPassword() == null) {
            return Mono.error(new com.example.userservice.exception.UnauthorizedException("Username and password are required"));
        }

        UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword());

        return authenticationManager.authenticate(authToken)
                .flatMap(auth -> userRepository.findByUsername(auth.getName())
                        .map(user -> {
                            String token = jwtService.generateToken(user);
                            return new AuthResponse(token);
                        })
                );
    }
}