package com.example.assignmentservice.controller;

import com.example.assignmentservice.controller.UserProductAssignmentController;
import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.dto.UserProductAssignmentRequest;
import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.service.UserProductAssignmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProductAssignmentControllerTest {

    @Mock
    private UserProductAssignmentService service;

    @Mock
    private Jwt jwt;

    @InjectMocks
    private UserProductAssignmentController controller;

    private final UUID testUserId = UUID.randomUUID();
    private final UUID testProductId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private UserProductAssignmentDto testDto;
    private UserProductAssignment testAssignment;

    @BeforeEach
    void setUp() {
        testDto = new UserProductAssignmentDto();
        testDto.setId(assignmentId);
        testDto.setUserId(testUserId);
        testDto.setProductId(testProductId);
        testDto.setRole(AssignmentRole.PRODUCT_OWNER);

        testAssignment = new UserProductAssignment();
        testAssignment.setId(assignmentId);
        testAssignment.setUserId(testUserId);
        testAssignment.setProductId(testProductId);
        testAssignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
    }

    @Test
    void assign_Success_ReturnsCreated() {
        // Arrange
        UserProductAssignmentRequest req = new UserProductAssignmentRequest();
        req.setUserId(testUserId);
        req.setProductId(testProductId);
        req.setRole(AssignmentRole.PRODUCT_OWNER);

        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());
        when(jwt.getClaimAsString("role")).thenReturn("ROLE_ADMIN");
        when(service.assign(eq(actorId), eq("ROLE_ADMIN"), eq(testUserId), eq(testProductId), eq(AssignmentRole.PRODUCT_OWNER)))
                .thenReturn(testAssignment);
        when(service.toDto(testAssignment)).thenReturn(testDto);

        // Act
        ResponseEntity<UserProductAssignmentDto> response = controller.assign(
                req, jwt, UriComponentsBuilder.newInstance());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(testDto);
        assertThat(response.getHeaders().getLocation()).isNotNull();

        verify(service).assign(actorId, "ROLE_ADMIN", testUserId, testProductId, AssignmentRole.PRODUCT_OWNER);
        verify(service).toDto(testAssignment);
        verify(jwt).getClaimAsString("uid");
        verify(jwt).getClaimAsString("role");
    }

    @Test
    void assign_UsesSubjectWhenUidNotPresent_Success() {
        // Arrange
        UserProductAssignmentRequest req = new UserProductAssignmentRequest();
        req.setUserId(testUserId);
        req.setProductId(testProductId);
        req.setRole(AssignmentRole.PRODUCT_OWNER);

        when(jwt.getClaimAsString("uid")).thenReturn(null);
        when(jwt.getSubject()).thenReturn(actorId.toString());
        when(jwt.getClaimAsString("role")).thenReturn("ROLE_ADMIN");
        when(service.assign(eq(actorId), eq("ROLE_ADMIN"), eq(testUserId), eq(testProductId), eq(AssignmentRole.PRODUCT_OWNER)))
                .thenReturn(testAssignment);
        when(service.toDto(testAssignment)).thenReturn(testDto);

        // Act
        ResponseEntity<UserProductAssignmentDto> response = controller.assign(
                req, jwt, UriComponentsBuilder.newInstance());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(testDto);

        verify(jwt).getClaimAsString("uid");
        verify(jwt).getSubject();
        verify(jwt).getClaimAsString("role");
    }

    @Test
    void assign_Unauthorized_WhenJwtIsNull() {
        // Arrange
        UserProductAssignmentRequest req = new UserProductAssignmentRequest();
        req.setUserId(testUserId);
        req.setProductId(testProductId);
        req.setRole(AssignmentRole.PRODUCT_OWNER);

        // Act
        ResponseEntity<UserProductAssignmentDto> response = controller.assign(
                req, null, UriComponentsBuilder.newInstance());

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).assign(any(), any(), any(), any(), any());
    }

    @Test
    void list_NoFilters_ReturnsAllAssignments() {
        // Arrange
        List<UserProductAssignmentDto> assignments = List.of(testDto);
        when(service.list(null, null)).thenReturn(assignments);

        // Act
        ResponseEntity<List<UserProductAssignmentDto>> response = controller.list(null, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(assignments);

        verify(service).list(null, null);
    }

    @Test
    void list_WithFilters_ReturnsFilteredAssignments() {
        // Arrange
        List<UserProductAssignmentDto> assignments = List.of(testDto);
        when(service.list(testUserId, testProductId)).thenReturn(assignments);

        // Act
        ResponseEntity<List<UserProductAssignmentDto>> response = controller.list(testUserId, testProductId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(assignments);

        verify(service).list(testUserId, testProductId);
    }

    @Test
    void exists_AssignmentExists_ReturnsTrue() {
        // Arrange
        when(service.existsByUserAndProductAndRole(testUserId, testProductId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(true);

        // Act
        ResponseEntity<Boolean> response = controller.exists(testUserId, testProductId, "PRODUCT_OWNER");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isTrue();

        verify(service).existsByUserAndProductAndRole(testUserId, testProductId, AssignmentRole.PRODUCT_OWNER);
    }

    @Test
    void exists_AssignmentNotExists_ReturnsFalse() {
        // Arrange
        when(service.existsByUserAndProductAndRole(testUserId, testProductId, AssignmentRole.PRODUCT_OWNER))
                .thenReturn(false);

        // Act
        ResponseEntity<Boolean> response = controller.exists(testUserId, testProductId, "PRODUCT_OWNER");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isFalse();
    }

    @Test
    void exists_InvalidRole_ReturnsBadRequest() {
        // Act
        ResponseEntity<Boolean> response = controller.exists(testUserId, testProductId, "INVALID_ROLE");

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isFalse();

        verify(service, never()).existsByUserAndProductAndRole(any(), any(), any());
    }

    @Test
    void deleteAssignments_WithAllParams_DeletesWithFilters() {
        // Arrange
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());
        when(jwt.getClaimAsString("role")).thenReturn("ROLE_ADMIN");
        doNothing().when(service).deleteAssignments(actorId, "ROLE_ADMIN", testUserId, testProductId);

        // Act
        ResponseEntity<Void> response = controller.deleteAssignments(jwt, testUserId, testProductId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(service).deleteAssignments(actorId, "ROLE_ADMIN", testUserId, testProductId);
        verify(jwt).getClaimAsString("uid");
        verify(jwt).getClaimAsString("role");
    }

    @Test
    void deleteAssignments_WithoutFilters_DeletesAll() {
        // Arrange
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());
        when(jwt.getClaimAsString("role")).thenReturn("ROLE_ADMIN");
        doNothing().when(service).deleteAssignments(actorId, "ROLE_ADMIN", null, null);

        // Act
        ResponseEntity<Void> response = controller.deleteAssignments(jwt, null, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(service).deleteAssignments(actorId, "ROLE_ADMIN", null, null);
    }

    @Test
    void deleteAssignments_Unauthorized_WhenJwtIsNull() {
        // Act
        ResponseEntity<Void> response = controller.deleteAssignments(null, testUserId, testProductId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(service, never()).deleteAssignments(any(), any(), any(), any());
    }

    @Test
    void deleteAssignments_UsesSubjectWhenUidNotPresent_Success() {
        // Arrange
        when(jwt.getClaimAsString("uid")).thenReturn(null);
        when(jwt.getSubject()).thenReturn(actorId.toString());
        when(jwt.getClaimAsString("role")).thenReturn("ROLE_ADMIN");
        doNothing().when(service).deleteAssignments(actorId, "ROLE_ADMIN", null, null);

        // Act
        ResponseEntity<Void> response = controller.deleteAssignments(jwt, null, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(jwt).getClaimAsString("uid");
        verify(jwt).getSubject();
        verify(jwt).getClaimAsString("role");
    }
}