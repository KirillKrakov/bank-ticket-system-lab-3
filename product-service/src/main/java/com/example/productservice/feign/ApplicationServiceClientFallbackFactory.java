package com.example.productservice.feign;

import com.example.productservice.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ApplicationServiceClientFallbackFactory implements FallbackFactory<ApplicationServiceClient> {
    @Override
    public ApplicationServiceClient create(Throwable cause) {
        return new ApplicationServiceClient() {
            @Override
            public Void deleteApplicationsByProductId(UUID productId) {
                throw new ServiceUnavailableException("Application service is unavailable now");
            }
        };
    }
}