package com.example.assignmentservice.dto;

import com.example.assignmentservice.model.enums.AssignmentRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class UserProductAssignmentRequest {

    @NotNull
    private UUID userId;

    @NotNull
    private UUID productId;

    @NotNull
    private AssignmentRole role;

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public AssignmentRole getRole() { return role; }
    public void setRole(AssignmentRole role) { this.role = role; }
}