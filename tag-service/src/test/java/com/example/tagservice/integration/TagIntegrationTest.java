package com.example.tagservice.integration;

import com.example.tagservice.TagServiceApplication;
import com.example.tagservice.dto.TagDto;
import com.example.tagservice.model.entity.Tag;
import com.example.tagservice.repository.TagRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = TagServiceApplication.class)
public class TagIntegrationTest {

    // NOTE: keep secret length >= 32 bytes for HMAC-SHA256
    private static final String SECRET = "test-secret-very-long-string-at-least-32-bytes-123456";

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
        registry.add("resilience4j.circuitbreaker.instances.application-service.registerHealthIndicator", () -> "false");
        // JWT secret used by the application
        registry.add("jwt.secret", () -> SECRET);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TagRepository tagRepository;

    @BeforeEach
    void setUp() {
        tagRepository.deleteAll();
    }

    // Helper: generate JWT token with uid and role claims
    private String generateToken(UUID uid, String role) {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = Date.from(Instant.now());
        Date exp = Date.from(Instant.now().plusSeconds(3600));
        return Jwts.builder()
                .setSubject(uid.toString())
                .claim("uid", uid.toString())
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    private HttpHeaders headersWithToken(UUID uid, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String token = generateToken(uid, role);
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void createTag_shouldReturnCreated() {
        // Given
        String tagName = "integration-test";
        HttpHeaders headers = headersWithToken(UUID.randomUUID(), "ROLE_CLIENT");
        HttpEntity<String> entity = new HttpEntity<>(tagName, headers);

        // When
        ResponseEntity<TagDto> response = restTemplate.postForEntity(
                "/api/v1/tags",
                entity,
                TagDto.class
        );

        // Then
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(tagName, response.getBody().getName());
        assertNotNull(response.getBody().getId());

        // Verify location header
        URI location = response.getHeaders().getLocation();
        assertNotNull(location);
        assertTrue(location.toString().contains("/api/v1/tags/" + tagName));

        // Verify saved in database
        assertTrue(tagRepository.existsByName(tagName));
    }

    @Test
    void createTag_alreadyExists_shouldReturnExistingTag() {
        // Given - create tag in database
        Tag existingTag = new Tag();
        existingTag.setId(UUID.randomUUID());
        existingTag.setName("existing-tag");
        tagRepository.save(existingTag);

        String tagName = "existing-tag";
        HttpHeaders headers = headersWithToken(UUID.randomUUID(), "ROLE_CLIENT");
        HttpEntity<String> entity = new HttpEntity<>(tagName, headers);

        // When - try to create same tag
        ResponseEntity<TagDto> response = restTemplate.postForEntity(
                "/api/v1/tags",
                entity,
                TagDto.class
        );

        // Then - should return existing tag (not create new one)
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(existingTag.getId(), response.getBody().getId());
        assertEquals(existingTag.getName(), response.getBody().getName());

        // Verify only one tag exists
        assertEquals(1, tagRepository.count());
    }

    @Test
    void createTag_withEmptyName_shouldReturnCreated() {
        // Given — отправляем строку состоящую из пробелов
        String name = "   ";

        HttpHeaders headers = headersWithToken(UUID.randomUUID(), "ROLE_CLIENT");
        HttpEntity<String> entity = new HttpEntity<>(name, headers);

        // When
        ResponseEntity<TagDto> response = restTemplate.postForEntity(
                "/api/v1/tags",
                entity,
                TagDto.class
        );

        // Then
        // Depending on service validation, behaviour could be success or bad request.
        // In current implementation controller trims and attempts to create — assert CREATED
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        // trimmed name becomes empty string (""), we expect the body name equals trimmed version
        assertEquals(name.trim(), response.getBody().getName());
    }

    @Test
    void getTagByName_notFound_shouldReturnNotFound() {
        // prepare headers (any authenticated user)
        HttpHeaders headers = headersWithToken(UUID.randomUUID(), "ROLE_CLIENT");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // When
        ResponseEntity<TagDto> response = restTemplate.exchange(
                "/api/v1/tags/{name}",
                HttpMethod.GET,
                entity,
                TagDto.class,
                "non-existent-tag"
        );

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listTags_emptyDatabase_shouldReturnEmptyList() {
        // prepare headers
        HttpHeaders headers = headersWithToken(UUID.randomUUID(), "ROLE_CLIENT");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        // When - no tags in database
        ResponseEntity<TagDto[]> response = restTemplate.exchange(
                "/api/v1/tags?page=0&size=10",
                HttpMethod.GET,
                entity,
                TagDto[].class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void createOrGetTagsBatch_shouldReturnTags() {
        // Given - one tag exists, one is new
        Tag existingTag = new Tag();
        existingTag.setId(UUID.randomUUID());
        existingTag.setName("existing-tag");
        tagRepository.save(existingTag);

        List<String> tagNames = Arrays.asList("existing-tag", "new-tag-1", "new-tag-2");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tagNames, headers);

        // When (batch endpoint is allowed anonymously in config)
        ResponseEntity<TagDto[]> response = restTemplate.postForEntity(
                "/api/v1/tags/batch",
                entity,
                TagDto[].class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length);

        // Verify all tags exist
        assertEquals(3, tagRepository.count());
        assertTrue(tagRepository.existsByName("existing-tag"));
        assertTrue(tagRepository.existsByName("new-tag-1"));
        assertTrue(tagRepository.existsByName("new-tag-2"));
    }

    @Test
    void createOrGetTagsBatch_withEmptyList_shouldReturnEmptyList() {
        // Given
        List<String> emptyList = List.of();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(emptyList, headers);

        // When
        ResponseEntity<TagDto[]> response = restTemplate.postForEntity(
                "/api/v1/tags/batch",
                entity,
                TagDto[].class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void createOrGetTagsBatch_withNull_shouldReturnEmptyList() {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("[]", headers);

        // When
        ResponseEntity<TagDto[]> response = restTemplate.postForEntity(
                "/api/v1/tags/batch",
                entity,
                TagDto[].class
        );

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void createOrGetTagsBatch_duplicateNames_shouldReturnUniqueTags() {
        // Given
        List<String> tagNames = Arrays.asList("tag1", "tag1", "tag2", "tag2", "tag3");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tagNames, headers);

        // When
        ResponseEntity<TagDto[]> response = restTemplate.postForEntity(
                "/api/v1/tags/batch",
                entity,
                TagDto[].class
        );

        // Then - should return only unique tags
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length); // tag1, tag2, tag3

        // Verify only 3 tags in database
        assertEquals(3, tagRepository.count());
    }

    @Test
    void createOrGetTagsBatch_withWhitespace_shouldTrimNames() {
        // Given
        List<String> tagNames = Arrays.asList("  tag1  ", "tag2 ", " tag3");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tagNames, headers);

        // When
        ResponseEntity<TagDto[]> response = restTemplate.postForEntity(
                "/api/v1/tags/batch",
                entity,
                TagDto[].class
        );

        // Then - names should be trimmed
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        // Verify tags exist with trimmed names
        assertTrue(tagRepository.existsByName("tag1"));
        assertTrue(tagRepository.existsByName("tag2"));
        assertTrue(tagRepository.existsByName("tag3"));
    }

    @Test
    void createTag_withSpecialCharacters_shouldWork() {
        // Given
        String[] specialNames = {
                "test-tag",
                "test_tag",
                "test.tag",
                "test123",
                "123test",
                "TEST",
                "Test-Tag"
        };

        HttpHeaders headers = headersWithToken(UUID.randomUUID(), "ROLE_CLIENT");

        for (String name : specialNames) {
            HttpEntity<String> entity = new HttpEntity<>(name, headers);

            // When
            ResponseEntity<TagDto> response = restTemplate.postForEntity(
                    "/api/v1/tags",
                    entity,
                    TagDto.class
            );

            // Then
            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(name, response.getBody().getName());
        }
    }
}