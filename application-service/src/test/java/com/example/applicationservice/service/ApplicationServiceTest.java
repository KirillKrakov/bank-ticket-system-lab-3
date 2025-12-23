package com.example.applicationservice.service;

import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.feign.*;
import com.example.applicationservice.model.entity.*;
import com.example.applicationservice.model.enums.ApplicationStatus;
import com.example.applicationservice.model.enums.UserRole;
import com.example.applicationservice.repository.*;
import com.example.applicationservice.service.ApplicationService;
import com.example.applicationservice.util.ApplicationPage;
import com.example.applicationservice.util.CursorUtil;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.domain.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class ApplicationServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private ApplicationHistoryRepository applicationHistoryRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private TagServiceClient tagServiceClient;

    @InjectMocks
    private ApplicationService applicationService;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        // Не нужно создавать экземпляр вручную, @InjectMocks сделает это
    }

    // -----------------------
    // createApplication tests
    // -----------------------
    @Test
    public void createApplication_nullRequest_throwsBadRequest() {
        StepVerifier.create(applicationService.createApplication(null))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void createApplication_missingApplicantOrProduct_throwsBadRequest() {
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(null);
        req.setProductId(null);

        StepVerifier.create(applicationService.createApplication(req))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void createApplication_applicantNotFound_throwsNotFound() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(aid);
        req.setProductId(pid);

        when(userServiceClient.userExists(aid)).thenReturn(false);

        StepVerifier.create(applicationService.createApplication(req))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void createApplication_productNotFound_throwsNotFound() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(aid);
        req.setProductId(pid);

        when(userServiceClient.userExists(aid)).thenReturn(true);
        when(productServiceClient.productExists(pid)).thenReturn(false);

        StepVerifier.create(applicationService.createApplication(req))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void createApplication_success_createsApplicationAndHistoryAndTags() {
        UUID aid = UUID.randomUUID();
        UUID pid = UUID.randomUUID();
        ApplicationRequest req = new ApplicationRequest();
        req.setApplicantId(aid);
        req.setProductId(pid);
        req.setTags(List.of("t1", "t2"));

        DocumentRequest d = new DocumentRequest();
        d.setFileName("f.txt");
        d.setContentType("text/plain");
        d.setStoragePath("/tmp/f");
        req.setDocuments(List.of(d));

        when(userServiceClient.userExists(aid)).thenReturn(true);
        when(productServiceClient.productExists(pid)).thenReturn(true);

        // Mock tag service response
        TagDto tag1 = new TagDto();
        tag1.setName("t1");
        TagDto tag2 = new TagDto();
        tag2.setName("t2");
        when(tagServiceClient.createOrGetTagsBatch(List.of("t1", "t2")))
                .thenReturn(List.of(tag1, tag2));

        // Mock repository save operations
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationHistoryRepository.save(any(ApplicationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        // Выполнение тестируемого метода
        StepVerifier.create(applicationService.createApplication(req))
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertNotNull(dto.getId());
                    assertEquals(aid, dto.getApplicantId());
                    assertEquals(pid, dto.getProductId());
                    assertEquals(ApplicationStatus.SUBMITTED, dto.getStatus());
                    assertEquals(1, dto.getDocuments().size());
                    assertEquals(2, dto.getTags().size());
                })
                .verifyComplete();

        // Проверки взаимодействий
        verify(applicationRepository, atLeastOnce()).save(any(Application.class));
        verify(applicationHistoryRepository, times(1)).save(any(ApplicationHistory.class));
        verify(tagServiceClient, times(1)).createOrGetTagsBatch(List.of("t1", "t2"));
        verify(userServiceClient, times(1)).userExists(aid);
        verify(productServiceClient, times(1)).productExists(pid);
    }

    // -----------------------
    // findAll tests
    // -----------------------
    @Test
    public void findAll_sizeExceeds50_throwsBadRequest() {
        StepVerifier.create(applicationService.findAll(0, 51))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void findAll_returnsPagedDtos() {
        // Setup
        Application app1 = new Application();
        app1.setId(UUID.randomUUID());
        app1.setStatus(ApplicationStatus.SUBMITTED);
        app1.setCreatedAt(Instant.now());

        Application app2 = new Application();
        app2.setId(UUID.randomUUID());
        app2.setStatus(ApplicationStatus.DRAFT);
        app2.setCreatedAt(Instant.now());

        Page<Application> page = new PageImpl<>(List.of(app1, app2));
        when(applicationRepository.findAllWithDocuments(any(Pageable.class))).thenReturn(page);

        List<UUID> appIds = List.of(app1.getId(), app2.getId());
        when(applicationRepository.findByIdsWithTags(appIds)).thenReturn(List.of(app1, app2));

        // Test
        StepVerifier.create(applicationService.findAll(0, 10))
                .expectNextCount(2)
                .verifyComplete();
    }

    // -----------------------
    // findById tests
    // -----------------------
    @Test
    public void findById_whenNotFound_returnsEmpty() {
        UUID id = UUID.randomUUID();
        when(applicationRepository.findByIdWithDocuments(id)).thenReturn(Optional.empty());

        StepVerifier.create(applicationService.findById(id))
                .expectNextCount(0)
                .verifyError();
    }

    @Test
    public void findById_whenFound_returnsDto() {
        UUID id = UUID.randomUUID();
        Application app = new Application();
        app.setId(id);
        app.setStatus(ApplicationStatus.DRAFT);
        app.setCreatedAt(Instant.now());

        when(applicationRepository.findByIdWithDocuments(id)).thenReturn(Optional.of(app));
        when(applicationRepository.findByIdWithTags(id)).thenReturn(Optional.of(app));

        StepVerifier.create(applicationService.findById(id))
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals(id, dto.getId());
                })
                .verifyComplete();
    }

    // -----------------------
    // streamWithNextCursor tests
    // -----------------------
    @Test
    public void streamWithNextCursor_invalidLimit_throwsBadRequest() {
        StepVerifier.create(applicationService.streamWithNextCursor(null, 0))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void streamWithNextCursor_callsFirstPageRepository_whenCursorIsNull() {
        UUID appId1 = UUID.randomUUID();
        UUID appId2 = UUID.randomUUID();
        Instant timestamp1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant timestamp2 = Instant.parse("2024-01-01T00:00:10Z");

        List<UUID> appIds = List.of(appId1, appId2);
        when(applicationRepository.findIdsFirstPage(5)).thenReturn(appIds);

        Application app1 = new Application();
        app1.setId(appId1);
        app1.setCreatedAt(timestamp1);
        Application app2 = new Application();
        app2.setId(appId2);
        app2.setCreatedAt(timestamp2);

        when(applicationRepository.findByIdsWithDocuments(appIds)).thenReturn(List.of(app1, app2));
        when(applicationRepository.findByIdsWithTags(appIds)).thenReturn(List.of(app1, app2));

        StepVerifier.create(applicationService.streamWithNextCursor(null, 5))
                .assertNext(page -> {
                    assertNotNull(page);
                    assertEquals(2, page.items().size());
                    assertNotNull(page.nextCursor());
                })
                .verifyComplete();

        verify(applicationRepository, times(1)).findIdsFirstPage(5);
    }

    @Test
    public void streamWithNextCursor_callsFindByKeyset_whenCursorProvided() {
        Instant timestamp = Instant.parse("2024-01-01T00:00:05Z");
        UUID cursorId = UUID.randomUUID();
        String cursor = CursorUtil.encode(timestamp, cursorId);

        UUID appId = UUID.randomUUID();
        List<UUID> appIds = List.of(appId);
        when(applicationRepository.findIdsByKeyset(timestamp, cursorId, 5)).thenReturn(appIds);

        Application app = new Application();
        app.setId(appId);
        app.setCreatedAt(Instant.parse("2024-01-01T00:00:04Z"));

        when(applicationRepository.findByIdsWithDocuments(appIds)).thenReturn(List.of(app));
        when(applicationRepository.findByIdsWithTags(appIds)).thenReturn(List.of(app));

        StepVerifier.create(applicationService.streamWithNextCursor(cursor, 5))
                .assertNext(page -> {
                    assertEquals(1, page.items().size());
                    assertNotNull(page.nextCursor());
                })
                .verifyComplete();

        verify(applicationRepository, times(1)).findIdsByKeyset(timestamp, cursorId, 5);
    }

    // -----------------------
    // attachTags tests
    // -----------------------

    @Test
    public void attachTags_applicationNotFound_throwsForbidden() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationRepository.findByIdWithDocuments(applicationId)).thenReturn(Optional.empty());

        StepVerifier.create(applicationService.attachTags(applicationId, tags, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void attachTags_notAllowed_throwsForbidden() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(UUID.randomUUID()); // Different from actorId
        app.setTags(new HashSet<>());

        when(applicationRepository.findByIdWithTags(applicationId)).thenReturn(Optional.of(app));

        // Mock user role check
        UserRole clientRole = UserRole.ROLE_CLIENT;
        when(userServiceClient.getUserRole(actorId)).thenReturn(clientRole);
        when(applicationRepository.findByIdWithDocuments(applicationId))
                .thenReturn(Optional.of(app));

        StepVerifier.create(applicationService.attachTags(applicationId, tags, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void attachTags_success_addsTagsAndSaves() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(actorId); // Same as actorId
        app.setTags(new HashSet<>());

        TagDto tagDto = new TagDto();
        tagDto.setName("tag1");

        when(applicationRepository.findByIdWithTags(applicationId)).thenReturn(Optional.of(app));
        when(tagServiceClient.createOrGetTagsBatch(tags)).thenReturn(List.of(tagDto));
        when(applicationRepository.save(app)).thenReturn(app);

        // Mock user role check
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(applicationRepository.findByIdWithDocuments(applicationId))
                .thenReturn(Optional.of(app));

        StepVerifier.create(applicationService.attachTags(applicationId, tags, actorId))
                .verifyComplete();

        assertTrue(app.getTags().contains("tag1"));
        verify(applicationRepository, times(1)).save(app);
        verify(tagServiceClient, times(1)).createOrGetTagsBatch(tags);
    }

    // -----------------------
    // removeTags tests
    // -----------------------
    @Test
    public void removeTags_applicationNotFound_throwsForbidden() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationRepository.findByIdWithDocuments(applicationId)).thenReturn(Optional.empty());

        StepVerifier.create(applicationService.removeTags(applicationId, tags, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void removeTags_success_removesTagsAndSaves() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(actorId);
        app.setTags(new HashSet<>(Set.of("tag1", "tag2")));

        when(applicationRepository.findByIdWithTags(applicationId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(app)).thenReturn(app);

        // Mock user role check
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(applicationRepository.findByIdWithDocuments(applicationId))
                .thenReturn(Optional.of(app));

        StepVerifier.create(applicationService.removeTags(applicationId, tags, actorId))
                .verifyComplete();

        assertFalse(app.getTags().contains("tag1"));
        assertTrue(app.getTags().contains("tag2"));
        verify(applicationRepository, times(1)).save(app);
    }

    // -----------------------
    // changeStatus tests
    // -----------------------
    @Test
    public void changeStatus_applicationNotFound_throwsNotFound() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        // Настраиваем, что пользователь - админ, чтобы проверка прав прошла
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);

        // Приложение не найдено
        when(applicationRepository.findById(applicationId)).thenReturn(Optional.empty());

        StepVerifier.create(applicationService.changeStatus(applicationId, status, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void changeStatus_actorNotAdminOrManager_throwsForbidden() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(UUID.randomUUID());

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(app));
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);

        StepVerifier.create(applicationService.changeStatus(applicationId, status, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void changeStatus_managerCannotChangeOwnApplication_throwsConflict() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(actorId); // Manager is applicant
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(app));
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_MANAGER);

        StepVerifier.create(applicationService.changeStatus(applicationId, status, actorId))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    public void changeStatus_invalidStatus_throwsConflict() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "INVALID_STATUS";

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(UUID.randomUUID()); // Different from actor
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(app));
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);

        StepVerifier.create(applicationService.changeStatus(applicationId, status, actorId))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    public void changeStatus_adminSuccess_savesApplicationAndHistory() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(UUID.randomUUID()); // Different from actor
        app.setStatus(ApplicationStatus.SUBMITTED);

        when(applicationRepository.findById(applicationId)).thenReturn(Optional.of(app));
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        when(applicationRepository.findByIdWithDocuments(applicationId)).thenReturn(Optional.of(app));
        when(applicationRepository.findByIdWithTags(applicationId)).thenReturn(Optional.of(app));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));
        when(applicationHistoryRepository.save(any(ApplicationHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        StepVerifier.create(applicationService.changeStatus(applicationId, status, actorId))
                .assertNext(dto -> {
                    assertNotNull(dto);
                    assertEquals(ApplicationStatus.APPROVED, dto.getStatus());
                })
                .verifyComplete();

        verify(applicationRepository, times(1)).save(any(Application.class));
        verify(applicationHistoryRepository, times(1)).save(any(ApplicationHistory.class));
    }

    // -----------------------
    // deleteApplication tests
    // -----------------------
    @Test
    public void deleteApplication_actorNotAdmin_throwsForbidden() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);

        StepVerifier.create(applicationService.deleteApplication(applicationId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void deleteApplication_success_callsAllDeletes() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_ADMIN);
        doNothing().when(documentRepository).deleteByApplicationId(applicationId);
        doNothing().when(applicationHistoryRepository).deleteByApplicationId(applicationId);
        doNothing().when(applicationRepository).deleteTagsByApplicationId(applicationId);
        doNothing().when(applicationRepository).deleteById(applicationId);

        StepVerifier.create(applicationService.deleteApplication(applicationId, actorId))
                .verifyComplete();

        verify(documentRepository, times(1)).deleteByApplicationId(applicationId);
        verify(applicationHistoryRepository, times(1)).deleteByApplicationId(applicationId);
        verify(applicationRepository, times(1)).deleteTagsByApplicationId(applicationId);
        verify(applicationRepository, times(1)).deleteById(applicationId);
    }

    // -----------------------
    // listHistory tests
    // -----------------------
    @Test
    public void listHistory_notAllowed_throwsForbidden() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(UUID.randomUUID()); // Different from actor

        when(applicationRepository.findByIdWithDocuments(applicationId)).thenReturn(Optional.of(app));
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);

        StepVerifier.create(applicationService.listHistory(applicationId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void listHistory_success_returnsHistoryDtos() {
        UUID applicationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Application app = new Application();
        app.setId(applicationId);
        app.setApplicantId(actorId); // Same as actor

        ApplicationHistory h1 = new ApplicationHistory();
        h1.setId(UUID.randomUUID());
        h1.setApplication(app);
        h1.setOldStatus(null);
        h1.setNewStatus(ApplicationStatus.SUBMITTED);
        h1.setChangedBy(UserRole.ROLE_CLIENT);
        h1.setChangedAt(Instant.now());

        when(applicationRepository.findByIdWithDocuments(applicationId)).thenReturn(Optional.of(app));
        when(userServiceClient.getUserRole(actorId)).thenReturn(UserRole.ROLE_CLIENT);
        when(applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId))
                .thenReturn(List.of(h1));

        StepVerifier.create(applicationService.listHistory(applicationId, actorId))
                .expectNextCount(1)
                .verifyComplete();
    }

    // -----------------------
    // deleteApplicationsByUserId tests
    // -----------------------
    @Test
    public void deleteApplicationsByUserId_success_deletesAllRelatedData() {
        UUID userId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        when(applicationRepository.findIdsByApplicantId(userId)).thenReturn(List.of(appId));
        doNothing().when(documentRepository).deleteByApplicationId(appId);
        doNothing().when(applicationHistoryRepository).deleteByApplicationId(appId);
        doNothing().when(applicationRepository).deleteTagsByApplicationId(appId);
        doNothing().when(applicationRepository).deleteById(appId);

        StepVerifier.create(applicationService.deleteApplicationsByUserId(userId))
                .verifyComplete();

        verify(documentRepository, times(1)).deleteByApplicationId(appId);
        verify(applicationHistoryRepository, times(1)).deleteByApplicationId(appId);
        verify(applicationRepository, times(1)).deleteTagsByApplicationId(appId);
        verify(applicationRepository, times(1)).deleteById(appId);
    }

    // -----------------------
    // deleteApplicationsByProductId tests
    // -----------------------
    @Test
    public void deleteApplicationsByProductId_success_deletesAllRelatedData() {
        UUID productId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        when(applicationRepository.findIdsByProductId(productId)).thenReturn(List.of(appId));
        doNothing().when(documentRepository).deleteByApplicationId(appId);
        doNothing().when(applicationHistoryRepository).deleteByApplicationId(appId);
        doNothing().when(applicationRepository).deleteTagsByApplicationId(appId);
        doNothing().when(applicationRepository).deleteById(appId);

        StepVerifier.create(applicationService.deleteApplicationsByProductId(productId))
                .verifyComplete();

        verify(documentRepository, times(1)).deleteByApplicationId(appId);
        verify(applicationHistoryRepository, times(1)).deleteByApplicationId(appId);
        verify(applicationRepository, times(1)).deleteTagsByApplicationId(appId);
        verify(applicationRepository, times(1)).deleteById(appId);
    }

    // -----------------------
    // findApplicationsByTag tests
    // -----------------------
    @Test
    public void findApplicationsByTag_success_returnsApplicationInfoDtos() {
        String tagName = "important";
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(UUID.randomUUID());
        app.setProductId(UUID.randomUUID());
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(Instant.now());

        when(applicationRepository.findByTag(tagName)).thenReturn(List.of(app));

        StepVerifier.create(applicationService.findApplicationsByTag(tagName))
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals(app.getId(), list.get(0).getId());
                })
                .verifyComplete();
    }

    // -----------------------
    // count tests
    // -----------------------
    @Test
    public void count_success_returnsCount() {
        long expectedCount = 42L;
        when(applicationRepository.count()).thenReturn(expectedCount);

        StepVerifier.create(applicationService.count())
                .assertNext(count -> assertEquals(expectedCount, count))
                .verifyComplete();
    }
}