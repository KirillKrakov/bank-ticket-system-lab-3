package com.example.userservice.auth;

import com.example.userservice.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

import java.util.Collections;

public class RepoReactiveUserDetailsService implements ReactiveUserDetailsService {

    private final UserRepository userRepository;

    public RepoReactiveUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Bean
    public ReactiveUserDetailsService reactiveUserDetailsService(UserRepository userRepository) {
        return new RepoReactiveUserDetailsService(userRepository);
    }

    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(u -> {
                    var authorities = Collections.singletonList(new SimpleGrantedAuthority(u.getRole().name()));
                    return org.springframework.security.core.userdetails.User
                            .withUsername(u.getUsername())
                            .password(u.getPasswordHash())
                            .authorities(authorities)
                            .accountExpired(false)
                            .accountLocked(false)
                            .credentialsExpired(false)
                            .disabled(false)
                            .build();
                });
    }
}
