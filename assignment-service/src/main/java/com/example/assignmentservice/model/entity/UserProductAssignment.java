package com.example.assignmentservice.model.entity;

import com.example.assignmentservice.model.enums.AssignmentRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_product_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "product_id"}))
public class UserProductAssignment {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_on_product", length = 100)
    private AssignmentRole roleOnProduct;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public UserProductAssignment() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public AssignmentRole getRoleOnProduct() { return roleOnProduct; }
    public void setRoleOnProduct(AssignmentRole roleOnProduct) { this.roleOnProduct = roleOnProduct; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }
}