package com.example.productservice.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            try {
                if (jwtService.validateToken(token)) {
                    String uidStr = jwtService.extractUserId(token);
                    String role = jwtService.extractRole(token);

                    if (uidStr != null) {
                        String principal = uidStr;
                        try {
                            UUID.fromString(uidStr);
                        } catch (IllegalArgumentException e) {
                        }

                        List<GrantedAuthority> authorities = (role == null || role.isEmpty())
                                ? List.of()
                                : List.of(new SimpleGrantedAuthority(role));

                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, token, authorities);

                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } else {
                    log.debug("JWT is not valid for request {}", request.getRequestURI());
                }
            } catch (Exception ex) {
                log.debug("JWT processing failed: {}", ex.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }
}