package com.example.productservice.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.*;
import com.example.productservice.auth.AuthUtils;

@Configuration
public class FeignAuthRequestInterceptor {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                AuthUtils.currentToken().ifPresent(token -> {
                    template.header("Authorization", "Bearer " + token);
                });
            }
        };
    }
}