package com.example.applicationservice.config;

import com.example.applicationservice.exception.ConflictException;
import com.example.applicationservice.exception.ForbiddenException;
import com.example.applicationservice.exception.NotFoundException;
import com.example.applicationservice.exception.ServiceUnavailableException;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomErrorDecoder implements ErrorDecoder {
    private static final Logger logger = LoggerFactory.getLogger(CustomErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        // Обычные HTTP ошибки
        return switch (response.status()) {
            case 503 -> new ServiceUnavailableException("Service unavailable");
            case 409 -> new ConflictException("Already in use");
            case 404 -> new NotFoundException("Resource not found");
            case 403 -> new ForbiddenException("Not enough rights");
            default -> FeignException.errorStatus(methodKey, response);
        };
    }
}