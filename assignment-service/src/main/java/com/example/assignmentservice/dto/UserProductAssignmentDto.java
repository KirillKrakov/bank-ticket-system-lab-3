package com.example.assignmentservice.dto;

import com.example.assignmentservice.model.enums.AssignmentRole;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class UserProductAssignmentDto {

    private UUID id;

    @NotNull
    private UUID userId;

    @NotNull
    private UUID productId;

    @NotNull
    private AssignmentRole role;

    private Instant assignedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public AssignmentRole getRole() { return role; }
    public void setRole(AssignmentRole role) { this.role = role; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
}