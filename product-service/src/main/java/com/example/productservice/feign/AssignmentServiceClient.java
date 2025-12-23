package com.example.productservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "assignment-service",
        fallbackFactory = AssignmentServiceClientFallbackFactory.class
)
public interface AssignmentServiceClient {

    @GetMapping("/api/v1/assignments/exists")
    Boolean existsByUserAndProductAndRole(
            @RequestParam("userId") UUID userId,
            @RequestParam("productId") UUID productId,
            @RequestParam("role") String role
    );
}