package com.example.assignmentservice.repository;

import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProductAssignmentRepository extends JpaRepository<UserProductAssignment, UUID> {

    List<UserProductAssignment> findByUserId(UUID userId);
    List<UserProductAssignment> findByProductId(UUID productId);

    Optional<UserProductAssignment> findByUserIdAndProductId(UUID userId, UUID productId);

    boolean existsByUserIdAndProductIdAndRoleOnProduct(UUID userId, UUID productId, AssignmentRole role);

    boolean existsByUserIdAndProductId(UUID userId, UUID productId);

    @Modifying
    @Query("DELETE FROM UserProductAssignment a WHERE a.userId = :userId AND a.productId = :productId")
    void deleteByUserIdAndProductId(@Param("userId") UUID userId, @Param("productId") UUID productId);

    @Modifying
    @Query("DELETE FROM UserProductAssignment a WHERE a.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM UserProductAssignment a WHERE a.productId = :productId")
    void deleteByProductId(@Param("productId") UUID productId);
}