package com.example.productservice.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;
import java.util.stream.Collectors;

public final class AuthUtils {

    private AuthUtils(){}

    public static Optional<UUID> currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(auth.getPrincipal().toString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public static List<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
    }

    public static Optional<String> currentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getCredentials() == null) return Optional.empty();
        return Optional.of(auth.getCredentials().toString());
    }

    public static boolean hasRole(String role) {
        return currentRoles().stream().anyMatch(r -> r.equals(role));
    }
}
