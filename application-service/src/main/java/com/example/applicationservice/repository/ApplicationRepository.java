package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    @Query("SELECT a.id FROM Application a WHERE a.applicantId = :applicantId")
    List<UUID> findIdsByApplicantId(@Param("applicantId") UUID applicantId);

    @Query("SELECT a.id FROM Application a WHERE a.productId = :productId")
    List<UUID> findIdsByProductId(@Param("productId") UUID productId);

    // РАЗДЕЛЬНЫЕ методы для избежания декартова произведения
    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.documents")
    Page<Application> findAllWithDocuments(Pageable pageable);

    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.documents WHERE a.id IN :ids")
    List<Application> findByIdsWithDocuments(@Param("ids") List<UUID> ids);

    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.tags WHERE a.id IN :ids")
    List<Application> findByIdsWithTags(@Param("ids") List<UUID> ids);

    // Отдельные методы для конкретной заявки
    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.documents WHERE a.id = :id")
    Optional<Application> findByIdWithDocuments(@Param("id") UUID id);

    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.tags WHERE a.id = :id")
    Optional<Application> findByIdWithTags(@Param("id") UUID id);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM application_tag WHERE application_id = :applicationId", nativeQuery = true)
    void deleteTagsByApplicationId(@Param("applicationId") UUID applicationId);

    // Методы для получения ID с пагинацией
    @Query("SELECT a.id FROM Application a " +
            "ORDER BY a.createdAt DESC, a.id DESC")
    List<UUID> findIdsFirstPage(Pageable pageable);  // Используем Pageable вместо @Param

    @Query("SELECT a.id FROM Application a " +
            "WHERE (a.createdAt < :timestamp OR (a.createdAt = :timestamp AND a.id < :id)) " +
            "ORDER BY a.createdAt DESC, a.id DESC")
    List<UUID> findIdsByKeyset(@Param("timestamp") Instant timestamp,
                               @Param("id") UUID id,
                               Pageable pageable);

    default List<UUID> findIdsFirstPage(int limit) {
        return findIdsFirstPage(PageRequest.of(0, limit));
    }

    default List<UUID> findIdsByKeyset(Instant timestamp, UUID id, int limit) {
        return findIdsByKeyset(timestamp, id, PageRequest.of(0, limit));
    }

    @Query("SELECT DISTINCT a FROM Application a LEFT JOIN FETCH a.tags t WHERE t = :tagName")
    List<Application> findByTag(@Param("tagName") String tagName);

    long countByApplicantId(UUID applicantId);

    long countByProductId(UUID productId);
}