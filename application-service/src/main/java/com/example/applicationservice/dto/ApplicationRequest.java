package com.example.applicationservice.dto;

import java.util.List;
import java.util.UUID;

public class ApplicationRequest {
    private UUID applicantId;
    private UUID productId;
    private List<DocumentRequest> documents;
    private List<String> tags;

    // getters/setters
    public UUID getApplicantId() { return applicantId; }
    public void setApplicantId(UUID applicantId) { this.applicantId = applicantId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public List<DocumentRequest> getDocuments() { return documents; }
    public void setDocuments(List<DocumentRequest> documents) { this.documents = documents; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
}