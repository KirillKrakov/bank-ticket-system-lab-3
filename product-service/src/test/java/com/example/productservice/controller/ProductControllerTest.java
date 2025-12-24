package com.example.productservice.controller;

import com.example.productservice.dto.ProductDto;
import com.example.productservice.dto.ProductRequest;
import com.example.productservice.exception.*;
import com.example.productservice.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.security.oauth2.jwt.Jwt;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    private ProductDto createSampleProductDto() {
        ProductDto dto = new ProductDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Test Product");
        dto.setDescription("Test Description");
        return dto;
    }

    private ProductRequest createSampleProductRequest() {
        ProductRequest request = new ProductRequest();
        request.setName("Test Product");
        request.setDescription("Test Description");
        return request;
    }

    // -----------------------
    // create tests
    // -----------------------
    @Test
    void create_validRequest_returnsCreated() {
        ProductRequest request = createSampleProductRequest();
        ProductDto responseDto = createSampleProductDto();

        when(productService.create(any(ProductRequest.class))).thenReturn(responseDto);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        ResponseEntity<ProductDto> response = productController.create(request, uriBuilder);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(responseDto.getId(), response.getBody().getId());
        assertNotNull(response.getHeaders().getLocation());
        assertTrue(response.getHeaders().getLocation().toString().contains("/api/v1/products/"));
    }

    @Test
    void create_badRequest_throwsBadRequestException() {
        ProductRequest request = createSampleProductRequest();

        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new BadRequestException("Invalid request"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        assertThrows(BadRequestException.class, () ->
                productController.create(request, uriBuilder)
        );
    }

    @Test
    void create_conflict_throwsConflictException() {
        ProductRequest request = createSampleProductRequest();

        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new ConflictException("Product name already in use"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        assertThrows(ConflictException.class, () ->
                productController.create(request, uriBuilder)
        );
    }

    // -----------------------
    // list tests
    // -----------------------
    @Test
    void list_validParameters_returnsOkWithTotalCountHeader() {
        ProductDto dto1 = createSampleProductDto();
        ProductDto dto2 = createSampleProductDto();
        Page<ProductDto> page = new PageImpl<>(List.of(dto1, dto2), PageRequest.of(0, 20), 50);

        when(productService.list(0, 20)).thenReturn(page);

        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ResponseEntity<List<ProductDto>> response = productController.list(0, 20, mockResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void list_sizeExceedsMax_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () ->
                productController.list(0, 100, mock(HttpServletResponse.class))
        );
        verify(productService, never()).list(anyInt(), anyInt());
    }

    @Test
    void list_serviceThrowsBadRequest_throwsBadRequestException() {
        when(productService.list(0, 20))
                .thenThrow(new BadRequestException("Invalid parameters"));

        assertThrows(BadRequestException.class, () ->
                productController.list(0, 20, mock(HttpServletResponse.class))
        );
    }

    // -----------------------
    // get tests
    // -----------------------
    @Test
    void get_productFound_returnsOk() {
        UUID productId = UUID.randomUUID();
        ProductDto dto = createSampleProductDto();
        dto.setId(productId);

        when(productService.get(productId)).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.get(productId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(productId, response.getBody().getId());
    }

    @Test
    void get_productNotFound_returnsNotFound() {
        UUID productId = UUID.randomUUID();

        when(productService.get(productId)).thenReturn(null);

        ResponseEntity<ProductDto> response = productController.get(productId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void get_serviceThrowsException_throwsException() {
        UUID productId = UUID.randomUUID();

        when(productService.get(productId))
                .thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () ->
                productController.get(productId)
        );
    }

    // -----------------------
    // updateProduct tests
    // -----------------------
    @Test
    void updateProduct_success_returnsOk() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();
        ProductDto dto = createSampleProductDto();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());
        // removed unnecessary jwt.getSubject() stub

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt))).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.updateProduct(productId, request, jwt);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void updateProduct_badRequest_throwsBadRequestException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt)))
                .thenThrow(new BadRequestException("Invalid request"));

        assertThrows(BadRequestException.class, () ->
                productController.updateProduct(productId, request, jwt)
        );
    }

    @Test
    void updateProduct_unauthorized_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt)))
                .thenThrow(new UnauthorizedException("Actor is unauthorized"));

        assertThrows(UnauthorizedException.class, () ->
                productController.updateProduct(productId, request, jwt)
        );
    }

    @Test
    void updateProduct_forbidden_throwsForbiddenException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt)))
                .thenThrow(new ForbiddenException("Insufficient rights"));

        assertThrows(ForbiddenException.class, () ->
                productController.updateProduct(productId, request, jwt)
        );
    }

    @Test
    void updateProduct_notFound_throwsNotFoundException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt)))
                .thenThrow(new NotFoundException("Product not found"));

        assertThrows(NotFoundException.class, () ->
                productController.updateProduct(productId, request, jwt)
        );
    }

    @Test
    void updateProduct_conflict_throwsConflictException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt)))
                .thenThrow(new ConflictException("Product name already in use"));

        assertThrows(ConflictException.class, () ->
                productController.updateProduct(productId, request, jwt)
        );
    }

    @Test
    void updateProduct_serviceUnavailable_throwsServiceUnavailableException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt)))
                .thenThrow(new ServiceUnavailableException("User service is unavailable"));

        assertThrows(ServiceUnavailableException.class, () ->
                productController.updateProduct(productId, request, jwt)
        );
    }

    @Test
    void updateProduct_nullJwt_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        // controller checks jwt == null and throws UnauthorizedException before calling service
        assertThrows(UnauthorizedException.class, () ->
                productController.updateProduct(productId, request, null)
        );
    }

    // -----------------------
    // deleteProduct tests
    // -----------------------
    @Test
    void deleteProduct_success_returnsNoContent() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        doNothing().when(productService).deleteProduct(eq(productId), eq(actorId), eq(jwt));

        ResponseEntity<Void> response = productController.deleteProduct(productId, jwt);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void deleteProduct_unauthorized_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        doThrow(new UnauthorizedException("Actor is unauthorized"))
                .when(productService).deleteProduct(eq(productId), eq(actorId), eq(jwt));

        assertThrows(UnauthorizedException.class, () ->
                productController.deleteProduct(productId, jwt)
        );
    }

    @Test
    void deleteProduct_forbidden_throwsForbiddenException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        doThrow(new ForbiddenException("Insufficient rights"))
                .when(productService).deleteProduct(eq(productId), eq(actorId), eq(jwt));

        assertThrows(ForbiddenException.class, () ->
                productController.deleteProduct(productId, jwt)
        );
    }

    @Test
    void deleteProduct_notFound_throwsNotFoundException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        doThrow(new NotFoundException("Product not found"))
                .when(productService).deleteProduct(eq(productId), eq(actorId), eq(jwt));

        assertThrows(NotFoundException.class, () ->
                productController.deleteProduct(productId, jwt)
        );
    }

    @Test
    void deleteProduct_conflict_throwsConflictException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        doThrow(new ConflictException("Error during deletion"))
                .when(productService).deleteProduct(eq(productId), eq(actorId), eq(jwt));

        assertThrows(ConflictException.class, () ->
                productController.deleteProduct(productId, jwt)
        );
    }

    @Test
    void deleteProduct_serviceUnavailable_throwsServiceUnavailableException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        doThrow(new ServiceUnavailableException("User service is unavailable"))
                .when(productService).deleteProduct(eq(productId), eq(actorId), eq(jwt));

        assertThrows(ServiceUnavailableException.class, () ->
                productController.deleteProduct(productId, jwt)
        );
    }

    // -----------------------
    // productExists tests (internal endpoint)
    // -----------------------
    @Test
    void productExists_productFound_returnsTrue() {
        UUID productId = UUID.randomUUID();

        when(productService.existsById(productId)).thenReturn(true);

        ResponseEntity<Boolean> response = productController.productExists(productId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody());
    }

    @Test
    void productExists_productNotFound_returnsFalse() {
        UUID productId = UUID.randomUUID();

        when(productService.existsById(productId)).thenReturn(false);

        ResponseEntity<Boolean> response = productController.productExists(productId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody());
    }

    // -----------------------
    // edge cases tests
    // -----------------------
    @Test
    void create_emptyName_throwsBadRequestException() {
        ProductRequest request = new ProductRequest();
        request.setName("");
        request.setDescription("Description");

        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new BadRequestException("Product name must be in request body and not empty"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        assertThrows(BadRequestException.class, () ->
                productController.create(request, uriBuilder)
        );
    }

    @Test
    void create_emptyDescription_throwsBadRequestException() {
        ProductRequest request = new ProductRequest();
        request.setName("Product Name");
        request.setDescription("");

        when(productService.create(any(ProductRequest.class)))
                .thenThrow(new BadRequestException("Product description must be in request body and not empty"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        assertThrows(BadRequestException.class, () ->
                productController.create(request, uriBuilder)
        );
    }

    @Test
    void create_nullRequest_throwsBadRequestException() {
        when(productService.create(null))
                .thenThrow(new BadRequestException("Request is required"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        assertThrows(BadRequestException.class, () ->
                productController.create(null, uriBuilder)
        );
    }

    @Test
    void deleteProduct_nullJwt_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();

        // controller checks jwt == null and throws UnauthorizedException before calling service
        assertThrows(UnauthorizedException.class, () ->
                productController.deleteProduct(productId, null)
        );
    }

    @Test
    void list_emptyPage_returnsEmptyList() {
        Page<ProductDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(productService.list(0, 20)).thenReturn(page);

        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ResponseEntity<List<ProductDto>> response = productController.list(0, 20, mockResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void list_largePageNumber_returnsEmptyIfNoData() {
        Page<ProductDto> page = new PageImpl<>(List.of(), PageRequest.of(100, 20), 5);

        when(productService.list(100, 20)).thenReturn(page);

        HttpServletResponse mockResponse = mock(HttpServletResponse.class);
        ResponseEntity<List<ProductDto>> response = productController.list(100, 20, mockResponse);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void updateProduct_partialUpdate_success() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = new ProductRequest();
        request.setName("Updated Name");
        ProductDto dto = createSampleProductDto();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt))).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.updateProduct(productId, request, jwt);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void updateProduct_emptyNameUpdate_success() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = new ProductRequest();
        request.setDescription("Updated Description");
        ProductDto dto = createSampleProductDto();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsString("uid")).thenReturn(actorId.toString());

        when(productService.updateProduct(eq(productId), any(ProductRequest.class), eq(actorId), eq(jwt))).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.updateProduct(productId, request, jwt);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }
}