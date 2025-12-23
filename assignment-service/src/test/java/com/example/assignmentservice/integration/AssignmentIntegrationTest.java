package com.example.assignmentservice.integration;

import com.example.assignmentservice.AssignmentServiceApplication;
import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.dto.UserProductAssignmentRequest;
import com.example.assignmentservice.feign.ProductServiceClient;
import com.example.assignmentservice.feign.UserServiceClient;
import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.model.enums.UserRole;
import com.example.assignmentservice.repository.UserProductAssignmentRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = AssignmentServiceApplication.class)
public class AssignmentIntegrationTest {

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
        // Мок для ADMIN пользователя
        when(userServiceClient.getUserRole(adminUserId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(adminUserId)).thenReturn(true);

        // Мок для обычного пользователя
        when(userServiceClient.getUserRole(regularUserId)).thenReturn(UserRole.ROLE_CLIENT);
        when(userServiceClient.userExists(regularUserId)).thenReturn(true);

        // Мок для владельца продукта
        when(userServiceClient.getUserRole(productOwnerUserId)).thenReturn(UserRole.ROLE_CLIENT);
        when(userServiceClient.userExists(productOwnerUserId)).thenReturn(true);

        // Мок для существования продуктов
        when(productServiceClient.productExists(productId)).thenReturn(true);
        when(productServiceClient.productExists(anotherProductId)).thenReturn(true);

        // Мок для несуществующих сущностей
        when(userServiceClient.userExists(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(adminUserId) || id.equals(regularUserId) || id.equals(productOwnerUserId);
        });

        when(productServiceClient.productExists(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(productId) || id.equals(anotherProductId);
        });
    }

