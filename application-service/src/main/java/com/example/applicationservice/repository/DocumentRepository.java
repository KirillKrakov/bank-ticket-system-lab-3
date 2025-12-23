package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    long countByApplicationId(UUID applicationId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Document d WHERE d.application.id = :applicationId")
    void deleteByApplicationId(@Param("applicationId") UUID applicationId);
}
