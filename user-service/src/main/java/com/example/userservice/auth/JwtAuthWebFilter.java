package com.example.userservice.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

public class JwtAuthWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthWebFilter.class);
    private final JwtService jwtService;

    public JwtAuthWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.validateToken(token)) {
                    String uid = jwtService.extractUserId(token);
                    String role = jwtService.extractRole(token);
                    if (uid != null && role != null) {
                        GrantedAuthority authority = new SimpleGrantedAuthority(role);
                        Authentication auth = new UsernamePasswordAuthenticationToken(uid, null, List.of(authority));

                        SecurityContextImpl context = new SecurityContextImpl(auth);
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                    }
                }
            } catch (Exception ex) {
                log.debug("JWT validation failed: {}", ex.getMessage());
            }
        }
        return chain.filter(exchange);
    }
}