package com.example.applicationservice.dto;

import com.example.applicationservice.model.entity.Application;

import java.time.Instant;
import java.util.UUID;

public class ApplicationInfoDto {
    private UUID id;
    private UUID applicantId;
    private UUID productId;
    private String status;
    private Instant createdAt;

    private ApplicationInfoDto toInfoDto(Application app) {
        ApplicationInfoDto dto = new ApplicationInfoDto();
        dto.setId(app.getId());
        dto.setApplicantId(app.getApplicantId());
        dto.setProductId(app.getProductId());
        dto.setStatus(app.getStatus().toString());
        dto.setCreatedAt(app.getCreatedAt());
        return dto;
    }

    // Геттеры и сеттеры
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicantId() { return applicantId; }
    public void setApplicantId(UUID applicantId) { this.applicantId = applicantId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}