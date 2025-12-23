package com.example.tagservice.model.entity;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tag", uniqueConstraints = @UniqueConstraint(columnNames = {"name"}))
public class Tag {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    // Примечание: связи ManyToMany не поддерживаются между микросервисами
    // Это будет отдельная таблица в application-service
    @Transient
    private Set<UUID> applicationIds = new HashSet<>();

    public Tag() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Set<UUID> getApplicationIds() { return applicationIds; }
    public void setApplicationIds(Set<UUID> applicationIds) { this.applicationIds = applicationIds; }
}