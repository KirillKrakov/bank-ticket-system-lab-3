package com.example.productservice.feign;

import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssignmentServiceClientFallbackFactory implements FallbackFactory<AssignmentServiceClient> {
    @Override
    public AssignmentServiceClient create(Throwable cause) {
        return new AssignmentServiceClient() {
            @Override
            public Boolean existsByUserAndProductAndRole(UUID userId, UUID productId, String role) {
                return null;
            }
        };
    }
}