package com.example.tagservice.controller;

import com.example.tagservice.dto.ApplicationInfoDto;
import com.example.tagservice.dto.TagDto;
import com.example.tagservice.feign.ApplicationServiceClient;
import com.example.tagservice.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Tags", description = "API for managing tags")
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private static final Logger log = LoggerFactory.getLogger(TagController.class);
    private static final int MAX_PAGE_SIZE = 50;
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(summary = "Create a new  unique tag", description = "Registers a new tag: name if it has not already existed")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Tag created or found successfully")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TagDto> createTag(
            @Valid @RequestBody String name,
            UriComponentsBuilder uriBuilder) {

        name = name.trim();
        var tag = tagService.createIfNotExists(name);

        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setName(tag.getName());

        URI location = uriBuilder.path("/api/v1/tags/{name}")
                .buildAndExpand(dto.getName())
                .toUri();

        log.info("Tag created or retrieved: {}", name);
        return ResponseEntity.created(location).body(dto);
    }

    @Operation(summary = "Read all tags", description = "Returns list of tags")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
    @GetMapping
    public ResponseEntity<List<TagDto>> listTags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("Page size cannot be greater than %d", MAX_PAGE_SIZE));
        }

        Page<TagDto> tagPage = tagService.listAll(page, size);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total-Count", String.valueOf(tagPage.getTotalElements()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(tagPage.getContent());
    }

    @Operation(summary = "Read certain tag by its name", description = "Returns data about a single tag: name and list of applications that uses this tag")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data about a single tag"),
            @ApiResponse(responseCode = "404", description = "Tag with this name is not found")
    })
    @GetMapping("/{name}")
    public ResponseEntity<TagDto> getTagWithApplications(@PathVariable String name) {
        TagDto response = tagService.getTagByName(name);
        log.info("Returning tag {} with {} applications", name, response.getApplications().size());
        return ResponseEntity.ok(response);
    }

    // internal-запрос для application-service
    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<List<TagDto>> createOrGetTagsBatch(
            @Valid @RequestBody List<String> tagNames) {

        if (tagNames == null || tagNames.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<com.example.tagservice.model.entity.Tag> tags =
                tagService.createOrGetTags(tagNames);

        List<TagDto> dtos = tags.stream()
                .map(tag -> {
                    TagDto dto = new TagDto();
                    dto.setId(tag.getId());
                    dto.setName(tag.getName());
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("Processed batch of {} tags", dtos.size());
        return ResponseEntity.ok(dtos);
    }
}