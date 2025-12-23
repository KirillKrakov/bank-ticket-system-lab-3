package com.example.productservice.feign;

import com.example.productservice.model.enums.UserRole;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public Boolean userExists(UUID userId) {
                return null;
            }

            @Override
            public UserRole getUserRole(UUID userId) {
                return null;
            }
        };
    }
}
