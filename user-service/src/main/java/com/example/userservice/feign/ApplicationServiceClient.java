package com.example.userservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "application-service",
        fallbackFactory = ApplicationServiceClientFallbackFactory.class
)
public interface ApplicationServiceClient {

    @DeleteMapping("/api/v1/applications/internal/by-user")
    Void deleteApplicationsByUserId(@RequestParam("userId") String userId);
}