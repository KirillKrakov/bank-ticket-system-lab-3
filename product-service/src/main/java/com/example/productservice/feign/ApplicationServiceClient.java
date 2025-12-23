package com.example.productservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "application-service",
        fallbackFactory = ApplicationServiceClientFallbackFactory.class
)
public interface ApplicationServiceClient {

    @DeleteMapping("/api/v1/applications/internal/by-product")
    Void deleteApplicationsByProductId(@RequestParam("productId") UUID productId);
}