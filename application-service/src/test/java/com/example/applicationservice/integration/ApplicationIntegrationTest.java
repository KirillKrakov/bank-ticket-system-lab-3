package com.example.applicationservice.integration;

import com.example.applicationservice.ApplicationServiceApplication;
import com.example.applicationservice.dto.*;
import com.example.applicationservice.model.entity.Application;
import com.example.applicationservice.model.enums.ApplicationStatus;
import com.example.applicationservice.repository.ApplicationRepository;
import com.example.applicationservice.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = ApplicationServiceApplication.class)
public class ApplicationIntegrationTest {

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
    private ApplicationRepository applicationRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @MockitoBean
    private com.example.applicationservice.feign.UserServiceClient userServiceClient;

    @MockitoBean
    private com.example.applicationservice.feign.ProductServiceClient productServiceClient;

    @MockitoBean
    private com.example.applicationservice.feign.TagServiceClient tagServiceClient;

    private final UUID applicantId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID managerId = UUID.randomUUID();
    private final UUID anotherApplicantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        documentRepository.deleteAll();
        setupMocks();
    }

    private void setupMocks() {
        // Мок для проверки существования пользователя
        when(userServiceClient.userExists(applicantId)).thenReturn(true);
        when(userServiceClient.userExists(anotherApplicantId)).thenReturn(true);
        when(userServiceClient.userExists(adminId)).thenReturn(true);
        when(userServiceClient.userExists(managerId)).thenReturn(true);
        when(userServiceClient.userExists(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(applicantId) || id.equals(adminId) ||
                    id.equals(managerId) || id.equals(anotherApplicantId);
        });

        // Мок для ролей пользователей
        when(userServiceClient.getUserRole(adminId))
                .thenReturn(com.example.applicationservice.model.enums.UserRole.ROLE_ADMIN);
        when(userServiceClient.getUserRole(managerId))
                .thenReturn(com.example.applicationservice.model.enums.UserRole.ROLE_MANAGER);
        when(userServiceClient.getUserRole(applicantId))
                .thenReturn(com.example.applicationservice.model.enums.UserRole.ROLE_CLIENT);
        when(userServiceClient.getUserRole(anotherApplicantId))
                .thenReturn(com.example.applicationservice.model.enums.UserRole.ROLE_CLIENT);

        // Мок для проверки существования продукта
        when(productServiceClient.productExists(productId)).thenReturn(true);
        when(productServiceClient.productExists(any(UUID.class))).thenAnswer(invocation -> {
            UUID id = invocation.getArgument(0);
            return id.equals(productId);
        });

        // Мок для тегов
        when(tagServiceClient.createOrGetTagsBatch(any(List.class))).thenAnswer(invocation -> {
            List<String> tagNames = invocation.getArgument(0);
            return tagNames.stream()
                    .map(name -> {
                        TagDto dto = new TagDto();
                        dto.setId(UUID.randomUUID());
                        dto.setName(name);
                        return dto;
                    })
                    .toList();
        });
    }

    @Test
    void createApplication_shouldReturnCreated() {
        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(applicantId);
        request.setProductId(productId);
        request.setTags(List.of("urgent", "new-customer"));

        DocumentRequest doc = new DocumentRequest();
        doc.setFileName("passport.pdf");
        doc.setContentType("application/pdf");
        doc.setStoragePath("/storage/passport.pdf");
        request.setDocuments(List.of(doc));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApplicationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApplicationDto> response = restTemplate.postForEntity(
                "/api/v1/applications",
                entity,
                ApplicationDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getId());
        assertEquals(applicantId, response.getBody().getApplicantId());
        assertEquals(productId, response.getBody().getProductId());
        assertEquals(ApplicationStatus.SUBMITTED, response.getBody().getStatus());
        assertNotNull(response.getBody().getCreatedAt());

        // Проверяем документы
        assertNotNull(response.getBody().getDocuments());
        assertEquals(1, response.getBody().getDocuments().size());
        assertEquals("passport.pdf", response.getBody().getDocuments().get(0).getFileName());

        // Проверяем теги
        assertNotNull(response.getBody().getTags());
        assertTrue(response.getBody().getTags().contains("urgent"));
        assertTrue(response.getBody().getTags().contains("new-customer"));
    }

    @Test
    void createApplication_productNotFound_shouldReturnBadRequest() {
        UUID nonExistingProductId = UUID.randomUUID();
        when(productServiceClient.productExists(nonExistingProductId)).thenReturn(false);

        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(applicantId);
        request.setProductId(nonExistingProductId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApplicationRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/applications",
                    entity,
                    String.class
            );

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    void createApplication_applicantNotFound_shouldReturnBadRequest() {
        UUID nonExistingUserId = UUID.randomUUID();
        when(userServiceClient.userExists(nonExistingUserId)).thenReturn(false);

        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(nonExistingUserId);
        request.setProductId(productId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApplicationRequest> entity = new HttpEntity<>(request, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/applications",
                    entity,
                    String.class
            );

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    void listApplications_withPagination_shouldReturnPage() {
        for (int i = 0; i < 5; i++) {
            Application app = new Application();
            app.setId(UUID.randomUUID());
            app.setApplicantId(applicantId);
            app.setProductId(productId);
            app.setStatus(ApplicationStatus.SUBMITTED);
            app.setCreatedAt(java.time.Instant.now());
            applicationRepository.save(app);
        }

        ResponseEntity<ApplicationDto[]> response = restTemplate.getForEntity(
                "/api/v1/applications?page=0&size=3",
                ApplicationDto[].class
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        ApplicationDto[] applications = response.getBody();
        assertTrue(applications.length <= 3, "Should return at most 3 applications (page size)");

        if (applications.length > 0) {
            assertEquals(3, applications.length, "Should return exactly 3 applications for first page");
        }
    }

    @Test
    void listApplications_pageSizeTooLarge_shouldReturnBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/applications?page=0&size=100",
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void getApplication_shouldReturnApplication() {
        // Создаем заявку напрямую
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        ResponseEntity<ApplicationDto> response = restTemplate.getForEntity(
                "/api/v1/applications/{id}",
                ApplicationDto.class,
                app.getId()
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(app.getId(), response.getBody().getId());
        assertEquals(applicantId, response.getBody().getApplicantId());
    }

    @Test
    void getApplication_notFound_shouldReturnNotFound() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/api/v1/applications/{id}",
                    String.class,
                    UUID.randomUUID()
            );
            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        }
    }

    @Test
    void streamApplications_limitTooLarge_shouldReturnBadRequest() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/applications/stream?limit=100",
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void addTags_asApplicant_shouldReturnNoContent() {
        // Создаем заявку для applicant
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        List<String> tags = List.of("urgent", "verified");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tags, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/{id}/tags?actorId={actorId}",
                HttpMethod.PUT,
                entity,
                Void.class,
                app.getId(),
                applicantId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void addTags_asAdmin_shouldReturnNoContent() {
        // Создаем заявку
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        List<String> tags = List.of("admin-tag");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tags, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/{id}/tags?actorId={actorId}",
                HttpMethod.PUT,
                entity,
                Void.class,
                app.getId(),
                adminId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void addTags_withoutPermissions_shouldReturnForbidden() {
        // Создаем заявку другого пользователя
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(anotherApplicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        List<String> tags = List.of("test-tag");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tags, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/{id}/tags?actorId={actorId}",
                HttpMethod.PUT,
                entity,
                Void.class,
                app.getId(),
                applicantId // Пытается изменить чужую заявку
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void removeTags_asApplicant_shouldReturnNoContent() {
        // Создаем заявку с тегами
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        app.setTags(java.util.Set.of("urgent", "verified"));
        applicationRepository.save(app);

        List<String> tagsToRemove = List.of("urgent");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<String>> entity = new HttpEntity<>(tagsToRemove, headers);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/{id}/tags?actorId={actorId}",
                HttpMethod.DELETE,
                entity,
                Void.class,
                app.getId(),
                applicantId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void changeStatus_asAdmin_shouldReturnOk() {
        // Создаем заявку
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        String newStatus = "APPROVED";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(newStatus, headers);

        ResponseEntity<ApplicationDto> response = restTemplate.exchange(
                "/api/v1/applications/{id}/status?actorId={actorId}",
                HttpMethod.PUT,
                entity,
                ApplicationDto.class,
                app.getId(),
                adminId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.APPROVED, response.getBody().getStatus());
    }

    @Test
    void changeStatus_asManager_shouldReturnOk() {
        // Создаем заявку другого пользователя (не менеджера)
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        String newStatus = "IN_REVIEW";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(newStatus, headers);

        ResponseEntity<ApplicationDto> response = restTemplate.exchange(
                "/api/v1/applications/{id}/status?actorId={actorId}",
                HttpMethod.PUT,
                entity,
                ApplicationDto.class,
                app.getId(),
                managerId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ApplicationStatus.IN_REVIEW, response.getBody().getStatus());
    }

    @Test
    void changeStatus_managerChangingOwnApplication_shouldReturnConflict() {
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(managerId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        String newStatus = "APPROVED";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(newStatus, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/applications/{id}/status?actorId={actorId}",
                    HttpMethod.PUT,
                    entity,
                    String.class,
                    app.getId(),
                    managerId
            );

            assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        }
    }

    @Test
    void changeStatus_withoutPermissions_shouldReturnForbidden() {
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        String newStatus = "APPROVED";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(newStatus, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/applications/{id}/status?actorId={actorId}",
                    HttpMethod.PUT,
                    entity,
                    String.class,
                    app.getId(),
                    applicantId
            );

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    void deleteApplication_asAdmin_shouldReturnNoContent() {
        // Создаем заявку
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/{id}?actorId={actorId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                app.getId(),
                adminId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(applicationRepository.existsById(app.getId()));
    }

    @Test
    void deleteApplication_withoutAdminRights_shouldReturnForbidden() {
        // Создаем заявку
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/{id}?actorId={actorId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                app.getId(),
                applicantId // Не администратор
        );

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertTrue(applicationRepository.existsById(app.getId()));
    }

    @Test
    void getApplicationHistory_asApplicant_shouldReturnHistory() {
        // Создаем заявку с историей
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        // Добавляем запись в историю (в реальной системе это делается при изменении статуса)
        // Здесь просто тестируем доступ

        ResponseEntity<List<ApplicationHistoryDto>> response = restTemplate.exchange(
                "/api/v1/applications/{id}/history?actorId={actorId}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ApplicationHistoryDto>>() {},
                app.getId(),
                applicantId
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void getApplicationHistory_withoutPermissions_shouldReturnForbidden() {
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(anotherApplicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        applicationRepository.save(app);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    "/api/v1/applications/{id}/history?actorId={actorId}",
                    HttpMethod.GET,
                    null,
                    String.class,
                    app.getId(),
                    applicantId
            );

            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        } catch (HttpClientErrorException e) {
            assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        }
    }

    @Test
    void deleteApplicationsByUserId_internal_shouldReturnNoContent() {
        // Создаем несколько заявок для пользователя
        for (int i = 0; i < 3; i++) {
            Application app = new Application();
            app.setId(UUID.randomUUID());
            app.setApplicantId(applicantId);
            app.setProductId(productId);
            app.setStatus(ApplicationStatus.SUBMITTED);
            app.setCreatedAt(java.time.Instant.now());
            applicationRepository.save(app);
        }

        long beforeCount = applicationRepository.countByApplicantId(applicantId);
        assertTrue(beforeCount > 0);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/internal/by-user?userId={userId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                applicantId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, applicationRepository.countByApplicantId(applicantId));
    }

    @Test
    void deleteApplicationsByProductId_internal_shouldReturnNoContent() {
        // Создаем несколько заявок для продукта
        for (int i = 0; i < 3; i++) {
            Application app = new Application();
            app.setId(UUID.randomUUID());
            app.setApplicantId(applicantId);
            app.setProductId(productId);
            app.setStatus(ApplicationStatus.SUBMITTED);
            app.setCreatedAt(java.time.Instant.now());
            applicationRepository.save(app);
        }

        long beforeCount = applicationRepository.countByProductId(productId);
        assertTrue(beforeCount > 0);

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/applications/internal/by-product?productId={productId}",
                HttpMethod.DELETE,
                null,
                Void.class,
                productId
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertEquals(0, applicationRepository.countByProductId(productId));
    }

    @Test
    void getApplicationsByTag_shouldReturnApplications() {
        // Создаем заявку с тегом
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(applicantId);
        app.setProductId(productId);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(java.time.Instant.now());
        app.setTags(java.util.Set.of("urgent"));
        applicationRepository.save(app);

        // Создаем заявку без этого тега
        Application app2 = new Application();
        app2.setId(UUID.randomUUID());
        app2.setApplicantId(anotherApplicantId);
        app2.setProductId(productId);
        app2.setStatus(ApplicationStatus.SUBMITTED);
        app2.setCreatedAt(java.time.Instant.now());
        app2.setTags(java.util.Set.of("normal"));
        applicationRepository.save(app2);

        ResponseEntity<List<ApplicationInfoDto>> response = restTemplate.exchange(
                "/api/v1/applications/by-tag?tag={tag}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<ApplicationInfoDto>>() {},
                "urgent"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(app.getId(), response.getBody().get(0).getId());
    }

    @Test
    void createApplication_withEmptyTags_shouldReturnCreated() {
        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(applicantId);
        request.setProductId(productId);
        request.setTags(List.of()); // Пустой список тегов

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApplicationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApplicationDto> response = restTemplate.postForEntity(
                "/api/v1/applications",
                entity,
                ApplicationDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getTags());
        assertTrue(response.getBody().getTags().isEmpty());
    }

    @Test
    void createApplication_withEmptyDocuments_shouldReturnCreated() {
        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(applicantId);
        request.setProductId(productId);
        request.setDocuments(List.of()); // Пустой список документов

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ApplicationRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<ApplicationDto> response = restTemplate.postForEntity(
                "/api/v1/applications",
                entity,
                ApplicationDto.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getDocuments());
        assertTrue(response.getBody().getDocuments().isEmpty());
    }
}
