package com.example.applicationservice.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class DocumentDto {
    private UUID id;
    @NotBlank
    private String fileName;
    private String contentType;
    private String storagePath;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
}
