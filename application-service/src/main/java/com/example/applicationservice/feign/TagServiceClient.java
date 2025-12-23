package com.example.applicationservice.feign;

import com.example.applicationservice.dto.TagDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        name = "tag-service",
        fallbackFactory = TagServiceClientFallbackFactory.class
)
public interface TagServiceClient {

    @PostMapping(
            value = "/api/v1/tags/batch",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    List<TagDto> createOrGetTagsBatch(@RequestBody List<String> tagNames);
}