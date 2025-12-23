package com.example.assignmentservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "product-service",
        fallbackFactory = ProductServiceClientFallbackFactory.class
)
public interface ProductServiceClient {

    @GetMapping("/api/v1/products/{productId}/exists")
    Boolean productExists(@PathVariable("productId") UUID productId);
}