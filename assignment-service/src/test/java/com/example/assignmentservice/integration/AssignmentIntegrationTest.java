package com.example.assignmentservice.integration;

import com.example.assignmentservice.AssignmentServiceApplication;
import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.dto.UserProductAssignmentRequest;
import com.example.assignmentservice.feign.ProductServiceClient;
import com.example.assignmentservice.feign.UserServiceClient;
import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.repository.UserProductAssignmentRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = AssignmentServiceApplication.class)
public class AssignmentIntegrationTest {

    // Keep secret length >= 32 bytes for HMAC-SHA256
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
        registry.add("feign.client.config.default.connectTimeout", () -> "5000");
        registry.add("feign.client.config.default.readTimeout", () -> "5000");
        // jwt secret for the application under test
        registry.add("jwt.secret", () -> SECRET);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserProductAssignmentRepository assignmentRepository;

    @MockitoBean
    private UserServiceClient userServiceClient;

    @MockitoBean
    private ProductServiceClient productServiceClient;

    private final UUID adminUserId = UUID.randomUUID();
    private final UUID regularUserId = UUID.randomUUID();
    private final UUID productOwnerUserId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID anotherProductId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        assignmentRepository.deleteAll();
        reset(userServiceClient);
        reset(productServiceClient);
        setupMocks();
    }

    private void setupMocks() {
        // Only userExists and productExists are required now

        when(userServiceClient.userExists(adminUserId)).thenReturn(true);
        when(userServiceClient.userExists(regularUserId)).thenReturn(true);
        when(userServiceClient.userExists(productOwnerUserId)).thenReturn(true);

        when(productServiceClient.productExists(productId)).thenReturn(true);
        when(productServiceClient.productExists(anotherProductId)).thenReturn(true);

        // Generic fallback: return true for known ids, false otherwise
        when(userServiceClient.userExists(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(adminUserId) || id.equals(regularUserId) || id.equals(productOwnerUserId);
        });

        when(productServiceClient.productExists(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(productId) || id.equals(anotherProductId);
        });
    }

    // helper — generate JWT token with uid and role claims
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
    void createAssignment_asAdmin_shouldReturnCreated() {
        // prepare header for admin
        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");

        // Create request
        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals(regularUserId, response.getBody().getUserId());
        assertEquals(productId, response.getBody().getProductId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, response.getBody().getRole());
        assertNotNull(response.getBody().getAssignedAt());

        assertNotNull(response.getHeaders().getLocation());
        assertTrue(response.getHeaders().getLocation().toString().contains("/api/v1/assignments/"));
    }

    @Test
    void createAssignment_asProductOwner_shouldReturnCreated() {
        // Insert existing PRODUCT_OWNER record for productOwnerUserId
        UserProductAssignment existing = new UserProductAssignment();
        existing.setId(UUID.randomUUID());
        existing.setUserId(productOwnerUserId);
        existing.setProductId(productId);
        existing.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        existing.setAssignedAt(Instant.now());
        assignmentRepository.save(existing);

        // product owner token (role in token can be ROLE_CLIENT; service checks repo for ownership)
        HttpHeaders headers = headersWithToken(productOwnerUserId, "ROLE_CLIENT");

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.VIEWER);

        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(regularUserId, response.getBody().getUserId());
        assertEquals(productId, response.getBody().getProductId());
        assertEquals(AssignmentRole.VIEWER, response.getBody().getRole());
    }

    @Test
    void createAssignment_withoutRights_shouldReturnForbidden() {
        HttpHeaders headers = headersWithToken(regularUserId, "ROLE_CLIENT");

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createAssignment_userNotFound_shouldReturnNotFound() {
        UUID nonExistingUserId = UUID.randomUUID();
        when(userServiceClient.userExists(nonExistingUserId)).thenReturn(false);

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(nonExistingUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createAssignment_productNotFound_shouldReturnNotFound() {
        UUID nonExistingProductId = UUID.randomUUID();
        when(productServiceClient.productExists(nonExistingProductId)).thenReturn(false);

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(nonExistingProductId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listAssignments_all_shouldReturnAll() {
        createTestAssignments();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.GET,
                entity,
                UserProductAssignmentDto[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length);
    }

    @Test
    void listAssignments_byUserId_shouldReturnFiltered() {
        createTestAssignments();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.exchange(
                "/api/v1/assignments?userId={userId}",
                HttpMethod.GET,
                entity,
                UserProductAssignmentDto[].class,
                regularUserId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);
        for (UserProductAssignmentDto dto : response.getBody()) {
            assertEquals(regularUserId, dto.getUserId());
        }
    }

    @Test
    void listAssignments_byProductId_shouldReturnFiltered() {
        createTestAssignments();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.exchange(
                "/api/v1/assignments?productId={productId}",
                HttpMethod.GET,
                entity,
                UserProductAssignmentDto[].class,
                productId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);
        for (UserProductAssignmentDto dto : response.getBody()) {
            assertEquals(productId, dto.getProductId());
        }
    }

    @Test
    void listAssignments_empty_shouldReturnEmptyList() {
        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.GET,
                entity,
                UserProductAssignmentDto[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void existsEndpoint_withValidRole_shouldReturnTrue() {
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(regularUserId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(AssignmentRole.SUPPORT);
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment);

        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/v1/assignments/exists?userId={userId}&productId={productId}&role={role}",
                Boolean.class,
                regularUserId, productId, "SUPPORT"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody());
    }

    @Test
    void existsEndpoint_withInvalidRole_shouldReturnBadRequest() {
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/v1/assignments/exists?userId={userId}&productId={productId}&role={role}",
                Boolean.class,
                regularUserId, productId, "INVALID_ROLE"
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void existsEndpoint_nonExisting_shouldReturnFalse() {
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/v1/assignments/exists?userId={userId}&productId={productId}&role={role}",
                Boolean.class,
                UUID.randomUUID(), UUID.randomUUID(), "RESELLER"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody());
    }

    @Test
    void deleteAssignments_asAdmin_deleteByUserAndProduct_shouldReturnNoContent() {
        createAssignmentAsOwner();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?userId={userId}&productId={productId}",
                HttpMethod.DELETE,
                entity,
                Void.class,
                productOwnerUserId, productId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(assignmentRepository.existsByUserIdAndProductId(productOwnerUserId, productId));
    }

    @Test
    void deleteAssignments_asAdmin_deleteAllUserAssignments_shouldReturnNoContent() {
        createTestAssignments();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?userId={userId}",
                HttpMethod.DELETE,
                entity,
                Void.class,
                regularUserId
        );

        List<UserProductAssignment> userAssignmentsAfter = assignmentRepository.findByUserId(regularUserId);
        long afterCount = userAssignmentsAfter.stream().count();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, afterCount);
    }

    @Test
    void deleteAssignments_asAdmin_deleteAllProductAssignments_shouldReturnNoContent() {
        createTestAssignments();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?productId={productId}",
                HttpMethod.DELETE,
                entity,
                Void.class,
                productId
        );

        List<UserProductAssignment> productAssignmentsAfter = assignmentRepository.findByProductId(productId);
        long afterCount = productAssignmentsAfter.stream().count();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, afterCount);
    }

    @Test
    void deleteAssignments_asAdmin_deleteAll_shouldReturnNoContent() {
        createTestAssignments();

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        long beforeCount = assignmentRepository.count();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.DELETE,
                entity,
                Void.class
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, assignmentRepository.count());
        assertTrue(beforeCount > 0);
    }

    @Test
    void deleteAssignments_withoutAdminRights_shouldReturnForbidden() {
        HttpHeaders headers = headersWithToken(regularUserId, "ROLE_CLIENT");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.DELETE,
                entity,
                Void.class
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void deleteAssignments_withoutActorId_shouldReturnUnauthorized() {
        // No Authorization header
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void createAssignment_updateExisting_shouldUpdateRole() {
        // Create initial assignment
        UserProductAssignment existing = new UserProductAssignment();
        existing.setId(UUID.randomUUID());
        existing.setUserId(regularUserId);
        existing.setProductId(productId);
        existing.setRoleOnProduct(AssignmentRole.VIEWER);
        existing.setAssignedAt(Instant.now());
        assignmentRepository.save(existing);

        HttpHeaders headers = headersWithToken(adminUserId, "ROLE_ADMIN");

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.RESELLER);

        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(AssignmentRole.RESELLER, response.getBody().getRole());

        var updated = assignmentRepository.findByUserIdAndProductId(regularUserId, productId);
        assertTrue(updated.isPresent());
        assertEquals(AssignmentRole.RESELLER, updated.get().getRoleOnProduct());
    }

    // helper methods
    private void createAssignmentAsOwner() {
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(productOwnerUserId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment);
    }

    private void createTestAssignments() {
        UserProductAssignment assignment1 = new UserProductAssignment();
        assignment1.setId(UUID.randomUUID());
        assignment1.setUserId(regularUserId);
        assignment1.setProductId(productId);
        assignment1.setRoleOnProduct(AssignmentRole.SUPPORT);
        assignment1.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment1);

        UserProductAssignment assignment2 = new UserProductAssignment();
        assignment2.setId(UUID.randomUUID());
        assignment2.setUserId(regularUserId);
        assignment2.setProductId(anotherProductId);
        assignment2.setRoleOnProduct(AssignmentRole.VIEWER);
        assignment2.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment2);

        UserProductAssignment assignment3 = new UserProductAssignment();
        assignment3.setId(UUID.randomUUID());
        assignment3.setUserId(productOwnerUserId);
        assignment3.setProductId(productId);
        assignment3.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment3.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment3);
    }
}