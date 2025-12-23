package com.example.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerAggregationConfig {

    @Bean
    public RouteLocator swaggerRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                // Swagger маршруты для каждого сервиса
                .route("user-service-swagger", r -> r
                        .path("/user-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/user-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://USER-SERVICE"))

                .route("application-service-swagger", r -> r
                        .path("/application-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/application-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://APPLICATION-SERVICE"))

                .route("product-service-swagger", r -> r
                        .path("/product-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/product-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://PRODUCT-SERVICE"))

                .route("assignment-service-swagger", r -> r
                        .path("/assignment-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/assignment-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://ASSIGNMENT-SERVICE"))

                .route("tag-service-swagger", r -> r
                        .path("/tag-service/v3/api-docs")
                        .filters(f -> f.rewritePath("/tag-service/v3/api-docs", "/v3/api-docs"))
                        .uri("lb://TAG-SERVICE"))

                .build();
    }
}