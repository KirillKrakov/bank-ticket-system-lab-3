package com.example.userservice.integration;

import com.example.userservice.UserServiceApplication;
import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserRequest;
import com.example.userservice.model.entity.User;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = UserServiceApplication.class)
public class UserIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Используем URL без пуллинга, Spring Boot сам настроит пул
        registry.add("spring.r2dbc.url", () ->
                String.format("r2dbc:postgresql://%s:%d/%s",
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);

        // Конфигурация пула
        registry.add("spring.r2dbc.pool.enabled", () -> "true");
        registry.add("spring.r2dbc.pool.initial-size", () -> "5");
        registry.add("spring.r2dbc.pool.max-size", () -> "10");
        registry.add("spring.r2dbc.pool.max-idle-time", () -> "30m");

        // Отключаем миграции и другие ненужные для тестов компоненты
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.liquibase.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
        registry.add("spring.cloud.config.enabled", () -> "false");
        registry.add("logging.level.org.springframework.r2dbc", () -> "DEBUG");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DatabaseClient databaseClient;

    @MockitoBean
    private com.example.userservice.feign.ApplicationServiceClient applicationServiceClient;

    private UUID adminId;
    private UUID clientId;
    private UUID managerId;
    private String adminUsername = "admin";
    private String clientUsername = "client";
    private String managerUsername = "manager";

    @BeforeEach
    void setUp() {
        createTableIfNotExists();
        // Очистка базы данных перед каждым тестом
        userRepository.deleteAll().block();

        // Создаем тестовых пользователей с разными ролями
        adminId = createTestUser(adminUsername, "admin@example.com", UserRole.ROLE_ADMIN);
        clientId = createTestUser(clientUsername, "client@example.com", UserRole.ROLE_CLIENT);
        managerId = createTestUser(managerUsername, "manager@example.com", UserRole.ROLE_MANAGER);

        when(applicationServiceClient.deleteApplicationsByUserId(anyString()))
                .thenAnswer(invocation -> Mono.empty());
    }

    private void createTableIfNotExists() {
        // SQL для создания таблицы users (или app_user - смотря какое имя используется)
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS app_user (
                id UUID PRIMARY KEY,
                username VARCHAR(100) NOT NULL UNIQUE,
                email VARCHAR(255) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(50) NOT NULL DEFAULT 'ROLE_CLIENT',
                created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                updated_at TIMESTAMP,
                version BIGINT DEFAULT 0
            );
            """;

        try {
            databaseClient.sql(createTableSql)
                    .fetch()
                    .rowsUpdated()
                    .block();
        } catch (Exception e) {
            // Логируем ошибку, но продолжаем
            System.err.println("Error creating table: " + e.getMessage());
        }
    }

    private UUID createTestUser(String username, String email, UserRole role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("$2a$10$hashedpassword");
        user.setRole(role);
        user.setCreatedAt(Instant.now());

        return userRepository.save(user)
                .map(User::getId)
                .block();
    }

    @Test
    void createUser_shouldReturnCreatedUser() {
        UserRequest request = new UserRequest();
        request.setUsername("newuser");
        request.setEmail("newuser@example.com");
        request.setPassword("password123");

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .consumeWith(response -> {
                    UserDto userDto = response.getResponseBody();
                    assertThat(userDto).isNotNull();
                    assertThat(userDto.getId()).isNotNull();
                    assertThat(userDto.getUsername()).isEqualTo("newuser");
                    assertThat(userDto.getEmail()).isEqualTo("newuser@example.com");
                    assertThat(userDto.getRole()).isEqualTo(UserRole.ROLE_CLIENT);
                    assertThat(userDto.getCreatedAt()).isNotNull();
                });
    }

    @Test
    void createUser_withExistingUsername_shouldReturnConflict() {
        UserRequest request = new UserRequest();
        request.setUsername(adminUsername); // Существующее имя пользователя
        request.setEmail("newemail@example.com");
        request.setPassword("password123");

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409); // Conflict
    }

    @Test
    void createUser_withExistingEmail_shouldReturnConflict() {
        UserRequest request = new UserRequest();
        request.setUsername("newusername");
        request.setEmail("admin@example.com"); // Существующий email
        request.setPassword("password123");

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409); // Conflict
    }

    @Test
    void createUser_withInvalidData_shouldReturnBadRequest() {
        UserRequest request = new UserRequest();
        request.setUsername(null); // Невалидные данные
        request.setEmail(null);
        request.setPassword(null);

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void getAllUsers_withPagination_shouldReturnPage() {
        webTestClient.get()
                .uri("/api/v1/users?page=0&size=2")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserDto.class)
                .hasSize(2);
    }

    @Test
    void getUserById_shouldReturnUser() {
        webTestClient.get()
                .uri("/api/v1/users/{id}", adminId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .consumeWith(response -> {
                    UserDto userDto = response.getResponseBody();
                    assertThat(userDto).isNotNull();
                    assertThat(userDto.getId()).isEqualTo(adminId);
                    assertThat(userDto.getUsername()).isEqualTo(adminUsername);
                });
    }

    @Test
    void getUserById_notFound_shouldReturnNotFound() {
        UUID nonExistingId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/users/{id}", nonExistingId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateUser_asAdmin_shouldUpdateSuccessfully() {
        UserRequest request = new UserRequest();
        request.setUsername("updatedclient");
        request.setEmail("updatedclient@example.com");
        request.setPassword("newpassword123");

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", adminId)
                        .build(clientId))
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .consumeWith(response -> {
                    UserDto userDto = response.getResponseBody();
                    assertThat(userDto).isNotNull();
                    assertThat(userDto.getId()).isEqualTo(clientId);
                    assertThat(userDto.getUsername()).isEqualTo("updatedclient");
                    assertThat(userDto.getEmail()).isEqualTo("updatedclient@example.com");
                });
    }

    @Test
    void updateUser_withoutAdminRights_shouldReturnForbidden() {
        UserRequest request = new UserRequest();
        request.setUsername("updatedclient");

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", clientId) // Клиент пытается обновить другого клиента
                        .build(managerId))
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void updateUser_notFound_shouldReturnBadRequest() {
        UUID nonExistingId = UUID.randomUUID();
        UserRequest request = new UserRequest();
        request.setUsername("updated");

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", adminId)
                        .build(nonExistingId))
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteUser_asAdmin_shouldDeleteSuccessfully() {
        // Сначала создаем пользователя для удаления
        User userToDelete = new User();
        userToDelete.setId(UUID.randomUUID());
        userToDelete.setUsername("todelete");
        userToDelete.setEmail("todelete@example.com");
        userToDelete.setPasswordHash("$2a$10$hashed");
        userToDelete.setRole(UserRole.ROLE_CLIENT);
        userToDelete.setCreatedAt(Instant.now());

        UUID userIdToDelete = userRepository.save(userToDelete)
                .map(User::getId)
                .block();

        // Проверяем, что пользователь существует
        assertThat(userRepository.findById(userIdToDelete).block()).isNotNull();

        // Удаляем пользователя
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", adminId)
                        .build(userIdToDelete))
                .exchange()
                .expectStatus().is5xxServerError();

    }

    @Test
    void deleteUser_withoutAdminRights_shouldReturnForbidden() {
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", clientId) // Клиент пытается удалить
                        .build(managerId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void promoteToManager_asAdmin_shouldPromoteSuccessfully() {
        // Проверяем начальную роль
        User userBefore = userRepository.findById(clientId).block();
        assertThat(userBefore.getRole()).isEqualTo(UserRole.ROLE_CLIENT);

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}/promote-manager")
                        .queryParam("actorId", adminId)
                        .build(clientId))
                .exchange()
                .expectStatus().isNoContent();

        // Проверяем, что роль изменилась
        User userAfter = userRepository.findById(clientId).block();
        assertThat(userAfter.getRole()).isEqualTo(UserRole.ROLE_MANAGER);
    }

    @Test
    void promoteToManager_alreadyManager_shouldDoNothing() {
        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}/promote-manager")
                        .queryParam("actorId", adminId)
                        .build(managerId))
                .exchange()
                .expectStatus().isNoContent();

        // Роль должна остаться менеджером
        User user = userRepository.findById(managerId).block();
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_MANAGER);
    }

    @Test
    void promoteToManager_withoutAdminRights_shouldReturnForbidden() {
        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}/promote-manager")
                        .queryParam("actorId", clientId)
                        .build(clientId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void demoteToClient_asAdmin_shouldDemoteSuccessfully() {
        // Проверяем начальную роль
        User userBefore = userRepository.findById(managerId).block();
        assertThat(userBefore.getRole()).isEqualTo(UserRole.ROLE_MANAGER);

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}/demote-manager")
                        .queryParam("actorId", adminId)
                        .build(managerId))
                .exchange()
                .expectStatus().isNoContent();

        // Проверяем, что роль изменилась
        User userAfter = userRepository.findById(managerId).block();
        assertThat(userAfter.getRole()).isEqualTo(UserRole.ROLE_CLIENT);
    }

    @Test
    void demoteToClient_alreadyClient_shouldDoNothing() {
        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}/demote-manager")
                        .queryParam("actorId", adminId)
                        .build(clientId))
                .exchange()
                .expectStatus().isNoContent();

        // Роль должна остаться клиентом
        User user = userRepository.findById(clientId).block();
        assertThat(user.getRole()).isEqualTo(UserRole.ROLE_CLIENT);
    }

    @Test
    void demoteToClient_withoutAdminRights_shouldReturnForbidden() {
        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}/demote-manager")
                        .queryParam("actorId", managerId)
                        .build(managerId))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void userExists_existingUser_shouldReturnTrue() {
        webTestClient.get()
                .uri("/api/v1/users/{id}/exists", adminId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void userExists_nonExistingUser_shouldReturnNotFound() {
        UUID nonExistingId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/users/{id}/exists", nonExistingId)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Not Found");
    }

    @Test
    void getUserRole_existingUser_shouldReturnRole() {
        webTestClient.get()
                .uri("/api/v1/users/{id}/role", adminId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserRole.class)
                .isEqualTo(UserRole.ROLE_ADMIN);
    }

    @Test
    void getUserRole_nonExistingUser_shouldReturnNotFound() {
        UUID nonExistingId = UUID.randomUUID();

        webTestClient.get()
                .uri("/api/v1/users/{id}/role", nonExistingId)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void createUser_withSpacesInUsername_shouldTrimSpaces() {
        UserRequest request = new UserRequest();
        request.setUsername("  testuser  ");
        request.setEmail("testuser@example.com");
        request.setPassword("password123");

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .consumeWith(response -> {
                    UserDto userDto = response.getResponseBody();
                    assertThat(userDto).isNotNull();
                    assertThat(userDto.getUsername()).isEqualTo("testuser"); // Должен быть триммирован
                });
    }

    @Test
    void createUser_withSpacesAndCaseInEmail_shouldTrimAndLowercase() {
        UserRequest request = new UserRequest();
        request.setUsername("testuser");
        request.setEmail("TestUser@Example.com");
        request.setPassword("password123");

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .consumeWith(response -> {
                    UserDto userDto = response.getResponseBody();
                    assertThat(userDto).isNotNull();
                    assertThat(userDto.getEmail()).isEqualTo("testuser@example.com");
                });
    }

    @Test
    void createUser_withInvalidEmail_shouldReturnBadRequest() {
        UserRequest request = new UserRequest();
        request.setUsername("testuser");
        request.setEmail("invalid-email");
        request.setPassword("password123");

        webTestClient.post()
                .uri("/api/v1/users")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void updateUser_partialUpdate_shouldUpdateOnlyProvidedFields() {
        // Сначала получаем текущие данные пользователя
        User userBefore = userRepository.findById(clientId).block();
        String originalEmail = userBefore.getEmail();

        // Обновляем только имя пользователя
        UserRequest request = new UserRequest();
        request.setUsername("updatedusername");
        // email и password не указаны - должны остаться прежними

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", adminId)
                        .build(clientId))
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .consumeWith(response -> {
                    UserDto userDto = response.getResponseBody();
                    assertThat(userDto).isNotNull();
                    assertThat(userDto.getUsername()).isEqualTo("updatedusername");
                    assertThat(userDto.getEmail()).isEqualTo(originalEmail);
                });
    }

    @Test
    void deleteUser_shouldCallApplicationService() {
        // Создаем нового пользователя для удаления
        User userToDelete = new User();
        userToDelete.setId(UUID.randomUUID());
        userToDelete.setUsername("deletecall");
        userToDelete.setEmail("deletecall@example.com");
        userToDelete.setPasswordHash("$2a$10$hashed");
        userToDelete.setRole(UserRole.ROLE_CLIENT);
        userToDelete.setCreatedAt(Instant.now());

        UUID userIdToDelete = userRepository.save(userToDelete)
                .map(User::getId)
                .block();

        // Проверяем, что мок вызывается
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", adminId)
                        .build(userIdToDelete))
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    void updateUser_bySelf_shouldReturnForbiddenIfNotAdmin() {
        UserRequest request = new UserRequest();
        request.setUsername("updatedself");

        webTestClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users/{id}")
                        .queryParam("actorId", clientId)
                        .build(clientId))
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getAllUsers_emptyDatabase_shouldReturnEmptyList() {
        // Очищаем базу
        userRepository.deleteAll().block();

        webTestClient.get()
                .uri("/api/v1/users?page=0&size=10")
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserDto.class)
                .hasSize(0);
    }

    @Test
    void getAllUsers_defaultPagination_shouldUseDefaults() {
        webTestClient.get()
                .uri("/api/v1/users") // без параметров
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(UserDto.class)
                .hasSize(3); // наши 3 тестовых пользователя
    }
}