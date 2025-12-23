package com.example.applicationservice.feign;

import com.example.applicationservice.dto.TagDto;
import com.example.applicationservice.exception.ServiceUnavailableException;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class TagServiceClientFallbackFactory implements FallbackFactory<TagServiceClient> {
    @Override
    public TagServiceClient create(Throwable cause) {
        return new TagServiceClient() {
            @Override
            public List<TagDto> createOrGetTagsBatch(List<String> tagNames) {
                throw new ServiceUnavailableException("Tag service is unavailable now");
            }
        };
    }
}