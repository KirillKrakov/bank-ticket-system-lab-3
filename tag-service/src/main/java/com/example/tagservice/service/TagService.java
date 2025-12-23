package com.example.tagservice.service;

import com.example.tagservice.dto.ApplicationInfoDto;
import com.example.tagservice.dto.TagDto;
import com.example.tagservice.exception.NotFoundException;
import com.example.tagservice.exception.ServiceUnavailableException;
import com.example.tagservice.feign.ApplicationServiceClient;
import com.example.tagservice.model.entity.Tag;
import com.example.tagservice.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TagService {

    private static final Logger log = LoggerFactory.getLogger(TagService.class);
    private final TagRepository tagRepository;
    private final ApplicationServiceClient applicationServiceClient;

    public TagService(TagRepository tagRepository, ApplicationServiceClient applicationServiceClient) {
        this.tagRepository = tagRepository;
        this.applicationServiceClient = applicationServiceClient;
    }

    @Transactional
    public Tag createIfNotExists(String name) {
        return tagRepository.findByName(name)
                .orElseGet(() -> {
                    Tag tag = new Tag();
                    tag.setId(UUID.randomUUID());
                    tag.setName(name.trim());
                    Tag saved = tagRepository.save(tag);
                    log.info("Created new tag: {}", name);
                    return saved;
                });
    }

    @Transactional
    public List<Tag> createOrGetTags(List<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return Collections.emptyList();
        }

        // Очистка и уникализация имён
        List<String> uniqueNames = tagNames.stream()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // Поиск существующих тегов
        List<Tag> existingTags = tagRepository.findByNames(uniqueNames);
        Set<String> existingNames = existingTags.stream()
                .map(Tag::getName)
                .collect(Collectors.toSet());

        // Создание новых тегов
        List<Tag> newTags = uniqueNames.stream()
                .filter(name -> !existingNames.contains(name))
                .map(name -> {
                    Tag tag = new Tag();
                    tag.setId(UUID.randomUUID());
                    tag.setName(name);
                    return tag;
                })
                .collect(Collectors.toList());

        if (!newTags.isEmpty()) {
            List<Tag> savedTags = tagRepository.saveAll(newTags);
            log.info("Created {} new tags", savedTags.size());
            existingTags.addAll(savedTags);
        }

        return existingTags;
    }

    @Transactional(readOnly = true)
    public Page<TagDto> listAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Tag> tags = tagRepository.findAll(pageable);

        return tags.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public TagDto getTagByName(String name) {
        Tag tag = tagRepository.findByName(name)
                .orElseThrow(() -> new NotFoundException("Tag not found: " + name));
        return toDto(tag);
    }

    private TagDto toDto(Tag tag) {
        List<ApplicationInfoDto> applications = applicationServiceClient.getApplicationsByTag(tag.getName());
        if (applications == null) {
            throw new ServiceUnavailableException("Application service is unavailable now");
        }
        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setName(tag.getName());
        dto.setApplications(applications);
        return dto;
    }
}