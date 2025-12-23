package com.example.applicationservice.feign;

import com.example.applicationservice.exception.ServiceUnavailableException;
import com.example.applicationservice.model.enums.UserRole;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public Boolean userExists(UUID id) {
                throw new ServiceUnavailableException("User service is unavailable now");
            }

            @Override
            public UserRole getUserRole(UUID id) {
                throw new ServiceUnavailableException("User service is unavailable now");
            }
        };
    }
}