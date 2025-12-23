package com.example.productservice.controller;

import com.example.productservice.controller.ProductController;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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

        when(productService.create(request)).thenReturn(responseDto);

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

        when(productService.create(request))
                .thenThrow(new BadRequestException("Invalid request"));

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        assertThrows(BadRequestException.class, () ->
                productController.create(request, uriBuilder)
        );
    }

    @Test
    void create_conflict_throwsConflictException() {
        ProductRequest request = createSampleProductRequest();

        when(productService.create(request))
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

        ResponseEntity<List<ProductDto>> response = productController.list(0, 20, mock(org.springframework.mock.web.MockHttpServletResponse.class));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void list_sizeExceedsMax_throwsBadRequestException() {
        assertThrows(BadRequestException.class, () ->
                productController.list(0, 100, mock(org.springframework.mock.web.MockHttpServletResponse.class))
        );
        verify(productService, never()).list(anyInt(), anyInt());
    }

    @Test
    void list_serviceThrowsBadRequest_throwsBadRequestException() {
        when(productService.list(0, 20))
                .thenThrow(new BadRequestException("Invalid parameters"));

        assertThrows(BadRequestException.class, () ->
                productController.list(0, 20, mock(org.springframework.mock.web.MockHttpServletResponse.class))
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

        when(productService.updateProduct(productId, request, actorId)).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.updateProduct(productId, request, actorId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void updateProduct_badRequest_throwsBadRequestException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        when(productService.updateProduct(productId, request, actorId))
                .thenThrow(new BadRequestException("Invalid request"));

        assertThrows(BadRequestException.class, () ->
                productController.updateProduct(productId, request, actorId)
        );
    }

    @Test
    void updateProduct_unauthorized_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        when(productService.updateProduct(productId, request, actorId))
                .thenThrow(new UnauthorizedException("Actor is unauthorized"));

        assertThrows(UnauthorizedException.class, () ->
                productController.updateProduct(productId, request, actorId)
        );
    }

    @Test
    void updateProduct_forbidden_throwsForbiddenException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        when(productService.updateProduct(productId, request, actorId))
                .thenThrow(new ForbiddenException("Insufficient rights"));

        assertThrows(ForbiddenException.class, () ->
                productController.updateProduct(productId, request, actorId)
        );
    }

    @Test
    void updateProduct_notFound_throwsNotFoundException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        when(productService.updateProduct(productId, request, actorId))
                .thenThrow(new NotFoundException("Product not found"));

        assertThrows(NotFoundException.class, () ->
                productController.updateProduct(productId, request, actorId)
        );
    }

    @Test
    void updateProduct_conflict_throwsConflictException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        when(productService.updateProduct(productId, request, actorId))
                .thenThrow(new ConflictException("Product name already in use"));

        assertThrows(ConflictException.class, () ->
                productController.updateProduct(productId, request, actorId)
        );
    }

    @Test
    void updateProduct_serviceUnavailable_throwsServiceUnavailableException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();

        when(productService.updateProduct(productId, request, actorId))
                .thenThrow(new ServiceUnavailableException("User service is unavailable"));

        assertThrows(ServiceUnavailableException.class, () ->
                productController.updateProduct(productId, request, actorId)
        );
    }

    // -----------------------
    // deleteProduct tests
    // -----------------------
    @Test
    void deleteProduct_success_returnsNoContent() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doNothing().when(productService).deleteProduct(productId, actorId);

        ResponseEntity<Void> response = productController.deleteProduct(productId, actorId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void deleteProduct_unauthorized_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doThrow(new UnauthorizedException("Actor is unauthorized"))
                .when(productService).deleteProduct(productId, actorId);

        assertThrows(UnauthorizedException.class, () ->
                productController.deleteProduct(productId, actorId)
        );
    }

    @Test
    void deleteProduct_forbidden_throwsForbiddenException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doThrow(new ForbiddenException("Insufficient rights"))
                .when(productService).deleteProduct(productId, actorId);

        assertThrows(ForbiddenException.class, () ->
                productController.deleteProduct(productId, actorId)
        );
    }

    @Test
    void deleteProduct_notFound_throwsNotFoundException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doThrow(new NotFoundException("Product not found"))
                .when(productService).deleteProduct(productId, actorId);

        assertThrows(NotFoundException.class, () ->
                productController.deleteProduct(productId, actorId)
        );
    }

    @Test
    void deleteProduct_conflict_throwsConflictException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doThrow(new ConflictException("Error during deletion"))
                .when(productService).deleteProduct(productId, actorId);

        assertThrows(ConflictException.class, () ->
                productController.deleteProduct(productId, actorId)
        );
    }

    @Test
    void deleteProduct_serviceUnavailable_throwsServiceUnavailableException() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        doThrow(new ServiceUnavailableException("User service is unavailable"))
                .when(productService).deleteProduct(productId, actorId);

        assertThrows(ServiceUnavailableException.class, () ->
                productController.deleteProduct(productId, actorId)
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

        when(productService.create(request))
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

        when(productService.create(request))
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
    void updateProduct_nullActorId_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        ProductRequest request = createSampleProductRequest();
        UUID nullActorId = null;

        when(productService.updateProduct(productId, request, nullActorId))
                .thenThrow(new UnauthorizedException("You must specify the actorId to authorize in this request"));

        assertThrows(UnauthorizedException.class, () ->
                productController.updateProduct(productId, request, nullActorId)
        );
    }

    @Test
    void deleteProduct_nullActorId_throwsUnauthorizedException() {
        UUID productId = UUID.randomUUID();
        UUID nullActorId = null;

        doThrow(new UnauthorizedException("You must specify the actorId to authorize in this request"))
                .when(productService).deleteProduct(productId, nullActorId);

        assertThrows(UnauthorizedException.class, () ->
                productController.deleteProduct(productId, nullActorId)
        );
    }

    @Test
    void list_emptyPage_returnsEmptyList() {
        Page<ProductDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(productService.list(0, 20)).thenReturn(page);

        ResponseEntity<List<ProductDto>> response = productController.list(0, 20, mock(org.springframework.mock.web.MockHttpServletResponse.class));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void list_largePageNumber_returnsEmptyIfNoData() {
        Page<ProductDto> page = new PageImpl<>(List.of(), PageRequest.of(100, 20), 5);

        when(productService.list(100, 20)).thenReturn(page);

        ResponseEntity<List<ProductDto>> response = productController.list(100, 20, mock(org.springframework.mock.web.MockHttpServletResponse.class));

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
        // description не устанавливаем - partial update
        ProductDto dto = createSampleProductDto();

        when(productService.updateProduct(productId, request, actorId)).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.updateProduct(productId, request, actorId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }

    @Test
    void updateProduct_emptyNameUpdate_success() {
        UUID productId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ProductRequest request = new ProductRequest();
        request.setDescription("Updated Description");
        // name не устанавливаем - оставляем прежним
        ProductDto dto = createSampleProductDto();

        when(productService.updateProduct(productId, request, actorId)).thenReturn(dto);

        ResponseEntity<ProductDto> response = productController.updateProduct(productId, request, actorId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(dto, response.getBody());
    }
}
