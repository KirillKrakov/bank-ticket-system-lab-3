/*package com.example.userservice.controller;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserRequest;
import com.example.userservice.exception.*;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserDto createSampleUserDto() {
        UserDto dto = new UserDto();
        dto.setId(UUID.randomUUID());
        dto.setUsername("testuser");
        dto.setEmail("test@example.com");
        dto.setRole(UserRole.ROLE_CLIENT);
        dto.setCreatedAt(Instant.now());
        return dto;
    }

    private UserRequest createSampleUserRequest() {
        UserRequest request = new UserRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("Password123");
        return request;
    }

    // -----------------------
    // createUser tests
    // -----------------------
    @Test
    public void createUser_success_returnsCreated() {
        UserRequest request = createSampleUserRequest();
        UserDto responseDto = createSampleUserDto();

        when(userService.create(request))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(userController.createUser(request))
                .expectNext(responseDto)
                .verifyComplete();
    }

    @Test
    public void createUser_serviceThrowsBadRequest_returnsError() {
        UserRequest request = createSampleUserRequest();

        when(userService.create(request))
                .thenReturn(Mono.error(new BadRequestException("Invalid request")));

        StepVerifier.create(userController.createUser(request))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void createUser_serviceThrowsConflict_returnsError() {
        UserRequest request = createSampleUserRequest();

        when(userService.create(request))
                .thenReturn(Mono.error(new ConflictException("Username already in use")));

        StepVerifier.create(userController.createUser(request))
                .expectError(ConflictException.class)
                .verify();
    }

    // -----------------------
    // getAllUsers tests
    // -----------------------
    @Test
    public void getAllUsers_success_returnsFlux() {
        UserDto dto1 = createSampleUserDto();
        UserDto dto2 = createSampleUserDto();

        when(userService.findAll(0, 20))
                .thenReturn(Flux.just(dto1, dto2));

        StepVerifier.create(userController.getAllUsers(0, 20))
                .expectNext(dto1)
                .expectNext(dto2)
                .verifyComplete();
    }

    @Test
    public void getAllUsers_sizeExceedsMax_returnsBadRequest() {
        StepVerifier.create(userController.getAllUsers(0, 51))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    public void getAllUsers_negativePage_returnsResults() {
        UserDto dto = createSampleUserDto();

        when(userService.findAll(-1, 10))
                .thenReturn(Flux.just(dto));

        StepVerifier.create(userController.getAllUsers(-1, 10))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    public void getAllUsers_serviceThrowsBadRequest_returnsError() {
        when(userService.findAll(0, 20))
                .thenReturn(Flux.error(new BadRequestException("Invalid parameters")));

        StepVerifier.create(userController.getAllUsers(0, 20))
                .expectError(BadRequestException.class)
                .verify();
    }

    // -----------------------
    // getUserById tests
    // -----------------------
    @Test
    public void getUserById_found_returnsDto() {
        UUID userId = UUID.randomUUID();
        UserDto dto = createSampleUserDto();
        dto.setId(userId);

        when(userService.findById(userId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(userController.getUserById(userId))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    public void getUserById_notFound_returnsError() {
        UUID userId = UUID.randomUUID();

        when(userService.findById(userId))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.getUserById(userId))
                .expectError(NotFoundException.class)
                .verify();
    }

    // -----------------------
    // updateUser tests
    // -----------------------
    @Test
    public void updateUser_success_returnsDto() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserRequest request = createSampleUserRequest();
        UserDto dto = createSampleUserDto();

        when(userService.update(userId, actorId, request))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(userController.updateUser(userId, actorId, request))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    public void updateUser_forbidden_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserRequest request = createSampleUserRequest();

        when(userService.update(userId, actorId, request))
                .thenReturn(Mono.error(new ForbiddenException("Access denied")));

        StepVerifier.create(userController.updateUser(userId, actorId, request))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void updateUser_notFound_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserRequest request = createSampleUserRequest();

        when(userService.update(userId, actorId, request))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.updateUser(userId, actorId, request))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void updateUser_unauthorized_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UserRequest request = createSampleUserRequest();

        when(userService.update(userId, actorId, request))
                .thenReturn(Mono.error(new UnauthorizedException("Actor ID is required")));

        StepVerifier.create(userController.updateUser(userId, actorId, request))
                .expectError(UnauthorizedException.class)
                .verify();
    }

    // -----------------------
    // deleteUser tests
    // -----------------------
    @Test
    public void deleteUser_success_returnsVoid() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.delete(userId, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(userController.deleteUser(userId, actorId))
                .verifyComplete();
    }

    @Test
    public void deleteUser_forbidden_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.delete(userId, actorId))
                .thenReturn(Mono.error(new ForbiddenException("Only ADMIN can perform this action")));

        StepVerifier.create(userController.deleteUser(userId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void deleteUser_notFound_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.delete(userId, actorId))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.deleteUser(userId, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    // -----------------------
    // promoteToManager tests
    // -----------------------
    @Test
    public void promoteToManager_success_returnsVoid() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.promoteToManager(userId, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(userController.promoteToManager(userId, actorId))
                .verifyComplete();
    }

    @Test
    public void promoteToManager_forbidden_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.promoteToManager(userId, actorId))
                .thenReturn(Mono.error(new ForbiddenException("Only ADMIN can perform this action")));

        StepVerifier.create(userController.promoteToManager(userId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void promoteToManager_notFound_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.promoteToManager(userId, actorId))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.promoteToManager(userId, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    // -----------------------
    // demoteToClient tests
    // -----------------------
    @Test
    public void demoteToClient_success_returnsVoid() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.demoteToClient(userId, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(userController.demoteToClient(userId, actorId))
                .verifyComplete();
    }

    @Test
    public void demoteToClient_forbidden_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.demoteToClient(userId, actorId))
                .thenReturn(Mono.error(new ForbiddenException("Only ADMIN can perform this action")));

        StepVerifier.create(userController.demoteToClient(userId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void demoteToClient_notFound_returnsError() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userService.demoteToClient(userId, actorId))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.demoteToClient(userId, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    // -----------------------
    // userExists tests
    // -----------------------
    @Test
    public void userExists_found_returnsTrue() {
        UUID userId = UUID.randomUUID();
        UserDto dto = createSampleUserDto();

        when(userService.findById(userId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(userController.userExists(userId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                Boolean.TRUE.equals(response.getBody()))
                .verifyComplete();
    }

    @Test
    public void userExists_notFound_returnsFalse() {
        UUID userId = UUID.randomUUID();

        when(userService.findById(userId))
                .thenReturn(Mono.empty());

        StepVerifier.create(userController.userExists(userId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                Boolean.FALSE.equals(response.getBody()))
                .verifyComplete();
    }

    // -----------------------
    // getUserRole tests
    // -----------------------
    @Test
    public void getUserRole_found_returnsRole() {
        UUID userId = UUID.randomUUID();
        UserDto dto = createSampleUserDto();
        dto.setRole(UserRole.ROLE_CLIENT);

        when(userService.findById(userId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(userController.getUserRole(userId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                response.getBody() == UserRole.ROLE_CLIENT)
                .verifyComplete();
    }

    @Test
    public void getUserRole_notFound_returnsNotFound() {
        UUID userId = UUID.randomUUID();

        when(userService.findById(userId))
                .thenReturn(Mono.empty());

        StepVerifier.create(userController.getUserRole(userId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.NOT_FOUND)
                .verifyComplete();
    }
}*/