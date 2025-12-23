package com.example.userservice.feign;

import com.example.userservice.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Component
public class ApplicationServiceClientFallbackFactory implements FallbackFactory<ApplicationServiceClient> {
    @Override
    public ApplicationServiceClient create(Throwable cause) {
        return new ApplicationServiceClient() {
            @Override
            public Void deleteApplicationsByUserId(String userId) {
                throw new ServiceUnavailableException("Application service is unavailable now");
            }
        };
    }
}