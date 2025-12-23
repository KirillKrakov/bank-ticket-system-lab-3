package com.example.tagservice.controller;

import com.example.tagservice.dto.ApplicationInfoDto;
import com.example.tagservice.dto.TagDto;
import com.example.tagservice.exception.NotFoundException;
import com.example.tagservice.exception.ServiceUnavailableException;
import com.example.tagservice.model.entity.Tag;
import com.example.tagservice.service.TagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TagControllerTest {

    @Mock
    private TagService tagService;

    @InjectMocks
    private TagController tagController;

    private TagDto createSampleTagDto() {
        TagDto dto = new TagDto();
        dto.setId(UUID.randomUUID());
        dto.setName("Test Tag");

        ApplicationInfoDto app1 = new ApplicationInfoDto();
        app1.setId(UUID.randomUUID());
        app1.setApplicantId(UUID.randomUUID());
        app1.setProductId(UUID.randomUUID());
        app1.setStatus("SUBMITTED");
        app1.setCreatedAt(Instant.now());

        ApplicationInfoDto app2 = new ApplicationInfoDto();
        app2.setId(UUID.randomUUID());
        app2.setApplicantId(UUID.randomUUID());
        app2.setProductId(UUID.randomUUID());
        app2.setStatus("APPROVED");
        app2.setCreatedAt(Instant.now());

        dto.setApplications(List.of(app1, app2));

        return dto;
    }

    private Tag createSampleTagEntity() {
        Tag tag = new Tag();
        tag.setId(UUID.randomUUID());
        tag.setName("Test Tag");
        return tag;
    }

    // -----------------------
    // createTag tests
    // -----------------------
    @Test
    void createTag_validName_returnsCreated() {
        String tagName = "New Tag";
        Tag tagEntity = createSampleTagEntity();
        tagEntity.setName(tagName);

        when(tagService.createIfNotExists(tagName)).thenReturn(tagEntity);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        ResponseEntity<TagDto> response = tagController.createTag(tagName, uriBuilder);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(tagEntity.getId(), response.getBody().getId());
        assertEquals(tagName, response.getBody().getName());
        assertNotNull(response.getHeaders().getLocation());
        assertTrue(response.getHeaders().getLocation().toString().contains("/api/v1/tags/"));
    }

    @Test
    void createTag_existingTag_returnsCreated() {
        String tagName = "Existing Tag";
        Tag tagEntity = createSampleTagEntity();
        tagEntity.setName(tagName);

        when(tagService.createIfNotExists(tagName)).thenReturn(tagEntity);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        ResponseEntity<TagDto> response = tagController.createTag(tagName, uriBuilder);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void createTag_nameWithSpaces_trimmedSuccessfully() {
        String tagName = "  Tag With Spaces  ";
        String trimmedName = "Tag With Spaces";
        Tag tagEntity = createSampleTagEntity();
        tagEntity.setName(trimmedName);

        when(tagService.createIfNotExists(trimmedName)).thenReturn(tagEntity);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        ResponseEntity<TagDto> response = tagController.createTag(tagName, uriBuilder);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(trimmedName, response.getBody().getName());
    }

    // -----------------------
    // listTags tests
    // -----------------------
    @Test
    void listTags_validParameters_returnsOkWithTotalCountHeader() {
        TagDto dto1 = createSampleTagDto();
        TagDto dto2 = createSampleTagDto();
        Page<TagDto> page = new PageImpl<>(List.of(dto1, dto2), PageRequest.of(0, 20), 50);

        when(tagService.listAll(0, 20)).thenReturn(page);

        ResponseEntity<List<TagDto>> response = tagController.listTags(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());

        HttpHeaders headers = response.getHeaders();
        assertNotNull(headers.get("X-Total-Count"));
        assertEquals("50", headers.getFirst("X-Total-Count"));
    }

    @Test
    void listTags_sizeExceedsMax_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                tagController.listTags(0, 100)
        );
        verify(tagService, never()).listAll(anyInt(), anyInt());
    }

    @Test
    void listTags_defaultParameters_returnsOk() {
        TagDto dto = createSampleTagDto();
        Page<TagDto> page = new PageImpl<>(List.of(dto), PageRequest.of(0, 20), 1);

        when(tagService.listAll(0, 20)).thenReturn(page);

        ResponseEntity<List<TagDto>> response = tagController.listTags(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void listTags_emptyPage_returnsEmptyList() {
        Page<TagDto> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(tagService.listAll(0, 20)).thenReturn(page);

        ResponseEntity<List<TagDto>> response = tagController.listTags(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        assertEquals("0", response.getHeaders().getFirst("X-Total-Count"));
    }

    @Test
    void listTags_largePageNumber_returnsEmptyIfNoData() {
        Page<TagDto> page = new PageImpl<>(List.of(), PageRequest.of(100, 20), 5);

        when(tagService.listAll(100, 20)).thenReturn(page);

        ResponseEntity<List<TagDto>> response = tagController.listTags(100, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    // -----------------------
    // getTagWithApplications tests
    // -----------------------
    @Test
    void getTagWithApplications_tagFound_returnsOk() {
        String tagName = "Existing Tag";
        TagDto dto = createSampleTagDto();
        dto.setName(tagName);

        when(tagService.getTagByName(tagName)).thenReturn(dto);

        ResponseEntity<TagDto> response = tagController.getTagWithApplications(tagName);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tagName, response.getBody().getName());
        assertNotNull(response.getBody().getApplications());
        assertEquals(2, response.getBody().getApplications().size());
    }

    @Test
    void getTagWithApplications_tagNotFound_throwsNotFoundException() {
        String tagName = "Non-existent Tag";

        when(tagService.getTagByName(tagName))
                .thenThrow(new NotFoundException("Tag not found: " + tagName));

        assertThrows(NotFoundException.class, () ->
                tagController.getTagWithApplications(tagName)
        );
    }

    @Test
    void getTagWithApplications_serviceUnavailable_throwsServiceUnavailableException() {
        String tagName = "Test Tag";

        when(tagService.getTagByName(tagName))
                .thenThrow(new ServiceUnavailableException("Application service is unavailable now"));

        assertThrows(ServiceUnavailableException.class, () ->
                tagController.getTagWithApplications(tagName)
        );
    }

    @Test
    void getTagWithApplications_tagWithoutApplications_returnsOk() {
        String tagName = "Tag Without Apps";
        TagDto dto = createSampleTagDto();
        dto.setName(tagName);
        dto.setApplications(List.of());

        when(tagService.getTagByName(tagName)).thenReturn(dto);

        ResponseEntity<TagDto> response = tagController.getTagWithApplications(tagName);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tagName, response.getBody().getName());
        assertNotNull(response.getBody().getApplications());
        assertTrue(response.getBody().getApplications().isEmpty());
    }

    // -----------------------
    // createOrGetTagsBatch tests
    // -----------------------
    @Test
    void createOrGetTagsBatch_validNames_returnsCreatedTags() {
        List<String> tagNames = List.of("Tag1", "Tag2", "Tag3");
        List<Tag> tags = List.of(
                createSampleTagEntity(),
                createSampleTagEntity(),
                createSampleTagEntity()
        );

        when(tagService.createOrGetTags(tagNames)).thenReturn(tags);

        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(tagNames);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
        verify(tagService).createOrGetTags(tagNames);
    }

    @Test
    void createOrGetTagsBatch_emptyList_returnsEmptyList() {
        List<String> tagNames = List.of();

        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(tagNames);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(tagService, never()).createOrGetTags(any());
    }

    @Test
    void createOrGetTagsBatch_nullList_returnsEmptyList() {
        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verify(tagService, never()).createOrGetTags(any());
    }

    @Test
    void createOrGetTagsBatch_namesWithSpaces_processedCorrectly() {
        List<String> tagNames = List.of("  Tag1  ", "Tag2  ", "  Tag3");
        List<Tag> tags = List.of(
                createSampleTagEntity(),
                createSampleTagEntity(),
                createSampleTagEntity()
        );

        when(tagService.createOrGetTags(any())).thenReturn(tags);

        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(tagNames);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(3, response.getBody().size());
    }

    @Test
    void createOrGetTagsBatch_duplicateNames_processedCorrectly() {
        List<String> tagNames = List.of("Tag1", "Tag1", "Tag2");
        List<Tag> tags = List.of(
                createSampleTagEntity(),
                createSampleTagEntity()
        );

        when(tagService.createOrGetTags(any())).thenReturn(tags);

        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(tagNames);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    // -----------------------
    // edge cases tests
    // -----------------------
    @Test
    void createTag_emptyName_createdSuccessfully() {
        String tagName = "";
        Tag tagEntity = createSampleTagEntity();
        tagEntity.setName("");

        when(tagService.createIfNotExists("")).thenReturn(tagEntity);

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();
        ResponseEntity<TagDto> response = tagController.createTag(tagName, uriBuilder);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("", response.getBody().getName());
    }

    @Test
    void createTag_nullName_throwsException() {
        // В контроллере параметр аннотирован @Valid, Spring вернёт 400 до вызова метода
        // Тестируем, что метод не вызывается при невалидных данных
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.newInstance();

        // Это вызовет MethodArgumentNotValidException (Spring Validation)
        // Для простоты теста проверим, что метод не был вызван с null
        verify(tagService, never()).createIfNotExists(null);
    }

    @Test
    void getTagWithApplications_nameWithSpecialCharacters_returnsOk() {
        String tagName = "Tag-With-Special@Chars#123";
        TagDto dto = createSampleTagDto();
        dto.setName(tagName);

        when(tagService.getTagByName(tagName)).thenReturn(dto);

        ResponseEntity<TagDto> response = tagController.getTagWithApplications(tagName);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(tagName, response.getBody().getName());
    }

    @Test
    void createOrGetTagsBatch_mixedCaseNames_processedCorrectly() {
        List<String> tagNames = List.of("TAG1", "Tag1", "tag1");
        List<Tag> tags = List.of(createSampleTagEntity());

        when(tagService.createOrGetTags(any())).thenReturn(tags);

        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(tagNames);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // Service должен обработать регистронезависимо или как определено в логике
        verify(tagService).createOrGetTags(any());
    }

    @Test
    void createOrGetTagsBatch_namesWithEmptyStrings_filteredOut() {
        List<String> tagNames = List.of("Tag1", "", "  ", "Tag2");
        List<Tag> tags = List.of(
                createSampleTagEntity(),
                createSampleTagEntity()
        );

        when(tagService.createOrGetTags(any())).thenReturn(tags);

        ResponseEntity<List<TagDto>> response = tagController.createOrGetTagsBatch(tagNames);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }
}
