package com.example.productservice.model.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 200)
    private String name;

    @Column(length = 1000)
    private String description;

    public Product() {}

    public Product(UUID id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}