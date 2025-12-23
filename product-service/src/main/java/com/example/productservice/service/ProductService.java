package com.example.productservice.service;

import com.example.productservice.dto.ProductDto;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.exception.*;
import com.example.productservice.feign.ApplicationServiceClient;
import com.example.productservice.feign.AssignmentServiceClient;
import com.example.productservice.feign.UserServiceClient;
import com.example.productservice.model.entity.Product;
import com.example.productservice.model.enums.AssignmentRole;
import com.example.productservice.model.enums.UserRole;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.auth.AuthUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger logger = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;
    private final UserServiceClient userServiceClient; // left for other usages (not used for role checks anymore)
    private final ApplicationServiceClient applicationServiceClient;
    private final AssignmentServiceClient assignmentServiceClient;

    @Autowired
    public ProductService(
            ProductRepository productRepository,
            UserServiceClient userServiceClient,
            ApplicationServiceClient applicationServiceClient,
            AssignmentServiceClient assignmentServiceClient) {
        this.productRepository = productRepository;
        this.userServiceClient = userServiceClient;
        this.applicationServiceClient = applicationServiceClient;
        this.assignmentServiceClient = assignmentServiceClient;
    }

    @Transactional
    public ProductDto create(ProductRequest req) {
        if (req == null) {
            throw new BadRequestException("Request is required");
        }

        String name = req.getName();
        String description = req.getDescription();

        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("Product name must be in request body and not empty");
        }

        if (description == null || description.trim().isEmpty()) {
            throw new BadRequestException("Product description must be in request body and not empty");
        }

        String trimmedName = name.trim();

        if (productRepository.existsByName(trimmedName)) {
            throw new ConflictException("Product name already in use");
        }

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName(trimmedName);
        product.setDescription(description.trim());

        product = productRepository.save(product);
        logger.info("Product created: {}", product.getId());

        return toDto(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductDto> list(int page, int size) {
        if (size > 50) {
            throw new BadRequestException("Page size cannot exceed 50");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> products = productRepository.findAll(pageable);

        return products.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public ProductDto get(UUID id) {
        return productRepository.findById(id)
                .map(this::toDto)
                .orElse(null);
    }

    /**
     * Update product. Authorization: only ROLE_ADMIN or PRODUCT_OWNER may update.
     * Actor info is derived from JWT (AuthUtils.currentUserId and currentRoles).
     */
    @Transactional
    public ProductDto updateProduct(UUID productId, ProductRequest req) {
        if (req == null) {
            throw new BadRequestException("Request is required");
        }

        UUID actorId = AuthUtils.currentUserId().orElseThrow(() -> new UnauthorizedException("Unauthorized"));
        boolean isAdmin = AuthUtils.hasRole("ROLE_ADMIN");

        // Check existence of product
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        // Check ownership via assignment service if not admin
        if (!isAdmin) {
            Boolean isOwner = assignmentServiceClient.existsByUserAndProductAndRole(
                    actorId, productId, AssignmentRole.PRODUCT_OWNER.name()
            );
            if (isOwner == null) {
                throw new ServiceUnavailableException("Assignment service is unavailable now");
            }
            if (!isOwner) {
                throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can update product");
            }
        }

        if (req.getName() != null && !req.getName().trim().isEmpty()) {
            String newName = req.getName().trim();
            if (!newName.equals(product.getName()) && productRepository.existsByName(newName)) {
                throw new ConflictException("Product name already in use");
            }
            product.setName(newName);
        }

        if (req.getDescription() != null) {
            product.setDescription(req.getDescription().trim());
        }

        Product saved = productRepository.save(product);
        logger.info("Product updated: {}", productId);

        return toDto(saved);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        UUID actorId = AuthUtils.currentUserId().orElseThrow(() -> new UnauthorizedException("Unauthorized"));
        boolean isAdmin = AuthUtils.hasRole("ROLE_ADMIN");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found: " + productId));

        if (!isAdmin) {
            Boolean isOwner = assignmentServiceClient.existsByUserAndProductAndRole(
                    actorId, productId, AssignmentRole.PRODUCT_OWNER.name()
            );
            if (isOwner == null) {
                throw new ServiceUnavailableException("Assignment service is unavailable now");
            }
            if (!isOwner) {
                throw new ForbiddenException("Only ADMIN or PRODUCT_OWNER can delete product");
            }
        }

        try {
            // Delete dependent applications via application service (propagated Authorization header)
            applicationServiceClient.deleteApplicationsByProductId(productId);
            logger.info("Applications deleted for product: {}", productId);

            // Delete product itself
            productRepository.delete(product);
            logger.info("Product deleted: {}", productId);

        } catch (Exception ex) {
            logger.error("Failed to delete product and its applications: {}", ex.getMessage(), ex);
            throw new ConflictException("Failed to delete product and its applications: " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(UUID id) {
        return productRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public boolean existsById(UUID id) {
        return productRepository.existsById(id);
    }

    private ProductDto toDto(Product product) {
        ProductDto dto = new ProductDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        return dto;
    }
}