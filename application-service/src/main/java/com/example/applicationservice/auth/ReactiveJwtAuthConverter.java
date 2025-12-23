package com.example.applicationservice.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.*;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

public class ReactiveJwtAuthConverter implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object r = jwt.getClaims().get("role");
        Object rs = jwt.getClaims().get("roles");
        if (rs instanceof Collection) {
            ((Collection<?>) rs).forEach(o -> { if (o != null) roles.add(String.valueOf(o)); });
        }
        if (r != null) {
            roles.add(String.valueOf(r));
        }

        Collection<GrantedAuthority> authorities = roles.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        String principal = jwt.getClaimAsString("uid");
        if (principal == null) principal = jwt.getSubject();

        AbstractAuthenticationToken token = new JwtAuthenticationToken(jwt, authorities, principal);
        return Mono.just(token);
    }
}
