package com.example.tagservice.repository;

import com.example.tagservice.model.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);

    @Query("SELECT t FROM Tag t WHERE t.name IN :names")
    List<Tag> findByNames(@Param("names") List<String> names);

    boolean existsByName(String name);
}