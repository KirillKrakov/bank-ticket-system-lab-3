package com.example.userservice.config;

import com.example.userservice.model.entity.User;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Configuration
public class DataInitializer {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Создаём администраторов если их нет
            UUID kirillId = UUID.randomUUID();
            UUID daniilId = UUID.randomUUID();

            userRepository.findById(kirillId)
                    .switchIfEmpty(
                            Mono.defer(() -> {
                                User kirill = new User();
                                kirill.setId(kirillId);
                                kirill.setUsername("kirill_krakov");
                                kirill.setEmail("kirill.krakov@example.com");
                                kirill.setPasswordHash(passwordEncoder.encode("kirill1234"));
                                kirill.setRole(UserRole.ROLE_ADMIN);
                                kirill.setCreatedAt(Instant.now());
                                return userRepository.save(kirill);
                            })
                    )
                    .flatMap(k -> userRepository.findById(daniilId))
                    .switchIfEmpty(
                            Mono.defer(() -> {
                                User daniil = new User();
                                daniil.setId(daniilId);
                                daniil.setUsername("daniil_babenko");
                                daniil.setEmail("daniil.babenko@example.com");
                                daniil.setPasswordHash(passwordEncoder.encode("daniil1234"));
                                daniil.setRole(UserRole.ROLE_ADMIN);
                                daniil.setCreatedAt(Instant.now());
                                return userRepository.save(daniil);
                            })
                    )
                    .subscribe(
                            user -> System.out.println("Admin users initialized successfully"),
                            error -> System.err.println("Failed to initialize admin users: " + error.getMessage())
                    );
        };
    }
}