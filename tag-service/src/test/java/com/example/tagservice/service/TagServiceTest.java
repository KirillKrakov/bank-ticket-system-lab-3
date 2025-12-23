package com.example.tagservice.service;

import com.example.tagservice.dto.ApplicationInfoDto;
import com.example.tagservice.dto.TagDto;
import com.example.tagservice.exception.NotFoundException;
import com.example.tagservice.feign.ApplicationServiceClient;
import com.example.tagservice.model.entity.Tag;
import com.example.tagservice.repository.TagRepository;
import com.example.tagservice.service.TagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ApplicationServiceClient applicationServiceClient;

    @InjectMocks
    private TagService tagService;

    private UUID testId;
    private String tagName;

    @BeforeEach
    public void setUp() {
        testId = UUID.randomUUID();
        tagName = "test-tag";
    }

    // -----------------------
    // createIfNotExists tests
    // -----------------------
    @Test
    public void createIfNotExists_createsNewTagWhenNotExists() {
        // Given
        String tagName = "urgent";
        when(tagRepository.findByName(tagName)).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.setId(testId);
            return tag;
        });

        // When
        Tag result = tagService.createIfNotExists(tagName);

        // Then
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(tagName, result.getName());
        verify(tagRepository, times(1)).findByName(tagName);
        verify(tagRepository, times(1)).save(any(Tag.class));
    }

    @Test
    public void createIfNotExists_returnsExistingTagWhenExists() {
        // Given
        Tag existingTag = new Tag();
        existingTag.setId(testId);
        existingTag.setName(tagName);
        when(tagRepository.findByName(tagName)).thenReturn(Optional.of(existingTag));

        // When
        Tag result = tagService.createIfNotExists(tagName);

        // Then
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(tagName, result.getName());
        verify(tagRepository, times(1)).findByName(tagName);
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    public void createIfNotExists_trimsName() {
        // Given
        String inputName = "  urgent  ";
        String expectedName = "urgent";

        lenient().when(tagRepository.findByName(expectedName)).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.setId(testId);
            return tag;
        });

        // When
        Tag result = tagService.createIfNotExists(inputName);

        // Then
        assertEquals(expectedName, result.getName());
    }

    // -----------------------
    // createOrGetTags tests
    // -----------------------
    @Test
    public void createOrGetTags_returnsEmptyListForNullInput() {
        // When
        List<Tag> result = tagService.createOrGetTags(null);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(tagRepository, never()).findByNames(anyList());
        verify(tagRepository, never()).saveAll(anyList());
    }

    @Test
    public void createOrGetTags_returnsEmptyListForEmptyInput() {
        // When
        List<Tag> result = tagService.createOrGetTags(Collections.emptyList());

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(tagRepository, never()).findByNames(anyList());
        verify(tagRepository, never()).saveAll(anyList());
    }

    @Test
    public void createOrGetTags_returnsExistingTagsOnly() {
        // Given
        List<String> tagNames = Arrays.asList("tag1", "tag2");

        Tag existingTag1 = new Tag();
        existingTag1.setId(UUID.randomUUID());
        existingTag1.setName("tag1");

        Tag existingTag2 = new Tag();
        existingTag2.setId(UUID.randomUUID());
        existingTag2.setName("tag2");

        when(tagRepository.findByNames(tagNames)).thenReturn(Arrays.asList(existingTag1, existingTag2));

        // When
        List<Tag> result = tagService.createOrGetTags(tagNames);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.contains(existingTag1));
        assertTrue(result.contains(existingTag2));
        verify(tagRepository, times(1)).findByNames(tagNames);
        verify(tagRepository, never()).saveAll(anyList());
    }

    @Test
    public void createOrGetTags_createsNewTagsWhenNeeded() {
        // Given
        List<String> tagNames = Arrays.asList("tag1", "tag2");

        Tag existingTag = new Tag();
        existingTag.setId(UUID.randomUUID());
        existingTag.setName("tag1");

        // Создаем изменяемый список с одним элементом
        when(tagRepository.findByNames(tagNames)).thenReturn(new ArrayList<>(Collections.singletonList(existingTag)));
        when(tagRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Tag> newTags = invocation.getArgument(0);
            newTags.forEach(tag -> tag.setId(UUID.randomUUID()));
            return newTags;
        });

        // When
        List<Tag> result = tagService.createOrGetTags(tagNames);

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(tag -> "tag1".equals(tag.getName())));
        assertTrue(result.stream().anyMatch(tag -> "tag2".equals(tag.getName())));
        verify(tagRepository, times(1)).findByNames(tagNames);
        verify(tagRepository, times(1)).saveAll(anyList());
    }

    @Test
    public void createOrGetTags_trimsAndRemovesDuplicates2() {
        // Given
        List<String> inputNames = Arrays.asList("  tag1  ", "tag1", "tag2  ", "  tag2");
        List<String> expectedNames = Arrays.asList("tag1", "tag2");

        // Используем изменяемый список вместо Arrays.asList()
        when(tagRepository.findByNames(expectedNames)).thenReturn(new ArrayList<>());
        when(tagRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<Tag> newTags = invocation.getArgument(0);
            newTags.forEach(tag -> tag.setId(UUID.randomUUID()));
            return newTags;
        });

        // When
        List<Tag> result = tagService.createOrGetTags(inputNames);

        // Then
        assertEquals(2, result.size());
        verify(tagRepository, times(1)).findByNames(expectedNames);
    }

    // -----------------------
    // listAll tests
    // -----------------------
    @Test
    public void listAll_returnsPageOfTagDtos() {
        // Given
        Tag tag1 = new Tag();
        tag1.setId(UUID.randomUUID());
        tag1.setName("tag1");

        Tag tag2 = new Tag();
        tag2.setId(UUID.randomUUID());
        tag2.setName("tag2");

        Page<Tag> tagPage = new PageImpl<>(Arrays.asList(tag1, tag2));
        when(tagRepository.findAll(any(PageRequest.class))).thenReturn(tagPage);

        ApplicationInfoDto appInfo = new ApplicationInfoDto();
        appInfo.setId(UUID.randomUUID());

        when(applicationServiceClient.getApplicationsByTag("tag1")).thenReturn(Collections.singletonList(appInfo));
        when(applicationServiceClient.getApplicationsByTag("tag2")).thenReturn(Collections.emptyList());

        // When
        Page<TagDto> result = tagService.listAll(0, 10);

        // Then
        assertEquals(2, result.getNumberOfElements());

        TagDto dto1 = result.getContent().get(0);
        assertEquals(tag1.getId(), dto1.getId());
        assertEquals("tag1", dto1.getName());
        assertEquals(1, dto1.getApplications().size());

        TagDto dto2 = result.getContent().get(1);
        assertEquals(tag2.getId(), dto2.getId());
        assertEquals("tag2", dto2.getName());
        assertEquals(0, dto2.getApplications().size());

        verify(tagRepository, times(1)).findAll(any(PageRequest.class));
    }

    // -----------------------
    // getTagByName tests
    // -----------------------
    @Test
    public void getTagByName_throwsNotFoundExceptionWhenTagNotFound() {
        // Given
        String nonExistentTag = "non-existent";
        when(tagRepository.findByName(nonExistentTag)).thenReturn(Optional.empty());

        // When & Then
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> tagService.getTagByName(nonExistentTag));

        assertEquals("Tag not found: " + nonExistentTag, exception.getMessage());
        verify(tagRepository, times(1)).findByName(nonExistentTag);
    }

    @Test
    public void getTagByName_returnsTagDtoWithApplications() {
        // Given
        Tag tag = new Tag();
        tag.setId(testId);
        tag.setName(tagName);

        when(tagRepository.findByName(tagName)).thenReturn(Optional.of(tag));

        ApplicationInfoDto appInfo1 = new ApplicationInfoDto();
        appInfo1.setId(UUID.randomUUID());

        ApplicationInfoDto appInfo2 = new ApplicationInfoDto();
        appInfo2.setId(UUID.randomUUID());

        when(applicationServiceClient.getApplicationsByTag(tagName))
                .thenReturn(Arrays.asList(appInfo1, appInfo2));

        // When
        TagDto result = tagService.getTagByName(tagName);

        // Then
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(tagName, result.getName());
        assertEquals(2, result.getApplications().size());
        assertTrue(result.getApplications().contains(appInfo1));
        assertTrue(result.getApplications().contains(appInfo2));

        verify(tagRepository, times(1)).findByName(tagName);
        verify(applicationServiceClient, times(1)).getApplicationsByTag(tagName);
    }

    @Test
    public void getTagByName_handlesEmptyApplicationsList() {
        // Given
        Tag tag = new Tag();
        tag.setId(testId);
        tag.setName(tagName);

        when(tagRepository.findByName(tagName)).thenReturn(Optional.of(tag));
        when(applicationServiceClient.getApplicationsByTag(tagName))
                .thenReturn(Collections.emptyList());

        // When
        TagDto result = tagService.getTagByName(tagName);

        // Then
        assertNotNull(result);
        assertEquals(testId, result.getId());
        assertEquals(tagName, result.getName());
        assertNotNull(result.getApplications());
        assertTrue(result.getApplications().isEmpty());
    }

    // -----------------------
    // toDto private method tests (indirectly tested)
    // -----------------------
    @Test
    public void toDto_mapsTagCorrectly() {
        // This tests the private toDto method indirectly through listAll
        Tag tag = new Tag();
        tag.setId(testId);
        tag.setName(tagName);

        Page<Tag> tagPage = new PageImpl<>(Collections.singletonList(tag));
        when(tagRepository.findAll(any(PageRequest.class))).thenReturn(tagPage);
        when(applicationServiceClient.getApplicationsByTag(tagName))
                .thenReturn(Collections.emptyList());

        // When
        Page<TagDto> resultPage = tagService.listAll(0, 10);
        TagDto result = resultPage.getContent().get(0);

        // Then
        assertEquals(testId, result.getId());
        assertEquals(tagName, result.getName());
        assertNotNull(result.getApplications());
        assertTrue(result.getApplications().isEmpty());
    }

    @Test
    public void toDto_includesApplicationsFromClient() {
        // This tests that applications are fetched from the external service
        Tag tag = new Tag();
        tag.setId(testId);
        tag.setName(tagName);

        Page<Tag> tagPage = new PageImpl<>(Collections.singletonList(tag));
        when(tagRepository.findAll(any(PageRequest.class))).thenReturn(tagPage);

        ApplicationInfoDto appInfo = new ApplicationInfoDto();
        appInfo.setId(UUID.randomUUID());

        when(applicationServiceClient.getApplicationsByTag(tagName))
                .thenReturn(Collections.singletonList(appInfo));

        // When
        Page<TagDto> resultPage = tagService.listAll(0, 10);
        TagDto result = resultPage.getContent().get(0);

        // Then
        assertEquals(1, result.getApplications().size());
        assertEquals(appInfo, result.getApplications().get(0));
        verify(applicationServiceClient, times(1)).getApplicationsByTag(tagName);
    }
}