package com.example.tagservice.feign;

import com.example.tagservice.dto.ApplicationInfoDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(
        name = "application-service",
        fallbackFactory = ApplicationServiceClientFallbackFactory.class
)
public interface ApplicationServiceClient {

    @GetMapping("/api/v1/applications/by-tag")
    List<ApplicationInfoDto> getApplicationsByTag(@RequestParam("tag") String tagName);
}