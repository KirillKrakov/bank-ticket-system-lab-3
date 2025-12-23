package com.example.tagservice.feign;

import com.example.tagservice.dto.ApplicationInfoDto;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ApplicationServiceClientFallbackFactory implements FallbackFactory<ApplicationServiceClient> {
    @Override
    public ApplicationServiceClient create(Throwable cause) {
        return new ApplicationServiceClient() {
            @Override
            public List<ApplicationInfoDto> getApplicationsByTag(String tagName) {
                return null;
            }
        };
    }
}