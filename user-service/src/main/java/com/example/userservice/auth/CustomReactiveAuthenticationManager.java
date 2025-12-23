package com.example.userservice.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import reactor.core.publisher.Mono;

public class CustomReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public CustomReactiveAuthenticationManager(ReactiveUserDetailsService uds, PasswordEncoder passwordEncoder) {
        this.userDetailsService = uds;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String username = authentication.getName();
        String presentedPassword = authentication.getCredentials().toString();
        return userDetailsService.findByUsername(username)
                .flatMap(userDetails -> {
                    if (passwordEncoder.matches(presentedPassword, userDetails.getPassword())) {
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                userDetails.getUsername(),
                                null,
                                userDetails.getAuthorities()
                        );
                        return Mono.just(auth);
                    } else {
                        return Mono.error(new org.springframework.security.core.AuthenticationException("Bad credentials") {});
                    }
                });
    }
}