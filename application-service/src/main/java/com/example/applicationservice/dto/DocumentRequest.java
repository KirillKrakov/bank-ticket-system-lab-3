package com.example.applicationservice.dto;

import jakarta.validation.constraints.NotBlank;

public class DocumentRequest {
    @NotBlank
    private String fileName;
    private String contentType;
    private String storagePath;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
}