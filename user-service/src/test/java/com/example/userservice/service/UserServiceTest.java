package com.example.userservice.service;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserRequest;
import com.example.userservice.exception.*;
import com.example.userservice.feign.ApplicationServiceClient;
import com.example.userservice.model.entity.User;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private final UUID testUserId = UUID.randomUUID();
    private final UUID actorAdminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Mockito will inject mocks into userService because of @InjectMocks
    }

    // -----------------------
    // create tests
    // -----------------------
    @Test
    void create_Success_CreatesUser() {
        // Arrange
        UserRequest req = new UserRequest();
        req.setUsername("alice");
        req.setEmail("alice@example.com");
        req.setPassword("StrongPass123");

        User savedUser = new User();
        savedUser.setId(testUserId);
        savedUser.setUsername("alice");
        savedUser.setEmail("alice@example.com");
        savedUser.setPasswordHash("encodedPassword");
        savedUser.setRole(UserRole.ROLE_CLIENT);
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.existsByUsername("alice")).thenReturn(Mono.just(false));
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(Mono.just(false));
        when(passwordEncoder.encode("StrongPass123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(savedUser));

        // Act & Assert
        StepVerifier.create(userService.create(req))
                .expectNextMatches(dto -> {
                    assertEquals(testUserId, dto.getId());
                    assertEquals("alice", dto.getUsername());
                    assertEquals("alice@example.com", dto.getEmail());
                    assertEquals(UserRole.ROLE_CLIENT, dto.getRole());
                    return true;
                })
                .verifyComplete();

        verify(userRepository).existsByUsername("alice");
        verify(userRepository).existsByEmail("alice@example.com");
        verify(passwordEncoder).encode("StrongPass123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void create_DuplicateUsername_ThrowsConflict() {
        // Arrange
        UserRequest req = new UserRequest();
        req.setUsername("existingUser");
        req.setEmail("new@example.com");
        req.setPassword("pass123");

        when(userRepository.existsByUsername("existingUser")).thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(userService.create(req))
                .expectErrorMatches(throwable ->
                        throwable instanceof ConflictException &&
                                throwable.getMessage().contains("Username already in use"))
                .verify();

        verify(userRepository).existsByUsername("existingUser");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_DuplicateEmail_ThrowsConflict() {
        // Arrange
        UserRequest req = new UserRequest();
        req.setUsername("newUser");
        req.setEmail("existing@example.com");
        req.setPassword("pass123");

        when(userRepository.existsByUsername("newUser")).thenReturn(Mono.just(false));
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(Mono.just(true));

        // Act & Assert
        StepVerifier.create(userService.create(req))
                .expectErrorMatches(throwable ->
                        throwable instanceof ConflictException &&
                                throwable.getMessage().contains("Email already in use"))
                .verify();

        verify(userRepository).existsByUsername("newUser");
        verify(userRepository).existsByEmail("existing@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void create_NullRequest_ThrowsBadRequest() {
        StepVerifier.create(Mono.defer(() -> userService.create(null)))
                .expectErrorMatches(throwable ->
                        throwable instanceof BadRequestException &&
                                "Request is required".equals(throwable.getMessage())
                )
                .verify();
    }

    @Test
    void create_MissingRequiredFields_ThrowsBadRequest() {
        UserRequest req = new UserRequest();
        req.setUsername("user");
        req.setEmail(null); // Missing email
        req.setPassword("pass");

        StepVerifier.create(Mono.defer(() -> userService.create(req)))
                .expectErrorMatches(throwable ->
                        throwable instanceof BadRequestException &&
                                throwable.getMessage().contains("Username, email and password are required")
                )
                .verify();
    }

    // -----------------------
    // findAll tests
    // -----------------------
    @Test
    void findAll_Success_ReturnsPaginatedUsers() {
        // Arrange
        User user1 = new User();
        user1.setId(UUID.randomUUID());
        user1.setUsername("user1");
        user1.setEmail("user1@example.com");
        user1.setRole(UserRole.ROLE_CLIENT);

        User user2 = new User();
        user2.setId(UUID.randomUUID());
        user2.setUsername("user2");
        user2.setEmail("user2@example.com");
        user2.setRole(UserRole.ROLE_MANAGER);

        when(userRepository.findAll()).thenReturn(Flux.just(user1, user2));

        // Act & Assert
        StepVerifier.create(userService.findAll(0, 2))
                .expectNextCount(2)
                .verifyComplete();

        verify(userRepository).findAll();
    }

    @Test
    void findAll_SizeExceeds50_ThrowsBadRequest() {
        // Act & Assert
        StepVerifier.create(userService.findAll(0, 51))
                .expectErrorMatches(throwable ->
                        throwable instanceof BadRequestException &&
                                throwable.getMessage().contains("Page size cannot exceed 50"))
                .verify();

        verify(userRepository, never()).findAll();
    }

    // -----------------------
    // findById tests
    // -----------------------
    @Test
    void findById_UserExists_ReturnsUserDto() {
        // Arrange
        User user = new User();
        user.setId(testUserId);
        user.setUsername("testUser");
        user.setEmail("test@example.com");
        user.setRole(UserRole.ROLE_CLIENT);
        user.setCreatedAt(Instant.now());

        when(userRepository.findById(testUserId)).thenReturn(Mono.just(user));

        // Act & Assert
        StepVerifier.create(userService.findById(testUserId))
                .expectNextMatches(dto -> {
                    assertEquals(testUserId, dto.getId());
                    assertEquals("testUser", dto.getUsername());
                    assertEquals("test@example.com", dto.getEmail());
                    assertEquals(UserRole.ROLE_CLIENT, dto.getRole());
                    return true;
                })
                .verifyComplete();

        verify(userRepository).findById(testUserId);
    }

    @Test
    void findById_UserNotFound_ThrowsNotFound() {
        // Arrange
        when(userRepository.findById(testUserId)).thenReturn(Mono.empty());

        // Act & Assert
        StepVerifier.create(userService.findById(testUserId))
                .expectErrorMatches(throwable ->
                        throwable instanceof NotFoundException &&
                                throwable.getMessage().contains("User not found"))
                .verify();

        verify(userRepository).findById(testUserId);
    }

    // -----------------------
    // update tests (now service reads actor from ReactiveSecurityContext)
    // -----------------------
    @Test
    void update_Success_UpdatesUser() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User existingUser = new User();
        existingUser.setId(testUserId);
        existingUser.setUsername("old");
        existingUser.setEmail("old@example.com");
        existingUser.setPasswordHash("oldHash");
        existingUser.setRole(UserRole.ROLE_CLIENT);
        existingUser.setCreatedAt(Instant.now());

        User updatedUser = new User();
        updatedUser.setId(testUserId);
        updatedUser.setUsername("new");
        updatedUser.setEmail("new@example.com");
        updatedUser.setPasswordHash("newHash");
        updatedUser.setRole(UserRole.ROLE_CLIENT);
        updatedUser.setUpdatedAt(Instant.now());

        UserRequest req = new UserRequest();
        req.setUsername("new");
        req.setEmail("new@example.com");
        req.setPassword("newPass123");

        // mock repository and encoder
        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(existingUser));
        when(passwordEncoder.encode("newPass123")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(updatedUser));

        // mock security context to simulate authenticated admin
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.update(testUserId, req))
                    .expectNextMatches(dto -> {
                        assertEquals(testUserId, dto.getId());
                        assertEquals("new", dto.getUsername());
                        assertEquals("new@example.com", dto.getEmail());
                        return true;
                    })
                    .verifyComplete();

            verify(userRepository).findById(actorAdminId);
            verify(userRepository).findById(testUserId);
            verify(passwordEncoder).encode("newPass123");
            verify(userRepository).save(any(User.class));
        }
    }

    @Test
    void update_UserNotFound_ThrowsNotFound() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.empty());

        UserRequest req = new UserRequest();
        req.setUsername("new");

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.update(testUserId, req))
                    .expectErrorMatches(throwable ->
                            throwable instanceof NotFoundException &&
                                    throwable.getMessage().contains("User not found"))
                    .verify();

            verify(userRepository).findById(actorAdminId);
            verify(userRepository).findById(testUserId);
            verify(userRepository, never()).save(any());
        }
    }

    @Test
    void update_NotAdmin_ThrowsForbidden() {
        // Arrange
        User nonAdmin = new User();
        nonAdmin.setId(actorAdminId);
        nonAdmin.setRole(UserRole.ROLE_CLIENT);

        User testUser = new User();
        testUser.setId(testUserId);
        testUser.setRole(UserRole.ROLE_CLIENT);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(nonAdmin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(testUser));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.update(testUserId, new UserRequest()))
                    .expectErrorMatches(throwable ->
                            throwable instanceof ForbiddenException &&
                                    throwable.getMessage().contains("Only ADMIN can perform this action"))
                    .verify();

            verify(userRepository).findById(actorAdminId);
        }
    }

    // -----------------------
    // delete tests
    // -----------------------
    @Test
    void delete_Success_DeletesUserAndApplications() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User userToDelete = new User();
        userToDelete.setId(testUserId);
        userToDelete.setUsername("toDelete");

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(userToDelete));

        // applicationServiceClient.deleteApplicationsByUserId is void -> doNothing
        doNothing().when(applicationServiceClient).deleteApplicationsByUserId(testUserId.toString());
        when(userRepository.delete(userToDelete)).thenReturn(Mono.empty());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.delete(testUserId))
                    .verifyComplete();

            verify(applicationServiceClient).deleteApplicationsByUserId(testUserId.toString());
            verify(userRepository).delete(userToDelete);
        }
    }

    @Test
    void delete_ApplicationServiceFails_ContinuesUserDeletion_or_errorsDependingOnImplementation() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User userToDelete = new User();
        userToDelete.setId(testUserId);
        userToDelete.setUsername("toDelete");

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(userToDelete));

        // Feign клиент бросает исключение
        doThrow(new RuntimeException("Service unavailable"))
                .when(applicationServiceClient).deleteApplicationsByUserId(testUserId.toString());

        when(userRepository.delete(userToDelete)).thenReturn(Mono.empty());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            // Depending on implementation: if exception from applicationServiceClient is not swallowed, service.delete will error.
            StepVerifier.create(userService.delete(testUserId))
                    .expectError() // expecting error because feign call throws in fromCallable
                    .verify();

            // ensure delete on repository still might be invoked in implementation; keep verification for repository.delete called (or not)
            verify(userRepository).delete(userToDelete);
        }
    }

    // -----------------------
    // promoteToManager tests
    // -----------------------
    @Test
    void promoteToManager_Success_PromotesClientToManager() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User client = new User();
        client.setId(testUserId);
        client.setRole(UserRole.ROLE_CLIENT);

        User promotedUser = new User();
        promotedUser.setId(testUserId);
        promotedUser.setRole(UserRole.ROLE_MANAGER);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(client));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(promotedUser));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.promoteToManager(testUserId))
                    .verifyComplete();

            verify(userRepository).save(argThat(user ->
                    user.getRole() == UserRole.ROLE_MANAGER));
        }
    }

    @Test
    void promoteToManager_AlreadyManager_DoesNothing() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User manager = new User();
        manager.setId(testUserId);
        manager.setRole(UserRole.ROLE_MANAGER);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(manager));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.promoteToManager(testUserId))
                    .verifyComplete();

            verify(userRepository, never()).save(any());
        }
    }

    // -----------------------
    // demoteToClient tests
    // -----------------------
    @Test
    void demoteToClient_Success_DemotesManagerToClient() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User manager = new User();
        manager.setId(testUserId);
        manager.setRole(UserRole.ROLE_MANAGER);

        User demotedUser = new User();
        demotedUser.setId(testUserId);
        demotedUser.setRole(UserRole.ROLE_CLIENT);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(manager));
        when(userRepository.save(any(User.class))).thenReturn(Mono.just(demotedUser));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.demoteToClient(testUserId))
                    .verifyComplete();

            verify(userRepository).save(argThat(user ->
                    user.getRole() == UserRole.ROLE_CLIENT));
        }
    }

    @Test
    void demoteToClient_AlreadyClient_DoesNothing() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        User client = new User();
        client.setId(testUserId);
        client.setRole(UserRole.ROLE_CLIENT);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));
        when(userRepository.findById(testUserId)).thenReturn(Mono.just(client));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.demoteToClient(testUserId))
                    .verifyComplete();

            verify(userRepository, never()).save(any());
        }
    }

    // -----------------------
    // count tests
    // -----------------------
    @Test
    void count_ReturnsUserCount() {
        // Arrange
        long userCount = 42L;
        when(userRepository.count()).thenReturn(Mono.just(userCount));

        // Act & Assert
        StepVerifier.create(userService.count())
                .expectNext(userCount)
                .verifyComplete();

        verify(userRepository).count();
    }

    // -----------------------
    // validateAdmin tests (service's validateAdmin reads ReactiveSecurityContextHolder)
    // -----------------------
    @Test
    void validateAdmin_AdminExists_ReturnsAdmin() {
        // Arrange
        User admin = new User();
        admin.setId(actorAdminId);
        admin.setRole(UserRole.ROLE_ADMIN);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(admin));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.validateAdmin())
                    .expectNext(admin)
                    .verifyComplete();
        }
    }

    @Test
    void validateAdmin_NotAdmin_ThrowsForbidden() {
        // Arrange
        User client = new User();
        client.setId(actorAdminId);
        client.setRole(UserRole.ROLE_CLIENT);

        when(userRepository.findById(actorAdminId)).thenReturn(Mono.just(client));

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.validateAdmin())
                    .expectErrorMatches(throwable ->
                            throwable instanceof ForbiddenException &&
                                    throwable.getMessage().contains("Only ADMIN can perform this action"))
                    .verify();
        }
    }

    @Test
    void validateAdmin_UserNotFound_ThrowsNotFound() {
        // Arrange
        when(userRepository.findById(actorAdminId)).thenReturn(Mono.empty());

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(actorAdminId.toString(), null);
        SecurityContextImpl secCtx = new SecurityContextImpl(auth);

        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.just(secCtx));

            // Act & Assert
            StepVerifier.create(userService.validateAdmin())
                    .expectErrorMatches(throwable ->
                            throwable instanceof NotFoundException &&
                                    throwable.getMessage().contains("Actor not found"))
                    .verify();
        }
    }

    @Test
    void validateAdmin_NoSecurityContext_ThrowsUnauthorized() {
        try (MockedStatic<ReactiveSecurityContextHolder> mockedSec = Mockito.mockStatic(ReactiveSecurityContextHolder.class)) {
            mockedSec.when(ReactiveSecurityContextHolder::getContext).thenReturn(Mono.empty());

            StepVerifier.create(userService.validateAdmin())
                    .expectErrorMatches(throwable ->
                            throwable instanceof UnauthorizedException)
                    .verify();
        }
    }
}