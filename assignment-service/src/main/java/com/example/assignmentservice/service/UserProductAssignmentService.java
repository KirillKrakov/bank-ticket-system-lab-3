package com.example.assignmentservice.service;

import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.exception.*;
import com.example.assignmentservice.feign.ProductServiceClient;
import com.example.assignmentservice.feign.UserServiceClient;
import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.model.enums.UserRole;
import com.example.assignmentservice.repository.UserProductAssignmentRepository;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserProductAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(UserProductAssignmentService.class);

    private final UserProductAssignmentRepository repo;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;

    @Autowired
    public UserProductAssignmentService(
            UserProductAssignmentRepository repo,
            UserServiceClient userServiceClient,
            ProductServiceClient productServiceClient) {
        this.repo = repo;
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
    }

    @Transactional
    public UserProductAssignment assign(UUID actorId, UUID userId, UUID productId, AssignmentRole role) {
        logger.info("Creating assignment: user={}, product={}, role={}, actor={}",
                userId, productId, role, actorId);

        checkActorRights(actorId, productId);

        checkUserAndProductExist(userId, productId);

        Optional<UserProductAssignment> existingAssignment = repo.findByUserIdAndProductId(userId, productId);
        UserProductAssignment assignment = new UserProductAssignment();

        if (existingAssignment.isPresent()) {
            assignment = existingAssignment.get();
            assignment.setRoleOnProduct(role);
            assignment.setAssignedAt(Instant.now());
            logger.info("Updating existing assignment: {}", assignment.getId());
        } else {
            assignment.setId(UUID.randomUUID());
            assignment.setUserId(userId);
            assignment.setProductId(productId);
            assignment.setRoleOnProduct(role);
            assignment.setAssignedAt(Instant.now());
            logger.info("Creating new assignment");
        }

        return repo.save(assignment);
    }

    @Transactional(readOnly = true)
    public List<UserProductAssignmentDto> list(UUID userId, UUID productId) {
        List<UserProductAssignment> assignments;

        if (userId != null) {
            assignments = repo.findByUserId(userId);
        } else if (productId != null) {
            assignments = repo.findByProductId(productId);
        } else {
            assignments = repo.findAll();
        }

        return assignments.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAssignments(UUID actorId, UUID userId, UUID productId) {
        logger.info("Deleting assignments: actor={}, user={}, product={}",
                actorId, userId, productId);

        if (actorId == null) {
            throw new UnauthorizedException("Actor ID is required");
        }

        checkAdminRights(actorId);

        if (userId != null && productId != null) {
            checkUserAndProductExist(userId, productId);
            repo.deleteByUserIdAndProductId(userId, productId);
            logger.info("Deleted assignment for user {} and product {}", userId, productId);

        } else if (userId != null) {
            checkUserExists(userId);
            repo.deleteByUserId(userId);
            logger.info("Deleted all assignments for user {}", userId);

        } else if (productId != null) {
            checkProductExists(productId);
            repo.deleteByProductId(productId);
            logger.info("Deleted all assignments for product {}", productId);

        } else {
            repo.deleteAll();
            logger.info("Deleted all assignments");
        }
    }

    @Transactional(readOnly = true)
    public boolean existsByUserAndProductAndRole(UUID userId, UUID productId, AssignmentRole role) {
        return repo.existsByUserIdAndProductIdAndRoleOnProduct(userId, productId, role);
    }

    @Transactional
    public void deleteByProductId(UUID productId) {
        repo.deleteByProductId(productId);
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        repo.deleteByUserId(userId);
    }

    // Вспомогательные методы
    private void checkActorRights(UUID actorId, UUID productId) {
        try {
            UserRole actorRole = userServiceClient.getUserRole(actorId);
            boolean isAdmin = actorRole == UserRole.ROLE_ADMIN;
            boolean isOwner = repo.existsByUserIdAndProductIdAndRoleOnProduct(
                    actorId, productId, AssignmentRole.PRODUCT_OWNER);
            if (!isAdmin && !isOwner) {
                throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can assign products");
            }
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Actor not found: " + actorId);
        } catch (FeignException | ServiceUnavailableException e) {
            logger.error("Error checking actor rights: {}", e.getMessage());
            throw new ServiceUnavailableException("Cannot verify user rights. User service is unavailable now");
        }
    }

    private void checkAdminRights(UUID actorId) {
        try {
            UserRole actorRole = userServiceClient.getUserRole(actorId);
            if (actorRole != UserRole.ROLE_ADMIN) {
                throw new ForbiddenException("Only ADMIN can delete assignments");
            }
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Actor not found: " + actorId);
        } catch (FeignException | ServiceUnavailableException e) {
            logger.error("Error checking admin rights: {}", e.getMessage());
            throw new ServiceUnavailableException("Cannot verify admin rights. User service is unavailable now");
        }
    }

    private void checkUserAndProductExist(UUID userId, UUID productId) {
        checkUserExists(userId);
        checkProductExists(productId);
    }

    private void checkUserExists(UUID userId) {
        try {
            Boolean exists = userServiceClient.userExists(userId);
            if (!exists) {
                throw new NotFoundException("User not found: " + userId);
            }
        } catch (FeignException | ServiceUnavailableException e) {
            logger.error("Error checking user existence: {}", e.getMessage());
            throw new ServiceUnavailableException("Cannot verify user. User service unavailable now");
        }
    }

    private void checkProductExists(UUID productId) {
        try {
            Boolean exists = productServiceClient.productExists(productId);
            if (!exists) {
                throw new NotFoundException("Product not found: " + productId);
            }
        } catch (FeignException | ServiceUnavailableException e) {
            logger.error("Error checking product existence: {}", e.getMessage());
            throw new ServiceUnavailableException("Cannot verify product. Product service unavailable now");
        }
    }

    public UserProductAssignmentDto toDto(UserProductAssignment assignment) {
        UserProductAssignmentDto dto = new UserProductAssignmentDto();
        dto.setId(assignment.getId());
        dto.setUserId(assignment.getUserId());
        dto.setProductId(assignment.getProductId());
        dto.setRole(assignment.getRoleOnProduct());
        dto.setAssignedAt(assignment.getAssignedAt());
        return dto;
    }
}