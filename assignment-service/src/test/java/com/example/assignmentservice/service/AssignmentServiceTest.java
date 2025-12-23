package com.example.assignmentservice.service;

import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.exception.ForbiddenException;
import com.example.assignmentservice.exception.NotFoundException;
import com.example.assignmentservice.exception.ServiceUnavailableException;
import com.example.assignmentservice.exception.UnauthorizedException;
import com.example.assignmentservice.feign.ProductServiceClient;
import com.example.assignmentservice.feign.UserServiceClient;
import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.model.enums.UserRole;
import com.example.assignmentservice.repository.UserProductAssignmentRepository;
import com.example.assignmentservice.service.UserProductAssignmentService;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private UserProductAssignmentRepository repo;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @InjectMocks
    private UserProductAssignmentService svc;

    private UUID actorId;
    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        userId = UUID.randomUUID();
        productId = UUID.randomUUID();
    }

    // -----------------------
    // assign tests
    // -----------------------
    @Test
    void assign_createsNewAssignment_whenNoExisting_and_actorIsAdmin() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(true);
        when(productServiceClient.productExists(productId)).thenReturn(true);
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(false);
        when(repo.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.empty());

        UserProductAssignment savedAssignment = new UserProductAssignment();
        savedAssignment.setId(UUID.randomUUID());
        savedAssignment.setUserId(userId);
        savedAssignment.setProductId(productId);
        savedAssignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        savedAssignment.setAssignedAt(Instant.now());

        when(repo.save(any(UserProductAssignment.class))).thenReturn(savedAssignment);

        // Act
        UserProductAssignment result = svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER);

        // Assert
        assertNotNull(result);
        verify(repo, times(1)).save(any(UserProductAssignment.class));
        verify(userServiceClient, times(1)).getUserRole(actorId);
        verify(userServiceClient, times(1)).userExists(userId);
        verify(productServiceClient, times(1)).productExists(productId);
    }

    @Test
    void assign_updatesExistingAssignment_whenFound_and_actorIsOwner() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(userServiceClient.userExists(userId)).thenReturn(true);
        when(productServiceClient.productExists(productId)).thenReturn(true);
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(true);

        // Сохраняем старое время для проверки
        Instant oldAssignedAt = Instant.now().minusSeconds(3600);

        UserProductAssignment existingAssignment = new UserProductAssignment();
        existingAssignment.setId(UUID.randomUUID());
        existingAssignment.setUserId(userId);
        existingAssignment.setProductId(productId);
        existingAssignment.setRoleOnProduct(AssignmentRole.VIEWER);
        existingAssignment.setAssignedAt(oldAssignedAt);

        when(repo.findByUserIdAndProductId(userId, productId)).thenReturn(Optional.of(existingAssignment));

        // Мокаем сохранение, возвращая измененный объект
        when(repo.save(any(UserProductAssignment.class))).thenAnswer(invocation -> {
            UserProductAssignment saved = invocation.getArgument(0);
            // Сервис уже установил новое время, возвращаем как есть
            return saved;
        });

        // Act
        UserProductAssignment result = svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER);

        // Assert
        assertNotNull(result);
        assertEquals(AssignmentRole.PRODUCT_OWNER, result.getRoleOnProduct());
        assertTrue(result.getAssignedAt().isAfter(oldAssignedAt),
                "New assignedAt should be after old assignedAt");
        verify(repo, times(1)).save(existingAssignment);
    }

    @Test
    void assign_throwsForbidden_whenActorIsNotAdminAndNotOwner() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(actorId, productId, AssignmentRole.PRODUCT_OWNER)).thenReturn(false);

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
        verify(userServiceClient, never()).userExists(any());
        verify(productServiceClient, never()).productExists(any());
    }

    @Test
    void assign_throwsNotFound_whenUserNotFound() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(false);

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
    }

    @Test
    void assign_throwsNotFound_whenProductNotFound() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(true);
        when(productServiceClient.productExists(productId)).thenReturn(false);

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
    }

    @Test
    void assign_throwsServiceUnavailable_whenUserServiceFails() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenThrow(FeignException.class);

        // Act & Assert
        assertThrows(ServiceUnavailableException.class,
                () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
    }

    @Test
    void assign_throwsNotFoundException_whenActorNotFound() {
        // Arrange
        FeignException.NotFound notFoundException = mock(FeignException.NotFound.class);
        when(userServiceClient.getUserRole(actorId)).thenThrow(notFoundException);

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));

        verify(repo, never()).save(any());
    }

    // -----------------------
    // list tests
    // -----------------------
    @Test
    void list_byUser_returnsMappedDtos() {
        // Arrange
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(userId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment.setAssignedAt(Instant.now());

        when(repo.findByUserId(userId)).thenReturn(List.of(assignment));

        // Act
        List<UserProductAssignmentDto> result = svc.list(userId, null);

        // Assert
        assertEquals(1, result.size());
        UserProductAssignmentDto dto = result.get(0);
        assertEquals(assignment.getId(), dto.getId());
        assertEquals(userId, dto.getUserId());
        assertEquals(productId, dto.getProductId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, dto.getRole());
        assertEquals(assignment.getAssignedAt(), dto.getAssignedAt());
    }

    @Test
    void list_byProduct_returnsMappedDtos() {
        // Arrange
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(userId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(AssignmentRole.VIEWER);
        assignment.setAssignedAt(Instant.now());

        when(repo.findByProductId(productId)).thenReturn(List.of(assignment));

        // Act
        List<UserProductAssignmentDto> result = svc.list(null, productId);

        // Assert
        assertEquals(1, result.size());
        UserProductAssignmentDto dto = result.get(0);
        assertEquals(productId, dto.getProductId());
        assertEquals(userId, dto.getUserId());
        assertEquals(AssignmentRole.VIEWER, dto.getRole());
    }

    @Test
    void list_all_returnsMappedDtos() {
        // Arrange
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(userId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment.setAssignedAt(Instant.now());

        when(repo.findAll()).thenReturn(List.of(assignment));

        // Act
        List<UserProductAssignmentDto> result = svc.list(null, null);

        // Assert
        assertEquals(1, result.size());
        assertEquals(assignment.getId(), result.get(0).getId());
    }

    @Test
    void list_byUser_whenNoAssignments_returnsEmptyList() {
        // Arrange
        when(repo.findByUserId(userId)).thenReturn(List.of());

        // Act
        List<UserProductAssignmentDto> result = svc.list(userId, null);

        // Assert
        assertTrue(result.isEmpty());
    }

    // -----------------------
    // deleteAssignments tests
    // -----------------------
    @Test
    void deleteAssignments_throwsForbidden_whenActorNotAdmin() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);

        // Act & Assert
        assertThrows(ForbiddenException.class,
                () -> svc.deleteAssignments(actorId, userId, productId));

        verify(repo, never()).deleteByUserIdAndProductId(any(), any());
    }

    @Test
    void deleteAssignments_deleteSpecificAssignment_whenBothIdsProvided() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(true);
        when(productServiceClient.productExists(productId)).thenReturn(true);

        // Act
        svc.deleteAssignments(actorId, userId, productId);

        // Assert
        verify(repo, times(1)).deleteByUserIdAndProductId(userId, productId);
        verify(userServiceClient, times(1)).userExists(userId);
        verify(productServiceClient, times(1)).productExists(productId);
    }

    @Test
    void deleteAssignments_deleteByUser_whenUserProvided() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(true);

        // Act
        svc.deleteAssignments(actorId, userId, null);

        // Assert
        verify(repo, times(1)).deleteByUserId(userId);
        verify(userServiceClient, times(1)).userExists(userId);
        verify(productServiceClient, never()).productExists(any());
    }

    @Test
    void deleteAssignments_deleteByProduct_whenProductProvided() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(productServiceClient.productExists(productId)).thenReturn(true);

        // Act
        svc.deleteAssignments(actorId, null, productId);

        // Assert
        verify(repo, times(1)).deleteByProductId(productId);
        verify(productServiceClient, times(1)).productExists(productId);
        verify(userServiceClient, never()).userExists(any());
    }

    @Test
    void deleteAssignments_deleteAll_whenNoIdsProvided() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);

        // Act
        svc.deleteAssignments(actorId, null, null);

        // Assert
        verify(repo, times(1)).deleteAll();
        verify(userServiceClient, never()).userExists(any());
        verify(productServiceClient, never()).productExists(any());
    }

    @Test
    void deleteAssignments_throwsNotFoundException_whenUserNotFound() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(false);

        // Act & Assert
        assertThrows(NotFoundException.class,
                () -> svc.deleteAssignments(actorId, userId, null));

        verify(repo, never()).deleteByUserId(any());
    }

    @Test
    void deleteAssignments_throwsServiceUnavailable_whenUserServiceFails() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenThrow(FeignException.class);

        // Act & Assert
        assertThrows(ServiceUnavailableException.class,
                () -> svc.deleteAssignments(actorId, userId, productId));

        verify(repo, never()).deleteByUserIdAndProductId(any(), any());
    }

    @Test
    void deleteAssignments_throwsUnauthorized_whenActorIdIsNull() {
        // Act & Assert
        assertThrows(UnauthorizedException.class,
                () -> svc.deleteAssignments(null, userId, productId));
    }

    // -----------------------
    // existsByUserAndProductAndRole tests
    // -----------------------
    @Test
    void existsByUserAndProductAndRole_returnsTrue_whenAssignmentExists() {
        // Arrange
        AssignmentRole role = AssignmentRole.PRODUCT_OWNER;
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(userId, productId, role)).thenReturn(true);

        // Act
        boolean result = svc.existsByUserAndProductAndRole(userId, productId, role);

        // Assert
        assertTrue(result);
        verify(repo, times(1)).existsByUserIdAndProductIdAndRoleOnProduct(userId, productId, role);
    }

    @Test
    void existsByUserAndProductAndRole_returnsFalse_whenAssignmentDoesNotExist() {
        // Arrange
        AssignmentRole role = AssignmentRole.VIEWER;
        when(repo.existsByUserIdAndProductIdAndRoleOnProduct(userId, productId, role)).thenReturn(false);

        // Act
        boolean result = svc.existsByUserAndProductAndRole(userId, productId, role);

        // Assert
        assertFalse(result);
        verify(repo, times(1)).existsByUserIdAndProductIdAndRoleOnProduct(userId, productId, role);
    }

    // -----------------------
    // deleteByProductId tests
    // -----------------------
    @Test
    void deleteByProductId_callsRepositoryDelete() {
        // Act
        svc.deleteByProductId(productId);

        // Assert
        verify(repo, times(1)).deleteByProductId(productId);
    }

    @Test
    void deleteByUserId_callsRepositoryDelete() {
        // Act
        svc.deleteByUserId(userId);

        // Assert
        verify(repo, times(1)).deleteByUserId(userId);
    }

    // -----------------------
    // toDto tests
    // -----------------------
    @Test
    void toDto_mapsAllFields() {
        // Arrange
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(userId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment.setAssignedAt(Instant.now());

        // Act
        UserProductAssignmentDto dto = svc.toDto(assignment);

        // Assert
        assertEquals(assignment.getId(), dto.getId());
        assertEquals(assignment.getUserId(), dto.getUserId());
        assertEquals(assignment.getProductId(), dto.getProductId());
        assertEquals(assignment.getRoleOnProduct(), dto.getRole());
        assertEquals(assignment.getAssignedAt(), dto.getAssignedAt());
    }

    @Test
    void toDto_withNullAssignment_throwsException() {
        // Act & Assert
        assertThrows(NullPointerException.class, () -> svc.toDto(null));
    }

    // -----------------------
    // Edge cases and exception handling
    // -----------------------
    @Test
    void checkUserExists_throwsServiceUnavailable_whenFeignException1() {
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenThrow(FeignException.class);

        // Act & Assert
        assertThrows(ServiceUnavailableException.class,
                () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));
    }

    @Test
    void checkProductExists_throwsServiceUnavailable_whenFeignException() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(userServiceClient.userExists(userId)).thenReturn(true);
        when(productServiceClient.productExists(productId)).thenThrow(FeignException.class);

        // Act & Assert
        assertThrows(ServiceUnavailableException.class, () -> svc.assign(actorId, userId, productId, AssignmentRole.PRODUCT_OWNER));
    }

    @Test
    void checkAdminRights_throwsServiceUnavailable_whenFeignException() {
        // Arrange
        when(userServiceClient.getUserRole(actorId)).thenThrow(FeignException.class);

        // Act & Assert
        assertThrows(ServiceUnavailableException.class, () -> svc.deleteAssignments(actorId, null, null));
    }
}
