package com.example.userservice.controller;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UserDto sampleUserDto() {
        UserDto dto = new UserDto();
        dto.setId(UUID.randomUUID());
        dto.setUsername("testuser");
        dto.setEmail("test@example.com");
        dto.setRole(UserRole.ROLE_CLIENT);
        dto.setCreatedAt(Instant.now());
        return dto;
    }

    private UserRequest sampleUserRequest() {
        UserRequest req = new UserRequest();
        req.setUsername("newuser");
        req.setEmail("new@example.com");
        req.setPassword("Password123");
        return req;
    }

    // -----------------------
    // createUser
    // -----------------------
    @Test
    void createUser_success() {
        UserRequest request = sampleUserRequest();
        UserDto dto = sampleUserDto();

        when(userService.create(request)).thenReturn(Mono.just(dto));

        StepVerifier.create(userController.createUser(request))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    void createUser_conflict() {
        when(userService.create(any(UserRequest.class)))
                .thenReturn(Mono.error(new ConflictException("Username already exists")));

        StepVerifier.create(userController.createUser(sampleUserRequest()))
                .expectError(ConflictException.class)
                .verify();
    }

    // -----------------------
    // getAllUsers
    // -----------------------
    @Test
    void getAllUsers_success() {
        UserDto u1 = sampleUserDto();
        UserDto u2 = sampleUserDto();

        when(userService.findAll(0, 20))
                .thenReturn(Flux.just(u1, u2));

        StepVerifier.create(userController.getAllUsers(0, 20))
                .expectNext(u1)
                .expectNext(u2)
                .verifyComplete();
    }

    // -----------------------
    // getUserById
    // -----------------------
    @Test
    void getUserById_found() {
        UUID id = UUID.randomUUID();
        UserDto dto = sampleUserDto();
        dto.setId(id);

        when(userService.findById(id)).thenReturn(Mono.just(dto));

        StepVerifier.create(userController.getUserById(id))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    void getUserById_notFound() {
        UUID id = UUID.randomUUID();

        when(userService.findById(id))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.getUserById(id))
                .expectError(NotFoundException.class)
                .verify();
    }

    // -----------------------
    // updateUser
    // -----------------------
    @Test
    void updateUser_success() {
        UUID id = UUID.randomUUID();
        UserDto dto = sampleUserDto();

        when(userService.update(eq(id), any(UserRequest.class)))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(userController.updateUser(id, sampleUserRequest()))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    void updateUser_forbidden() {
        UUID id = UUID.randomUUID();

        when(userService.update(eq(id), any(UserRequest.class)))
                .thenReturn(Mono.error(new ForbiddenException("Forbidden")));

        StepVerifier.create(userController.updateUser(id, sampleUserRequest()))
                .expectError(ForbiddenException.class)
                .verify();
    }

    // -----------------------
    // deleteUser
    // -----------------------
    @Test
    void deleteUser_success() {
        UUID id = UUID.randomUUID();

        when(userService.delete(id)).thenReturn(Mono.empty());

        StepVerifier.create(userController.deleteUser(id))
                .verifyComplete();
    }

    @Test
    void deleteUser_notFound() {
        UUID id = UUID.randomUUID();

        when(userService.delete(id))
                .thenReturn(Mono.error(new NotFoundException("User not found")));

        StepVerifier.create(userController.deleteUser(id))
                .expectError(NotFoundException.class)
                .verify();
    }

    // -----------------------
    // promote / demote
    // -----------------------
    @Test
    void promoteToManager_success() {
        UUID id = UUID.randomUUID();

        when(userService.promoteToManager(id)).thenReturn(Mono.empty());

        StepVerifier.create(userController.promoteToManager(id))
                .verifyComplete();
    }

    @Test
    void demoteToClient_success() {
        UUID id = UUID.randomUUID();

        when(userService.demoteToClient(id)).thenReturn(Mono.empty());

        StepVerifier.create(userController.demoteToClient(id))
                .verifyComplete();
    }

    // -----------------------
    // userExists
    // -----------------------
    @Test
    void userExists_true() {
        UUID id = UUID.randomUUID();

        when(userService.findById(id))
                .thenReturn(Mono.just(sampleUserDto()));

        StepVerifier.create(userController.userExists(id))
                .expectNextMatches(r ->
                        r.getStatusCode() == HttpStatus.OK &&
                                Boolean.TRUE.equals(r.getBody()))
                .verifyComplete();
    }

    @Test
    void userExists_false() {
        UUID id = UUID.randomUUID();

        when(userService.findById(id))
                .thenReturn(Mono.empty());

        StepVerifier.create(userController.userExists(id))
                .expectNextMatches(r ->
                        r.getStatusCode() == HttpStatus.OK &&
                                Boolean.FALSE.equals(r.getBody()))
                .verifyComplete();
    }

    // -----------------------
    // getUserRole
    // -----------------------
    @Test
    void getUserRole_found() {
        UUID id = UUID.randomUUID();
        UserDto dto = sampleUserDto();
        dto.setRole(UserRole.ROLE_MANAGER);

        when(userService.findById(id)).thenReturn(Mono.just(dto));

        StepVerifier.create(userController.getUserRole(id))
                .expectNextMatches(r ->
                        r.getStatusCode() == HttpStatus.OK &&
                                r.getBody() == UserRole.ROLE_MANAGER)
                .verifyComplete();
    }

    @Test
    void getUserRole_notFound() {
        UUID id = UUID.randomUUID();

        when(userService.findById(id)).thenReturn(Mono.empty());

        StepVerifier.create(userController.getUserRole(id))
                .expectNextMatches(r ->
                        r.getStatusCode() == HttpStatus.NOT_FOUND)
                .verifyComplete();
    }
}