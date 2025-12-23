package com.example.applicationservice.controller;

import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.service.ApplicationService;
import com.example.applicationservice.util.ApplicationPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Applications", description = "API for managing applications")
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);
    private static final int MAX_PAGE_SIZE = 50;
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // Create: POST "/api/v1/applications"
    @Operation(summary = "Create a new application", description = "Registers a new application: applicantId, productId, documents, tags")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Application created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Applicant or product not found"),
            @ApiResponse(responseCode = "409", description = "Failed to process tags"),
            @ApiResponse(responseCode = "503", description = "User or product service is unavailable now")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApplicationDto> createApplication(
            @Valid @RequestBody ApplicationRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Creating new application for applicant: {}, product: {} (auth principal {})",
                request.getApplicantId(), request.getProductId(), jwt != null ? jwt.getSubject() : "anonymous");

        // Extract actor info
        if (jwt == null) {
            return Mono.error(new UnauthorizedException("Authentication required"));
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);

        // derive role from token
        String roleStr = jwt.getClaimAsString("role");
        // roles claim might be a list - ApplicationService will accept roleStr or null
        return applicationService.createApplication(request, actorId, roleStr);
    }

    // ReadAll
    @GetMapping
    public Flux<ApplicationDto> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size > MAX_PAGE_SIZE) {
            return Flux.error(new BadRequestException(String.format("Page size cannot be greater than %d", MAX_PAGE_SIZE)));
        }
        return applicationService.findAll(page, size);
    }

    // Read
    @GetMapping("/{id}")
    public Mono<ApplicationDto> getApplication(@PathVariable UUID id) {
        log.debug("Getting application: {}", id);
        return applicationService.findById(id);
    }

    // Stream
    @GetMapping("/stream")
    public Mono<ApplicationPage> streamApplications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        if (limit > MAX_PAGE_SIZE) {
            return Mono.error(new BadRequestException(String.format("Limit cannot be greater than %d", MAX_PAGE_SIZE)));
        }
        log.debug("Streaming applications - cursor: {}, limit: {}", cursor, limit);
        return applicationService.streamWithNextCursor(cursor, limit);
    }

    // Add tags
    @PutMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> addTags(
            @PathVariable UUID id,
            @RequestBody List<String> tags,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Adding tags to application {} (auth principal {})", id, jwt != null ? jwt.getSubject() : "anonymous");

        if (jwt == null) {
            return Mono.error(new UnauthorizedException("Authentication required"));
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);
        String roleStr = jwt.getClaimAsString("role");

        return applicationService.attachTags(id, tags, actorId, roleStr);
    }

    // Remove tags
    @DeleteMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> removeTags(
            @PathVariable UUID id,
            @RequestBody List<String> tags,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Removing tags from application {} (auth principal {})", id, jwt != null ? jwt.getSubject() : "anonymous");

        if (jwt == null) {
            return Mono.error(new UnauthorizedException("Authentication required"));
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);
        String roleStr = jwt.getClaimAsString("role");

        return applicationService.removeTags(id, tags, actorId, roleStr);
    }

    // Change status
    @PutMapping("/{id}/status")
    public Mono<ApplicationDto> changeStatus(
            @PathVariable UUID id,
            @RequestBody String status,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Changing status of application {} to '{}' (auth principal {})", id, status, jwt != null ? jwt.getSubject() : "anonymous");

        if (jwt == null) {
            return Mono.error(new UnauthorizedException("Authentication required"));
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);
        String roleStr = jwt.getClaimAsString("role");

        return applicationService.changeStatus(id, status, actorId, roleStr);
    }

    // Delete application
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteApplication(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Deleting application {} (auth principal {})", id, jwt != null ? jwt.getSubject() : "anonymous");

        if (jwt == null) {
            return Mono.error(new UnauthorizedException("Authentication required"));
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);

        return applicationService.deleteApplication(id, actorId, jwt.getClaimAsString("role"));
    }

    // Get history
    @GetMapping("/{id}/history")
    public Flux<ApplicationHistoryDto> getApplicationHistory(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        log.debug("Getting history for application {} (auth principal {})", id, jwt != null ? jwt.getSubject() : "anonymous");

        if (jwt == null) {
            return Flux.error(new UnauthorizedException("Authentication required"));
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);

        return applicationService.listHistory(id, actorId, jwt.getClaimAsString("role"));
    }

    // Internal endpoints - keep as before (internal calls)
    @DeleteMapping("/internal/by-user")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteApplicationsByUserId(@RequestParam("userId") UUID userId) {
        log.info("Deleting all applications for user {} (internal call)", userId);
        return applicationService.deleteApplicationsByUserId(userId);
    }

    @DeleteMapping("/internal/by-product")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteApplicationsByProductId(@RequestParam("productId") UUID productId) {
        log.info("Deleting all applications for product {} (internal call)", productId);
        return applicationService.deleteApplicationsByProductId(productId);
    }

    @GetMapping("/by-tag")
    public Mono<List<ApplicationInfoDto>> getApplicationsByTag(@RequestParam("tag") String tagName) {
        log.debug("Getting applications with tag: {}", tagName);
        return applicationService.findApplicationsByTag(tagName);
    }
}