package com.example.applicationservice.auth;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;

@Component
public class FeignAuthRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String auth = AuthHeaderHolder.get();
        if (auth != null && !auth.isBlank()) {
            template.header("Authorization", auth);
        }
    }
}