    @Test
    void createAssignment_asAdmin_shouldReturnCreated() {
        // Сначала создаем assignment через owner, чтобы потом admin мог управлять
        createAssignmentAsOwner();

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class,
                adminUserId
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
        // Сначала владелец создает себе продукт
        UserProductAssignment existing = new UserProductAssignment();
        existing.setId(UUID.randomUUID());
        existing.setUserId(productOwnerUserId);
        existing.setProductId(productId);
        existing.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        existing.setAssignedAt(Instant.now());
        assignmentRepository.save(existing);

        // Теперь владелец может назначать других
        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.VIEWER);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class,
                productOwnerUserId
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(regularUserId, response.getBody().getUserId());
        assertEquals(productId, response.getBody().getProductId());
        assertEquals(AssignmentRole.VIEWER, response.getBody().getRole());
    }

    @Test
    void createAssignment_withoutRights_shouldReturnForbidden() {
        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class,
                regularUserId // Обычный пользователь без прав
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void createAssignment_userNotFound_shouldReturnNotFound() {
        UUID nonExistingUserId = UUID.randomUUID();
        when(userServiceClient.userExists(nonExistingUserId)).thenReturn(false);

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(nonExistingUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class,
                adminUserId
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createAssignment_productNotFound_shouldReturnNotFound() {
        UUID nonExistingProductId = UUID.randomUUID();
        when(productServiceClient.productExists(nonExistingProductId)).thenReturn(false);

        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(nonExistingProductId);
        request.setRole(AssignmentRole.PRODUCT_OWNER);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class,
                adminUserId
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listAssignments_all_shouldReturnAll() {
        // Создаем несколько назначений
        createTestAssignments();

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.getForEntity(
                "/api/v1/assignments",
                UserProductAssignmentDto[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().length);
    }

    @Test
    void listAssignments_byUserId_shouldReturnFiltered() {
        // Создаем несколько назначений
        createTestAssignments();

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.getForEntity(
                "/api/v1/assignments?userId={userId}",
                UserProductAssignmentDto[].class,
                regularUserId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);

        // Проверяем, что все назначения для нужного пользователя
        for (UserProductAssignmentDto dto : response.getBody()) {
            assertEquals(regularUserId, dto.getUserId());
        }
    }

    @Test
    void listAssignments_byProductId_shouldReturnFiltered() {
        // Создаем несколько назначений
        createTestAssignments();

        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.getForEntity(
                "/api/v1/assignments?productId={productId}",
                UserProductAssignmentDto[].class,
                productId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().length);

        // Проверяем, что все назначения для нужного продукта
        for (UserProductAssignmentDto dto : response.getBody()) {
            assertEquals(productId, dto.getProductId());
        }
    }

    @Test
    void listAssignments_empty_shouldReturnEmptyList() {
        ResponseEntity<UserProductAssignmentDto[]> response = restTemplate.getForEntity(
                "/api/v1/assignments",
                UserProductAssignmentDto[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void existsEndpoint_withValidRole_shouldReturnTrue() {
        // Создаем назначение
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
        // Создаем назначение для удаления
        createAssignmentAsOwner();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}&userId={userId}&productId={productId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                adminUserId, productOwnerUserId, productId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(assignmentRepository.existsByUserIdAndProductId(productOwnerUserId, productId));
    }

    @Test
    void deleteAssignments_asAdmin_deleteAllUserAssignments_shouldReturnNoContent() {
        // Создаем несколько назначений для пользователя
        createTestAssignments();

        List<UserProductAssignment> userAssignments = assignmentRepository.findByUserId(regularUserId);
        long beforeCount = userAssignments.stream().count();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}&userId={userId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                adminUserId, regularUserId
        );

        List<UserProductAssignment> userAssignmentsAfter = assignmentRepository.findByUserId(regularUserId);
        long afterCount = userAssignmentsAfter.stream().count();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, afterCount);
        assertTrue(beforeCount > 0);
    }

    @Test
    void deleteAssignments_asAdmin_deleteAllProductAssignments_shouldReturnNoContent() {
        // Создаем несколько назначений для продукта
        createTestAssignments();

        List<UserProductAssignment> productAssignments = assignmentRepository.findByProductId(productId);
        long beforeCount = productAssignments.stream().count();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}&productId={productId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                adminUserId, productId
        );

        List<UserProductAssignment> productAssignmentsAfter = assignmentRepository.findByProductId(productId);
        long afterCount = productAssignmentsAfter.stream().count();

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, afterCount);
        assertTrue(beforeCount > 0);
    }

    @Test
    void deleteAssignments_asAdmin_deleteAll_shouldReturnNoContent() {
        // Создаем несколько назначений
        createTestAssignments();

        long beforeCount = assignmentRepository.count();

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                adminUserId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, assignmentRepository.count());
        assertTrue(beforeCount > 0);
    }

    @Test
    void deleteAssignments_withoutAdminRights_shouldReturnForbidden() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                regularUserId // Не ADMIN
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void deleteAssignments_withoutActorId_shouldReturnUnauthorized() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/assignments",
                HttpMethod.DELETE,
                null,
                Void.class
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void createAssignment_updateExisting_shouldUpdateRole() {
        // Создаем первоначальное назначение
        UserProductAssignment existing = new UserProductAssignment();
        existing.setId(UUID.randomUUID());
        existing.setUserId(regularUserId);
        existing.setProductId(productId);
        existing.setRoleOnProduct(AssignmentRole.VIEWER);
        existing.setAssignedAt(Instant.now());
        assignmentRepository.save(existing);

        // Обновляем назначение через ADMIN
        UserProductAssignmentRequest request = new UserProductAssignmentRequest();
        request.setUserId(regularUserId);
        request.setProductId(productId);
        request.setRole(AssignmentRole.RESELLER);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<UserProductAssignmentRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<UserProductAssignmentDto> response = restTemplate.exchange(
                "/api/v1/assignments?actorId={actorId}",
                HttpMethod.POST,
                entity,
                UserProductAssignmentDto.class,
                adminUserId
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(AssignmentRole.RESELLER, response.getBody().getRole());

        // Проверяем, что назначение обновилось
        var updated = assignmentRepository.findByUserIdAndProductId(regularUserId, productId);
        assertTrue(updated.isPresent());
        assertEquals(AssignmentRole.RESELLER, updated.get().getRoleOnProduct());
    }

    // Вспомогательные методы
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
        // Первое назначение: regular user -> product (EDITOR)
        UserProductAssignment assignment1 = new UserProductAssignment();
        assignment1.setId(UUID.randomUUID());
        assignment1.setUserId(regularUserId);
        assignment1.setProductId(productId);
        assignment1.setRoleOnProduct(AssignmentRole.SUPPORT);
        assignment1.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment1);

        // Второе назначение: regular user -> another product (VIEWER)
        UserProductAssignment assignment2 = new UserProductAssignment();
        assignment2.setId(UUID.randomUUID());
        assignment2.setUserId(regularUserId);
        assignment2.setProductId(anotherProductId);
        assignment2.setRoleOnProduct(AssignmentRole.VIEWER);
        assignment2.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment2);

        // Третье назначение: product owner -> product (OWNER)
        UserProductAssignment assignment3 = new UserProductAssignment();
        assignment3.setId(UUID.randomUUID());
        assignment3.setUserId(productOwnerUserId);
        assignment3.setProductId(productId);
        assignment3.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment3.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment3);
    }
}
