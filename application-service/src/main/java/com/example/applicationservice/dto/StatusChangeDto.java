package com.example.applicationservice.dto;

import com.example.applicationservice.model.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public class StatusChangeDto {
    @NotNull
    private ApplicationStatus status;
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
}
