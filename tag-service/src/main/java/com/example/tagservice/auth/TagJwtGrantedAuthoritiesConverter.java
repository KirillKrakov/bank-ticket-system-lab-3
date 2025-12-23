package com.example.tagservice.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;
import java.util.stream.Collectors;

public class TagJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();

        Object roleObj = jwt.getClaims().get("role");
        Object rolesObj = jwt.getClaims().get("roles");

        if (rolesObj instanceof Collection) {
            ((Collection<?>) rolesObj).forEach(o -> { if (o != null) roles.add(String.valueOf(o)); });
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
}

