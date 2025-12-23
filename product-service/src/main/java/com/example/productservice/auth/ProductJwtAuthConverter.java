package com.example.productservice.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.*;
import java.util.*;
import java.util.stream.Collectors;

public class ProductJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String principal = extractUserId(jwt);
        if (principal == null) principal = jwt.getSubject();
        return new JwtAuthenticationToken(jwt, authorities, principal);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> roles = new ArrayList<>();
        Object roleObj = jwt.getClaims().get("role");
        Object rolesObj = jwt.getClaims().get("roles");

        if (rolesObj instanceof Collection) {
            ((Collection<?>) rolesObj).forEach(o -> roles.add(String.valueOf(o)));
        }
        if (roleObj != null) {
            roles.add(String.valueOf(roleObj));
        }

        return roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private String extractUserId(Jwt jwt) {
        Object uid = jwt.getClaims().get("uid");
        return uid != null ? String.valueOf(uid) : null;
    }
}

