package com.example.productservice.integration;

import com.example.productservice.ProductServiceApplication;
import com.example.productservice.dto.ProductDto;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.model.entity.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = ProductServiceApplication.class)
public class ProductIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.liquibase.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("resilience4j.circuitbreaker.instances.user-service.registerHealthIndicator", () -> "false");
        registry.add("resilience4j.circuitbreaker.instances.application-service.registerHealthIndicator", () -> "false");
        registry.add("resilience4j.circuitbreaker.instances.assignment-service.registerHealthIndicator", () -> "false");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    void createProduct_shouldReturnCreated() {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setDescription("Test Description");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ProductDto> response = restTemplate.postForEntity(
                "/api/v1/products",
                entity,
                ProductDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals("Test Product", response.getBody().getName());
        assertEquals("Test Description", response.getBody().getDescription());

        URI location = response.getHeaders().getLocation();
        assertNotNull(location);
        assertTrue(location.toString().contains("/api/v1/products/"));

        assertTrue(productRepository.existsByName("Test Product"));
    }

    @Test
    void createProduct_withDuplicateName_shouldReturnConflict() {
        Product existing = new Product();
        existing.setId(UUID.randomUUID());
        existing.setName("Existing Product");
        existing.setDescription("Existing Description");
        productRepository.save(existing);

        ProductRequest request = new ProductRequest();
        request.setName("Existing Product");
        request.setDescription("New Description");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ProductDto> response = restTemplate.postForEntity(
                "/api/v1/products",
                entity,
                ProductDto.class
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void getProduct_shouldReturnProduct() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Test Product");
        product.setDescription("Test Description");
        productRepository.save(product);

        ResponseEntity<ProductDto> response = restTemplate.getForEntity(
                "/api/v1/products/" + product.getId(),
                ProductDto.class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(product.getId(), response.getBody().getId());
        assertEquals("Test Product", response.getBody().getName());
    }

    @Test
    void getProduct_notFound_shouldReturnNotFound() {
        ResponseEntity<ProductDto> response = restTemplate.getForEntity(
                "/api/v1/products/" + UUID.randomUUID(),
                ProductDto.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listProducts_withPagination_shouldReturnPage() {
        for (int i = 0; i < 5; i++) {
            Product product = new Product();
            product.setId(UUID.randomUUID());
            product.setName("Product " + i);
            product.setDescription("Description " + i);
            productRepository.save(product);
        }

        ResponseEntity<ProductDto[]> response = restTemplate.getForEntity(
                "/api/v1/products?page=0&size=3",
                ProductDto[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length);
    }

    @Test
    void listProducts_pageSizeTooLarge_shouldReturnBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/products?page=0&size=100",
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        String body = response.getBody();
        assertNotNull(body);
        assertTrue(body.toLowerCase().contains("size") || body.toLowerCase().contains("bad"),
                "Expected error description about size in response body, actual: " + body);
    }

    @Test
    void updateProduct_withoutActorId_shouldReturnUnauthorized() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Product");
        product.setDescription("Description");
        productRepository.save(product);

        ProductRequest updateRequest = new ProductRequest();
        updateRequest.setName("Updated Product");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(updateRequest, headers);

        ResponseEntity<ProductDto> response = restTemplate.exchange(
                "/api/v1/products/{id}",
                HttpMethod.PUT,
                entity,
                ProductDto.class,
                product.getId()
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void deleteProduct_withoutActorId_shouldReturnUnauthorized() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Product");
        product.setDescription("Description");
        productRepository.save(product);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/products/{id}",
                HttpMethod.DELETE,
                null,
                Void.class,
                product.getId()
        );
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void productExists_endpoint_shouldReturnBoolean() {
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("Existing Product");
        product.setDescription("Description");
        productRepository.save(product);

        UUID nonExistingId = UUID.randomUUID();

        ResponseEntity<Boolean> response1 = restTemplate.getForEntity(
                "/api/v1/products/{id}/exists",
                Boolean.class,
                product.getId()
        );

        ResponseEntity<Boolean> response2 = restTemplate.getForEntity(
                "/api/v1/products/{id}/exists",
                Boolean.class,
                nonExistingId
        );

        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertTrue(response1.getBody());

        assertEquals(HttpStatus.OK, response2.getStatusCode());
        assertFalse(response2.getBody());
    }

    @Test
    void createProduct_withEmptyName_shouldReturnBadRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("");
        request.setDescription("Description");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ProductDto> response = restTemplate.postForEntity(
                "/api/v1/products",
                entity,
                ProductDto.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void createProduct_withTooLongName_shouldReturnBadRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("A".repeat(201));
        request.setDescription("Description");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ProductRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ProductDto> response = restTemplate.postForEntity(
                "/api/v1/products",
                entity,
                ProductDto.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}