package com.example.applicationservice.dto;

import com.example.applicationservice.model.enums.ApplicationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ApplicationDto {
    private UUID id;
    private UUID applicantId;
    private UUID productId;
    private ApplicationStatus status;
    private Instant createdAt;
    private List<DocumentDto> documents;
    private List<String> tags;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicantId() { return applicantId; }
    public void setApplicantId(UUID applicantId) { this.applicantId = applicantId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<DocumentDto> getDocuments() { return documents; }
    public void setDocuments(List<DocumentDto> documents) { this.documents = documents; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}
