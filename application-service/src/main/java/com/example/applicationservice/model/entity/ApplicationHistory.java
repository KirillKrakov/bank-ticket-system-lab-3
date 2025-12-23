package com.example.applicationservice.model.entity;

import com.example.applicationservice.model.enums.ApplicationStatus;
import com.example.applicationservice.model.enums.UserRole;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "application_history")
public class ApplicationHistory {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 50)
    private ApplicationStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", length = 50)
    private ApplicationStatus newStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by", length = 100)
    private UserRole changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public ApplicationHistory() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public ApplicationStatus getOldStatus() { return oldStatus; }
    public void setOldStatus(ApplicationStatus oldStatus) { this.oldStatus = oldStatus; }

    public ApplicationStatus getNewStatus() { return newStatus; }
    public void setNewStatus(ApplicationStatus newStatus) { this.newStatus = newStatus; }

    public UserRole getChangedBy() { return changedBy; }
    public void setChangedBy(UserRole changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
