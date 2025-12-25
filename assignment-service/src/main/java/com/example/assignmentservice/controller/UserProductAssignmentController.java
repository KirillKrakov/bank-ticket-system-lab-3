package com.example.assignmentservice.controller;

import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.dto.UserProductAssignmentRequest;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.service.UserProductAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "User-Products Assignments", description = "API for managing assignments between users and products")
@RestController
@RequestMapping("/api/v1/assignments")
public class UserProductAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(UserProductAssignmentController.class);
    private final UserProductAssignmentService service;

    public UserProductAssignmentController(UserProductAssignmentService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new user-product assignment",
            description = "Registers a new assignment between user and product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assignment created successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Insufficient rights"),
            @ApiResponse(responseCode = "404", description = "User, product or actor not found"),
            @ApiResponse(responseCode = "503", description = "User or product service is unavailable now")
    })
    @PostMapping
    public ResponseEntity<UserProductAssignmentDto> assign(
            @Valid @RequestBody UserProductAssignmentRequest req,
            @AuthenticationPrincipal Jwt jwt,
            UriComponentsBuilder uriBuilder) {

        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);
        String actorRole = jwt.getClaimAsString("role"); // may be null; service will fallback if necessary

        var assignment = service.assign(actorId, actorRole, req.getUserId(), req.getProductId(), req.getRole());
        UserProductAssignmentDto dto = service.toDto(assignment);

        URI location = uriBuilder.path("/api/v1/assignments/{id}")
                .buildAndExpand(dto.getId())
                .toUri();

        return ResponseEntity.created(location).body(dto);
    }

    @Operation(summary = "Read all user-product assignments",
            description = "Returns list of assignments")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of assignments"),
    })
    @GetMapping
    public ResponseEntity<List<UserProductAssignmentDto>> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId) {

        List<UserProductAssignmentDto> list = service.list(userId, productId);
        return ResponseEntity.ok(list);
    }

    @Operation(summary = "Check if assignment exists",
            description = "Checks if a specific assignment exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assignment exists or not"),
            @ApiResponse(responseCode = "400", description = "Bad request")
    })
    @GetMapping("/exists")
    public ResponseEntity<Boolean> exists(
            @RequestParam UUID userId,
            @RequestParam UUID productId,
            @RequestParam String role) {

        AssignmentRole assignmentRole;
        try {
            assignmentRole = AssignmentRole.valueOf(role);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(false);
        }

        boolean exists = service.existsByUserAndProductAndRole(userId, productId, assignmentRole);
        return ResponseEntity.ok(exists);
    }

    @Operation(summary = "Delete assignments",
            description = "Deletes assignments based on criteria")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Assignments deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Insufficient rights"),
            @ApiResponse(responseCode = "404", description = "User or product not found"),
            @ApiResponse(responseCode = "503", description = "User or product service is unavailable now")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteAssignments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId) {

        if (jwt == null) {
            return ResponseEntity.status(401).build();
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) uid = jwt.getSubject();
        UUID actorId = UUID.fromString(uid);
        String actorRole = jwt.getClaimAsString("role");

        service.deleteAssignments(actorId, actorRole, userId, productId);
        return ResponseEntity.noContent().build();
    }
}