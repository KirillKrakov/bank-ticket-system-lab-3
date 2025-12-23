package com.example.productservice.service;

import com.example.productservice.dto.ProductDto;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.exception.BadRequestException;
import com.example.productservice.exception.ConflictException;
import com.example.productservice.exception.ForbiddenException;
import com.example.productservice.exception.NotFoundException;
import com.example.productservice.feign.ApplicationServiceClient;
import com.example.productservice.feign.AssignmentServiceClient;
import com.example.productservice.feign.UserServiceClient;
import com.example.productservice.model.entity.Product;
import com.example.productservice.model.enums.AssignmentRole;
import com.example.productservice.model.enums.UserRole;
import com.example.productservice.repository.ProductRepository;
import com.example.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @Mock
    private AssignmentServiceClient assignmentServiceClient;

    private ProductService productService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(
                productRepository,
                userServiceClient,
                applicationServiceClient,
                assignmentServiceClient
        );
    }

    // -----------------------
    // createProduct tests
    // -----------------------
    @Test
    public void create_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> productService.create(null));
        verifyNoInteractions(productRepository);
    }

    @Test
    public void create_missingName_throwsBadRequest() {
        ProductRequest req = new ProductRequest();
        req.setDescription("desc");
        assertThrows(BadRequestException.class, () -> productService.create(req));
        verifyNoInteractions(productRepository);
    }

    @Test
    public void create_missingDescription_throwsBadRequest() {
        ProductRequest req = new ProductRequest();
        req.setName("name");
        assertThrows(BadRequestException.class, () -> productService.create(req));
        verifyNoInteractions(productRepository);
    }

    @Test
    public void create_nameAlreadyExists_throwsConflict() {
        ProductRequest req = new ProductRequest();
        req.setName("product");
        req.setDescription("desc");

        when(productRepository.existsByName("product")).thenReturn(true);

        assertThrows(ConflictException.class, () -> productService.create(req));

        verify(productRepository, times(1)).existsByName("product");
        verify(productRepository, never()).save(any());
    }

    @Test
    public void create_success_returnsDto() {
        ProductRequest req = new ProductRequest();
        req.setName("product");
        req.setDescription("desc");

        Product saved = new Product();
        UUID id = UUID.randomUUID();
        saved.setId(id);
        saved.setName("product");
        saved.setDescription("desc");

        when(productRepository.existsByName("product")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.create(req);

        assertNotNull(resp);
        assertEquals(id, resp.getId());
        assertEquals("product", resp.getName());
        assertEquals("desc", resp.getDescription());

        verify(productRepository, times(1)).existsByName("product");
        verify(productRepository, times(1)).save(any(Product.class));
    }

    // -----------------------
    // list tests
    // -----------------------
    @Test
    public void list_returnsPagedDto() {
        Product p1 = new Product();
        p1.setId(UUID.randomUUID());
        p1.setName("p1");
        p1.setDescription("d1");

        Product p2 = new Product();
        p2.setId(UUID.randomUUID());
        p2.setName("p2");
        p2.setDescription("d2");

        List<Product> list = List.of(p1, p2);
        Page<Product> page = new PageImpl<>(list);

        when(productRepository.findAll(PageRequest.of(0, 10))).thenReturn(page);

        Page<ProductDto> resp = productService.list(0, 10);

        assertEquals(2, resp.getTotalElements());
        assertEquals("p1", resp.getContent().get(0).getName());
        verify(productRepository, times(1)).findAll(PageRequest.of(0, 10));
    }

    // -----------------------
    // getProduct tests
    // -----------------------
    @Test
    public void get_whenNotFound_returnsNull() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        ProductDto resp = productService.get(id);
        assertNull(resp);
        verify(productRepository, times(1)).findById(id);
    }

    @Test
    public void get_whenFound_returnsDto() {
        UUID id = UUID.randomUUID();
        Product p = new Product();
        p.setId(id);
        p.setName("name");
        p.setDescription("desc");

        when(productRepository.findById(id)).thenReturn(Optional.of(p));

        ProductDto resp = productService.get(id);
        assertNotNull(resp);
        assertEquals(id, resp.getId());
        assertEquals("name", resp.getName());
    }

    // -----------------------
    // updateProduct tests
    // -----------------------
    @Test
    public void updateProduct_nullRequest_throwsBadRequest() {
        assertThrows(BadRequestException.class, () ->
                productService.updateProduct(UUID.randomUUID(), null, UUID.randomUUID()));
    }

    @Test
    public void updateProduct_nullActorId_throwsUnauthorized() {
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        Exception exception = assertThrows(Exception.class, () ->
                productService.updateProduct(productId, req, null));

        assertTrue(exception.getMessage().contains("actorId"));
    }

    @Test
    public void updateProduct_actorNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        when(userServiceClient.userExists(actorId)).thenReturn(false);

        assertThrows(NotFoundException.class, () ->
                productService.updateProduct(productId, req, actorId));
        verify(userServiceClient, times(2)).userExists(actorId);
    }

    @Test
    public void updateProduct_productNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                productService.updateProduct(productId, req, actorId));
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    public void updateProduct_actorNotAdminNorOwner_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        Product product = new Product();
        product.setId(productId);
        product.setName("oldName");

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                productService.updateProduct(productId, req, actorId));

        verify(userServiceClient, times(1)).getUserRole(actorId);
        verify(assignmentServiceClient, times(1)).existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name());
    }

    @Test
    public void updateProduct_asAdmin_updatesAndReturnsDto() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");
        req.setDescription("newDesc");

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("old");
        existing.setDescription("oldDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("newName");
        saved.setDescription("newDesc");

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(false);
        when(productRepository.existsByName("newName")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId);

        assertNotNull(resp);
        assertEquals("newName", resp.getName());
        assertEquals("newDesc", resp.getDescription());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    public void updateProduct_asOwner_updatesAndReturnsDto() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("ownerName");
        req.setDescription("ownerDesc");

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("old");
        existing.setDescription("oldDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("ownerName");
        saved.setDescription("ownerDesc");

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(true);
        when(productRepository.existsByName("ownerName")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId);

        assertNotNull(resp);
        assertEquals("ownerName", resp.getName());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    public void updateProduct_nameConflict_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("existingName");
        req.setDescription("desc");

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("oldName");
        existing.setDescription("oldDesc");

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(false);
        when(productRepository.existsByName("existingName")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                productService.updateProduct(productId, req, actorId));

        verify(productRepository, times(1)).existsByName("existingName");
        verify(productRepository, never()).save(any());
    }

    // -----------------------
    // deleteProduct tests
    // -----------------------
    @Test
    public void deleteProduct_nullActorId_throwsUnauthorized() {
        UUID productId = UUID.randomUUID();

        Exception exception = assertThrows(Exception.class, () ->
                productService.deleteProduct(productId, null));

        assertTrue(exception.getMessage().contains("actorId"));
    }

    @Test
    public void deleteProduct_actorNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(userServiceClient.userExists(actorId)).thenReturn(false);

        assertThrows(NotFoundException.class, () ->
                productService.deleteProduct(productId, actorId));
        verify(userServiceClient, times(2)).userExists(actorId);
    }

    @Test
    public void deleteProduct_productNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                productService.deleteProduct(productId, actorId));
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    public void deleteProduct_actorNotAdminNorOwner_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                productService.deleteProduct(productId, actorId));

        verify(assignmentServiceClient, times(1)).existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name());
    }

    @Test
    public void deleteProduct_success_deletesApplicationsAndProduct() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(false);

        productService.deleteProduct(productId, actorId);

        // Проверяем вызовы к внешним сервисам
        verify(applicationServiceClient, times(1)).deleteApplicationsByProductId(productId);
        verify(productRepository, times(1)).delete(product);
    }

    @Test
    public void deleteProduct_applicationServiceThrows_exceptionWrappedAsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        when(userServiceClient.userExists(actorId)).thenReturn(true);
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, AssignmentRole.PRODUCT_OWNER.name()))
                .thenReturn(false);

        // Симулируем ошибку при вызове Feign-клиента
        doThrow(new RuntimeException("Feign error")).when(applicationServiceClient)
                .deleteApplicationsByProductId(productId);

        // В текущей реализации ProductService оборачивает исключение в ConflictException
        assertThrows(ConflictException.class, () ->
                productService.deleteProduct(productId, actorId));
    }

    // -----------------------
    // existsById tests
    // -----------------------
    @Test
    public void existsById_whenExists_returnsTrue() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(true);

        assertTrue(productService.existsById(id));
        verify(productRepository, times(1)).existsById(id);
    }

    @Test
    public void existsById_whenNotExists_returnsFalse() {
        UUID id = UUID.randomUUID();
        when(productRepository.existsById(id)).thenReturn(false);

        assertFalse(productService.existsById(id));
        verify(productRepository, times(1)).existsById(id);
    }
}