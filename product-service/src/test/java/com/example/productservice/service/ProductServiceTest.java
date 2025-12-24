package com.example.productservice.service;

import com.example.productservice.dto.ProductDto;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.exception.*;
import com.example.productservice.feign.ApplicationServiceClient;
import com.example.productservice.feign.AssignmentServiceClient;
import com.example.productservice.model.entity.Product;
import com.example.productservice.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @Mock
    private AssignmentServiceClient assignmentServiceClient;

    @Mock
    private Jwt jwt;

    private ProductService productService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        productService = new ProductService(
                productRepository,
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

    @Test
    public void create_trimmingNameAndDescription() {
        ProductRequest req = new ProductRequest();
        req.setName("  product  ");
        req.setDescription("  desc  ");

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

    @Test
    public void list_sizeExceeds50_throwsBadRequest() {
        assertThrows(BadRequestException.class, () -> productService.list(0, 51));
        verifyNoInteractions(productRepository);
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
        assertEquals("desc", resp.getDescription());
    }

    // -----------------------
    // updateProduct tests
    // -----------------------
    @Test
    public void updateProduct_nullRequest_throwsBadRequest() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        assertThrows(BadRequestException.class, () ->
                productService.updateProduct(productId, null, actorId, jwt));
    }

    @Test
    public void updateProduct_nullActorId_throwsUnauthorized() {
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        assertThrows(UnauthorizedException.class, () ->
                productService.updateProduct(productId, req, null, jwt));
    }

    @Test
    public void updateProduct_productNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                productService.updateProduct(productId, req, actorId, jwt));
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

        // JWT не содержит роль ADMIN
        when(jwt.getClaims()).thenReturn(java.util.Map.of());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                productService.updateProduct(productId, req, actorId, jwt));

        verify(assignmentServiceClient, times(1)).existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER");
    }

    @Test
    public void updateProduct_assignmentServiceUnavailable_throwsServiceUnavailable() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");

        Product product = new Product();
        product.setId(productId);
        product.setName("oldName");

        when(jwt.getClaims()).thenReturn(java.util.Map.of());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(null); // Сервис недоступен

        assertThrows(ServiceUnavailableException.class, () ->
                productService.updateProduct(productId, req, actorId, jwt));
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

        // JWT содержит роль ADMIN
        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);
        when(productRepository.existsByName("newName")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId, jwt);

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

        // JWT не содержит роль ADMIN, но пользователь является владельцем
        when(jwt.getClaims()).thenReturn(java.util.Map.of());
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(true);
        when(productRepository.existsByName("ownerName")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId, jwt);

        assertNotNull(resp);
        assertEquals("ownerName", resp.getName());
        assertEquals("ownerDesc", resp.getDescription());
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

        // JWT содержит роль ADMIN
        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);
        when(productRepository.existsByName("existingName")).thenReturn(true);

        assertThrows(ConflictException.class, () ->
                productService.updateProduct(productId, req, actorId, jwt));

        verify(productRepository, times(1)).existsByName("existingName");
        verify(productRepository, never()).save(any());
    }

    @Test
    public void updateProduct_partialUpdate_onlyName() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("newName");
        // description не установлен

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("oldName");
        existing.setDescription("oldDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("newName");
        saved.setDescription("oldDesc"); // description остался прежним

        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);
        when(productRepository.existsByName("newName")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId, jwt);

        assertNotNull(resp);
        assertEquals("newName", resp.getName());
        assertEquals("oldDesc", resp.getDescription());
    }

    @Test
    public void updateProduct_partialUpdate_onlyDescription() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setDescription("newDesc");
        // name не установлен

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("oldName");
        existing.setDescription("oldDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("oldName"); // name остался прежним
        saved.setDescription("newDesc");

        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId, jwt);

        assertNotNull(resp);
        assertEquals("oldName", resp.getName());
        assertEquals("newDesc", resp.getDescription());
    }

    @Test
    public void updateProduct_sameName_noConflictCheck() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        ProductRequest req = new ProductRequest();
        req.setName("sameName");
        req.setDescription("newDesc");

        Product existing = new Product();
        existing.setId(productId);
        existing.setName("sameName");
        existing.setDescription("oldDesc");

        Product saved = new Product();
        saved.setId(productId);
        saved.setName("sameName");
        saved.setDescription("newDesc");

        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(existing));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);
        // existsByName не должен вызываться, так как имя не изменилось
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductDto resp = productService.updateProduct(productId, req, actorId, jwt);

        assertNotNull(resp);
        assertEquals("sameName", resp.getName());
        assertEquals("newDesc", resp.getDescription());
        verify(productRepository, never()).existsByName(anyString());
    }

    // -----------------------
    // deleteProduct tests
    // -----------------------
    @Test
    public void deleteProduct_nullActorId_throwsUnauthorized() {
        UUID productId = UUID.randomUUID();

        assertThrows(UnauthorizedException.class, () ->
                productService.deleteProduct(productId, null, jwt));
    }

    @Test
    public void deleteProduct_productNotFound_throwsNotFoundException() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                productService.deleteProduct(productId, actorId, jwt));
        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    public void deleteProduct_actorNotAdminNorOwner_throwsForbidden() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        // JWT не содержит роль ADMIN
        when(jwt.getClaims()).thenReturn(java.util.Map.of());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                productService.deleteProduct(productId, actorId, jwt));

        verify(assignmentServiceClient, times(1)).existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER");
    }

    @Test
    public void deleteProduct_assignmentServiceUnavailable_throwsServiceUnavailable() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        when(jwt.getClaims()).thenReturn(java.util.Map.of());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(null); // Сервис недоступен

        assertThrows(ServiceUnavailableException.class, () ->
                productService.deleteProduct(productId, actorId, jwt));
    }

    @Test
    public void deleteProduct_success_deletesApplicationsAndProduct() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        // JWT содержит роль ADMIN
        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);

        productService.deleteProduct(productId, actorId, jwt);

        // Проверяем вызовы к внешним сервисам
        verify(applicationServiceClient, times(1)).deleteApplicationsByProductId(productId);
        verify(productRepository, times(1)).delete(product);
    }

    @Test
    public void deleteProduct_asOwner_success_deletesApplicationsAndProduct() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        // JWT не содержит роль ADMIN, но пользователь является владельцем
        when(jwt.getClaims()).thenReturn(java.util.Map.of());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(true);

        productService.deleteProduct(productId, actorId, jwt);

        verify(applicationServiceClient, times(1)).deleteApplicationsByProductId(productId);
        verify(productRepository, times(1)).delete(product);
    }

    @Test
    public void deleteProduct_applicationServiceUnavailable_throwsServiceUnavailable() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);

        // Симулируем ошибку сервиса приложений
        doThrow(ServiceUnavailableException.class).when(applicationServiceClient)
                .deleteApplicationsByProductId(productId);

        assertThrows(ServiceUnavailableException.class, () ->
                productService.deleteProduct(productId, actorId, jwt));
    }

    @Test
    public void deleteProduct_generalException_throwsConflict() {
        UUID actorId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);

        when(jwt.getClaims()).thenReturn(java.util.Map.of("role", "ROLE_ADMIN"));
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(assignmentServiceClient.existsByUserAndProductAndRole(
                actorId, productId, "PRODUCT_OWNER"))
                .thenReturn(false);

        // Симулируем общее исключение при вызове Feign-клиента
        doThrow(new RuntimeException("General error")).when(applicationServiceClient)
                .deleteApplicationsByProductId(productId);

        assertThrows(ConflictException.class, () ->
                productService.deleteProduct(productId, actorId, jwt));
    }

    // -----------------------
    // findById tests
    // -----------------------
    @Test
    public void findById_whenFound_returnsOptional() {
        UUID id = UUID.randomUUID();
        Product product = new Product();
        product.setId(id);

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        Optional<Product> result = productService.findById(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    public void findById_whenNotFound_returnsEmptyOptional() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Product> result = productService.findById(id);
        assertFalse(result.isPresent());
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

    // -----------------------
    // toDto tests (через public методы)
    // -----------------------
    @Test
    public void toDto_mapsAllFieldsCorrectly() {
        Product product = new Product();
        UUID id = UUID.randomUUID();
        product.setId(id);
        product.setName("Test Product");
        product.setDescription("Test Description");

        // Используем метод get, который внутри вызывает toDto
        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        ProductDto dto = productService.get(id);

        assertNotNull(dto);
        assertEquals(id, dto.getId());
        assertEquals("Test Product", dto.getName());
        assertEquals("Test Description", dto.getDescription());
    }
}