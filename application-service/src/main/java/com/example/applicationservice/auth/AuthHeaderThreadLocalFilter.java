package com.example.applicationservice.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

@Component
public class AuthHeaderThreadLocalFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth != null && !auth.isBlank()) {
            AuthHeaderHolder.set(auth);
            return chain.filter(exchange)
                    .doFinally(sig -> AuthHeaderHolder.clear());
        } else {
            return chain.filter(exchange);
        }
    }
}

