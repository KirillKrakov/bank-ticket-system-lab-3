package com.example.assignmentservice.controller;

import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.dto.UserProductAssignmentRequest;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.service.UserProductAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "User-Products Assignments", description = "API for managing assignments between users and products")
@RestController
@RequestMapping("/api/v1/assignments")
public class UserProductAssignmentController {

    private final UserProductAssignmentService service;

    @Autowired
    public UserProductAssignmentController(UserProductAssignmentService service) {
        this.service = service;
    }

    @Operation(summary = "Create a new user-product assignment",
            description = "Registers a new assignment between user and product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Assignment created successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient rights"),
            @ApiResponse(responseCode = "404", description = "User, product or actor not found"),
            @ApiResponse(responseCode = "503", description = "User or product service is unavailable now")
    })
    @PostMapping
    public ResponseEntity<UserProductAssignmentDto> assign(
            @Valid @RequestBody UserProductAssignmentRequest req,
            @RequestParam("actorId") UUID actorId,
            UriComponentsBuilder uriBuilder) {

        var assignment = service.assign(actorId, req.getUserId(), req.getProductId(), req.getRole());
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
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized"),
            @ApiResponse(responseCode = "403", description = "Insufficient rights"),
            @ApiResponse(responseCode = "404", description = "User or product not found"),
            @ApiResponse(responseCode = "503", description = "User or product service is unavailable now")
    })
    @DeleteMapping
    public ResponseEntity<Void> deleteAssignments(
            @RequestParam UUID actorId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId) {

        service.deleteAssignments(actorId, userId, productId);
        return ResponseEntity.noContent().build();
    }
}