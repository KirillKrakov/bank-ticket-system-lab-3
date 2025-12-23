package com.example.productservice.controller;

import com.example.productservice.dto.ProductDto;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.exception.BadRequestException;
import com.example.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Products", description = "API for managing products")
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final ProductService productService;

    @Autowired
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "Create a new product", description = "Registers a new product: name, description")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Product created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "409", description = "Product name already in use")
    })
    @PostMapping
    public ResponseEntity<ProductDto> create(@Valid @RequestBody ProductRequest req, UriComponentsBuilder uriBuilder) {
        ProductDto dto = productService.create(req);
        URI location = uriBuilder.path("/api/v1/products/{id}").buildAndExpand(dto.getId()).toUri();
        logger.info("Product created with ID: {}", dto.getId());
        return ResponseEntity.created(location).body(dto);
    }

    @Operation(summary = "Read all products with pagination", description = "Returns a paginated list of products")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of products"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
    @GetMapping
    public ResponseEntity<List<ProductDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletResponse response) {

        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }

        Page<ProductDto> products = productService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(products.getTotalElements()));

        logger.debug("Returning {} products from page {}", products.getContent().size(), page);
        return ResponseEntity.ok(products.getContent());
    }

    @Operation(summary = "Read certain product by its ID", description = "Returns data about a single product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data about a single product"),
            @ApiResponse(responseCode = "404", description = "Product with this ID is not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ProductDto> get(@PathVariable UUID id) {
        ProductDto productDto = productService.get(id);
        if (productDto == null) {
            logger.debug("Product not found: {}", id);
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(productDto);
    }

    @Operation(summary = "Update the data of a specific product", description = "Update any data of single product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Product updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized"),
            @ApiResponse(responseCode = "403", description = "Insufficient rights"),
            @ApiResponse(responseCode = "404", description = "Product or actor not found"),
            @ApiResponse(responseCode = "409", description = "Product name already in use"),
            @ApiResponse(responseCode = "503", description = "User service is unavailable")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable("id") UUID id,
            @Valid @RequestBody ProductRequest req,
            @RequestParam("actorId") UUID actorId) {

        logger.info("Updating product {} by actor {}", id, actorId);
        ProductDto dto = productService.updateProduct(id, req, actorId);
        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Delete a specific product", description = "Deletes one specific product from the database")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized"),
            @ApiResponse(responseCode = "403", description = "Insufficient rights"),
            @ApiResponse(responseCode = "404", description = "Product or actor not found"),
            @ApiResponse(responseCode = "409", description = "Error during deletion"),
            @ApiResponse(responseCode = "503", description = "User or application service is unavailable")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable("id") UUID id,
            @RequestParam("actorId") UUID actorId) {

        logger.info("Deleting product {} by actor {}", id, actorId);
        productService.deleteProduct(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // Internal endpoints для других сервисов
    @GetMapping("/{id}/exists")
    public ResponseEntity<Boolean> productExists(@PathVariable UUID id) {
        boolean exists = productService.existsById(id);
        return ResponseEntity.ok(exists);
    }
}