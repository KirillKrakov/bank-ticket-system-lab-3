package com.example.assignmentservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClientFallbackFactory.class);
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public Boolean userExists(UUID userId) {
                logger.error(cause.getMessage());
                if (cause.getMessage().contains("User not found") || cause.getMessage().contains("NotFound")) {
                    return false;
                }
                else {
                    return null;
                }
            }
        };
    }
}