package com.example.assignmentservice.feign;

import com.example.assignmentservice.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ProductServiceClientFallbackFactory implements FallbackFactory<ProductServiceClient> {
    @Override
    public ProductServiceClient create(Throwable cause) {
        return new ProductServiceClient() {
            @Override
            public Boolean productExists(UUID productId) {
                throw new ServiceUnavailableException("Product service is unavailable now");
            }
        };
    }
}
