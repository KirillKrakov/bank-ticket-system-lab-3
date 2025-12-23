package com.example.productservice.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public class ProductDto {

    private UUID id;

    @Size(min = 1, max = 200)
    private String name;

    @Size(min = 1, max = 1000)
    private String description;

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