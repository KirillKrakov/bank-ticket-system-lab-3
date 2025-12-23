package com.example.applicationservice.service;

import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.feign.*;
import com.example.applicationservice.model.entity.*;
import com.example.applicationservice.model.enums.ApplicationStatus;
import com.example.applicationservice.model.enums.UserRole;
import com.example.applicationservice.repository.*;
import com.example.applicationservice.util.ApplicationPage;
import com.example.applicationservice.util.CursorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationHistoryRepository applicationHistoryRepository;
    private final DocumentRepository documentRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;
    private final TagServiceClient tagServiceClient;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationHistoryRepository applicationHistoryRepository,
            DocumentRepository documentRepository,
            UserServiceClient userServiceClient,
            ProductServiceClient productServiceClient,
            TagServiceClient tagServiceClient) {
        this.applicationRepository = applicationRepository;
        this.applicationHistoryRepository = applicationHistoryRepository;
        this.documentRepository = documentRepository;
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
        this.tagServiceClient = tagServiceClient;
    }

    @Transactional
    public Mono<ApplicationDto> createApplication(ApplicationRequest req) {
        if (req == null) {
            return Mono.error(new BadRequestException("Request is required"));
        }

        UUID applicantId = req.getApplicantId();
        UUID productId = req.getProductId();

        if (applicantId == null || productId == null) {
            return Mono.error(new BadRequestException("Applicant ID and Product ID are required"));
        }

        return Mono.fromCallable(() -> {
                try {
                    return userServiceClient.userExists(applicantId);
                } catch (ServiceUnavailableException e) {
                    throw new ServiceUnavailableException("User service is unavailable now");
                } catch (NotFoundException e){
                    throw new NotFoundException("Applicant with this ID not found");
                }}).subscribeOn(Schedulers.boundedElastic())
                .flatMap(userExists -> {
                    if (!userExists) {
                        return Mono.error(new NotFoundException("Applicant with this ID not found"));
                    }
                    return Mono.fromCallable(() -> {
                        try {
                            return productServiceClient.productExists(productId);
                        } catch (ServiceUnavailableException e) {
                            throw new ServiceUnavailableException("Product service is unavailable now");
                        } catch (NotFoundException e){
                            throw new NotFoundException("Product with this ID not found");
                        }}).subscribeOn(Schedulers.boundedElastic());
                })
                .flatMap(productExists -> {
                    if (!productExists) {
                        return Mono.error(new NotFoundException("Product with this ID not found"));
                    }
                    return Mono.fromCallable(() -> {
                        Application app = new Application();
                        app.setId(UUID.randomUUID());
                        app.setApplicantId(applicantId);
                        app.setProductId(productId);
                        app.setStatus(ApplicationStatus.SUBMITTED);
                        app.setCreatedAt(Instant.now());
                        if (req.getDocuments() != null) {
                            List<Document> docs = req.getDocuments().stream()
                                    .map(dreq -> {
                                        Document d = new Document();
                                        d.setId(UUID.randomUUID());
                                        d.setFileName(dreq.getFileName());
                                        d.setContentType(dreq.getContentType());
                                        d.setStoragePath(dreq.getStoragePath());
                                        d.setApplication(app);
                                        return d;
                                    })
                                    .collect(Collectors.toList());
                            app.setDocuments(docs);
                        }

                        applicationRepository.save(app);

                        ApplicationHistory hist = new ApplicationHistory();
                        hist.setId(UUID.randomUUID());
                        hist.setApplication(app);
                        hist.setOldStatus(null);
                        hist.setNewStatus(app.getStatus());
                        hist.setChangedBy(UserRole.ROLE_CLIENT);
                        hist.setChangedAt(Instant.now());
                        applicationHistoryRepository.save(hist);

                        log.info("Application created: {}", app.getId());
                        return app;
                    }).subscribeOn(Schedulers.boundedElastic());
                })
                .flatMap(app -> {
                    List<String> tagNames = req.getTags() != null ? req.getTags() : List.of();
                    if (!tagNames.isEmpty()) {
                        return Mono.fromCallable(() -> {
                            try {
                                return tagServiceClient.createOrGetTagsBatch(tagNames);
                            } catch (ServiceUnavailableException e) {
                                throw new ServiceUnavailableException("Tag service is unavailable now. Application saved without tags");
                            }}).subscribeOn(Schedulers.boundedElastic())
                                .flatMap(tagDtos -> {
                                    if (tagDtos == null) {
                                        return Mono.error(new ServiceUnavailableException("Tag service is unavailable now. Application saved without tags"));
                                    }
                                    return Mono.fromCallable(() -> {
                                    Set<String> tagNamesSet = tagDtos.stream()
                                            .map(TagDto::getName)
                                            .collect(Collectors.toSet());
                                    app.setTags(tagNamesSet);
                                    applicationRepository.save(app);
                                    log.info("Added {} tags to application {}", tagNamesSet.size(), app.getId());
                                    return app;
                                });
                                }).subscribeOn(Schedulers.boundedElastic());
                    }
                    return Mono.just(app);
                })
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Flux<ApplicationDto> findAll(int page, int size) {
        if (size > 50) {
            return Flux.error(new BadRequestException("Page size cannot exceed 50"));
        }
        return Mono.fromCallable(() -> {
                    Pageable pageable = PageRequest.of(page, size);
                    Page<Application> applicationsPage = applicationRepository.findAllWithDocuments(pageable);
                    List<Application> applications = applicationsPage.getContent();
                    if (applications.isEmpty()) {
                        return List.<ApplicationDto>of();
                    }
                    List<UUID> applicationIds = applications.stream()
                            .map(Application::getId)
                            .collect(Collectors.toList());
                    List<Application> appsWithTags = applicationRepository.findByIdsWithTags(applicationIds);
                    Map<UUID, Set<String>> tagsMap = new HashMap<>();
                    for (Application appWithTags : appsWithTags) {
                        tagsMap.put(appWithTags.getId(), appWithTags.getTags());
                    }
                    return applications.stream()
                            .map(app -> {
                                Set<String> tags = tagsMap.get(app.getId());
                                if (tags != null) {
                                    app.setTags(tags);
                                }
                                return toDto(app);
                            })
                            .collect(Collectors.toList());
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Transactional(readOnly = true)
    public Mono<ApplicationDto> findById(UUID id) {
        return Mono.fromCallable(() -> {
            Optional<Application> appWithDocs = applicationRepository.findByIdWithDocuments(id);
            if (appWithDocs.isEmpty()) {
                throw new NotFoundException("Application with this ID not found");
            }
            Application app = appWithDocs.get();
            Optional<Application> appWithTags = applicationRepository.findByIdWithTags(id);
            appWithTags.ifPresent(appWithTag -> app.setTags(appWithTag.getTags()));
            return toDto(app);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(readOnly = true)
    public Mono<ApplicationPage> streamWithNextCursor(String cursor, int limit) {
        if (limit <= 0) {
            return Mono.error(new BadRequestException("limit must be greater than 0"));
        }
        int capped = Math.min(limit, 50);
        final Instant[] tsHolder = new Instant[1];
        final UUID[] idHolder = new UUID[1];
        if (cursor != null && !cursor.trim().isEmpty()) {
            try {
                CursorUtil.Decoded decoded = CursorUtil.decode(cursor);
                if (decoded != null) {
                    tsHolder[0] = decoded.timestamp;
                    idHolder[0] = decoded.id;
                }
            } catch (Exception e) {
                return Mono.error(new BadRequestException("Invalid cursor format: " + e.getMessage()));
            }
        }
        return Mono.fromCallable(() -> {
            Instant ts = tsHolder[0];
            UUID id = idHolder[0];
            List<UUID> appIds;
            if (ts == null) {
                appIds = applicationRepository.findIdsFirstPage(capped);
            } else {
                appIds = applicationRepository.findIdsByKeyset(ts, id, capped);
            }
            if (appIds.isEmpty()) {
                return new ApplicationPage(List.of(), null);
            }
            List<Application> appsWithDocs = applicationRepository.findByIdsWithDocuments(appIds);
            List<Application> appsWithTags = applicationRepository.findByIdsWithTags(appIds);
            Map<UUID, Application> appMap = new HashMap<>();
            for (Application app : appsWithDocs) {
                appMap.put(app.getId(), app);
            }
            for (Application appWithTags : appsWithTags) {
                Application app = appMap.get(appWithTags.getId());
                if (app != null) {
                    app.setTags(appWithTags.getTags());
                }
            }
            List<Application> apps = appIds.stream()
                    .map(appMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            List<ApplicationDto> dtos = apps.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            String nextCursor = null;
            if (!apps.isEmpty()) {
                Application last = apps.get(apps.size() - 1);
                nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getId());
            }
            return new ApplicationPage(dtos, nextCursor);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> attachTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        return validateActor(applicationId, actorId)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new ForbiddenException("Insufficient permissions"));
                    }
                    return Mono.fromCallable(() -> {
                                Application app = applicationRepository.findByIdWithTags(applicationId)
                                        .orElseThrow(() -> new NotFoundException("Application not found"));
                                try {
                                    List<TagDto> tagDtos = tagServiceClient.createOrGetTagsBatch(tagNames);
                                    Set<String> newTags = tagDtos.stream()
                                            .map(TagDto::getName)
                                            .collect(Collectors.toSet());
                                    app.getTags().addAll(newTags);
                                    applicationRepository.save(app);
                                    log.info("Added {} tags to existed application {}", newTags.size(), applicationId);
                                    return (Void) null;
                                } catch (ServiceUnavailableException e) {
                                    throw new ServiceUnavailableException("Tag service is unavailable now");
                                }
                            }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @Transactional
    public Mono<Void> removeTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        return validateActor(applicationId, actorId)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new ForbiddenException("Insufficient permissions"));
                    }
                    return Mono.fromCallable(() -> {
                        Application app = applicationRepository.findByIdWithTags(applicationId)
                                .orElseThrow(() -> new NotFoundException("Application not found"));
                        tagNames.forEach(app.getTags()::remove);
                        applicationRepository.save(app);
                        log.info("Removed {} tags from application {}", tagNames.size(), applicationId);
                        return (Void) null;
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @Transactional
    public Mono<ApplicationDto> changeStatus(UUID applicationId, String status, UUID actorId) {
        return Mono.fromCallable(() -> {
            try {
                return userServiceClient.getUserRole(actorId);
            } catch (ServiceUnavailableException e) {
                throw new ServiceUnavailableException("User service is unavailable now");
            }
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMap(role -> {
        boolean isManagerOrAdmin = "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name());
        if (!isManagerOrAdmin) {
             return Mono.error(new ForbiddenException("Only admin or manager can change application status"));
        }
         return Mono.fromCallable(() -> {
             Application basicApp = applicationRepository.findById(applicationId)
                     .orElseThrow(() -> new NotFoundException("Application not found"));
             if (basicApp.getApplicantId().equals(actorId) && "ROLE_MANAGER".equals(role.name())) {
                 throw new ConflictException("Managers cannot change status of their own applications");
             }
             Optional<Application> appWithDocs = applicationRepository.findByIdWithDocuments(applicationId);
             Application app = appWithDocs.orElse(basicApp);
             Optional<Application> appWithTags = applicationRepository.findByIdWithTags(applicationId);
             appWithTags.ifPresent(appWithTag -> app.setTags(appWithTag.getTags()));
             ApplicationStatus newStatus;
             try {
                 newStatus = ApplicationStatus.valueOf(status.trim().toUpperCase());
             } catch (IllegalArgumentException e) {
                 throw new ConflictException(
                         "Invalid status. Valid values: DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED");
             }
             ApplicationStatus oldStatus = app.getStatus();
             if (oldStatus != newStatus) {
                 app.setStatus(newStatus);
                 app.setUpdatedAt(Instant.now());
                 applicationRepository.save(app);
                 ApplicationHistory hist = new ApplicationHistory();
                 hist.setId(UUID.randomUUID());
                 hist.setApplication(app);
                 hist.setOldStatus(oldStatus);
                 hist.setNewStatus(newStatus);
                 hist.setChangedBy(role);
                 hist.setChangedAt(Instant.now());
                 applicationHistoryRepository.save(hist);
                 log.info("Application {} status changed from {} to {} by {}",
                         applicationId, oldStatus, newStatus, actorId);
             }
             return toDto(app);
         }).subscribeOn(Schedulers.boundedElastic());
        });
    }

    @Transactional
    public Mono<Void> deleteApplication(UUID applicationId, UUID actorId) {
        return validateActorIsAdmin(actorId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.error(new ForbiddenException("Only admin can delete applications"));
                    }
                    return Mono.fromCallable(() -> {
                        // Удаляем в правильном порядке
                        documentRepository.deleteByApplicationId(applicationId);
                        applicationHistoryRepository.deleteByApplicationId(applicationId);
                        applicationRepository.deleteTagsByApplicationId(applicationId);
                        applicationRepository.deleteById(applicationId);

                        log.info("Application deleted: {}", applicationId);
                        return (Void) null;
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    public Flux<ApplicationHistoryDto> listHistory(UUID applicationId, UUID actorId) {
        return validateActorCanViewHistory(applicationId, actorId)
                .flatMapMany(canView -> {
                    if (!canView) {
                        return Flux.error(new ForbiddenException("Insufficient permissions to view history"));
                    }

                    return Mono.fromCallable(() ->
                                    applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId)
                                            .stream()
                                            .map(this::toHistoryDto)
                                            .collect(Collectors.toList())
                            ).subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                });
    }

    // Внутренний endpoint для user-service
    @Transactional
    public Mono<Void> deleteApplicationsByUserId(UUID userId) {
        return Mono.fromCallable(() -> {
            List<UUID> applicationIds = applicationRepository.findIdsByApplicantId(userId);
            for (UUID appId : applicationIds) {
                documentRepository.deleteByApplicationId(appId);
                applicationHistoryRepository.deleteByApplicationId(appId);
                applicationRepository.deleteTagsByApplicationId(appId);
                applicationRepository.deleteById(appId);

                log.info("Deleted application {} for user {}", appId, userId);
            }
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Внутренний endpoint для product-service
    @Transactional
    public Mono<Void> deleteApplicationsByProductId(UUID productId) {
        return Mono.fromCallable(() -> {
            List<UUID> productIds = applicationRepository.findIdsByProductId(productId);
            for (UUID appId : productIds) {
                documentRepository.deleteByApplicationId(appId);
                applicationHistoryRepository.deleteByApplicationId(appId);
                applicationRepository.deleteTagsByApplicationId(appId);
                applicationRepository.deleteById(appId);

                log.info("Deleted application {} for product {}", appId, productId);
            }
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(readOnly = true)
    public Mono<List<ApplicationInfoDto>> findApplicationsByTag(String tagName) {
        return Mono.fromCallable(() -> {
            try {
                List<Application> applications = applicationRepository.findByTag(tagName);
                List<ApplicationInfoDto> dtos = applications.stream()
                        .map(this::toInfoDto)
                        .collect(Collectors.toList());

                log.info("Found {} applications with tag {}", dtos.size(), tagName);
                return dtos;
            } catch (Exception e) {
                log.error("Failed to get applications by tag {}: {}", tagName, e.getMessage());
                throw new BadRequestException("Failed to get applications by tag: " + e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private ApplicationInfoDto toInfoDto(Application app) {
        ApplicationInfoDto dto = new ApplicationInfoDto();
        dto.setId(app.getId());
        dto.setApplicantId(app.getApplicantId());
        dto.setProductId(app.getProductId());
        dto.setStatus(app.getStatus().toString());
        dto.setCreatedAt(app.getCreatedAt());
        return dto;
    }

    // Вспомогательные методы
    private ApplicationDto toDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setApplicantId(app.getApplicantId());
        dto.setProductId(app.getProductId());
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());

        if (app.getDocuments() != null) {
            List<DocumentDto> docDtos = app.getDocuments().stream()
                    .map(doc -> {
                        DocumentDto docDto = new DocumentDto();
                        docDto.setId(doc.getId());
                        docDto.setFileName(doc.getFileName());
                        docDto.setContentType(doc.getContentType());
                        docDto.setStoragePath(doc.getStoragePath());
                        return docDto;
                    })
                    .collect(Collectors.toList());
            dto.setDocuments(docDtos);
        }

        if (app.getTags() != null) {
            dto.setTags(new ArrayList<>(app.getTags()));
        }

        return dto;
    }

    private ApplicationHistoryDto toHistoryDto(ApplicationHistory history) {
        ApplicationHistoryDto dto = new ApplicationHistoryDto();
        dto.setId(history.getId());
        dto.setApplicationId(history.getApplication().getId());
        dto.setOldStatus(history.getOldStatus());
        dto.setNewStatus(history.getNewStatus());
        dto.setChangedByRole(history.getChangedBy());
        dto.setChangedAt(history.getChangedAt());
        return dto;
    }

    // Обновляем validateActor метод
    private Mono<Boolean> validateActor(UUID applicationId, UUID actorId) {
        return findById(applicationId)
                .flatMap(app ->
                        Mono.fromCallable(() -> {
                            try {
                                return userServiceClient.getUserRole(actorId);
                            } catch (ServiceUnavailableException e) {
                                throw new ServiceUnavailableException("User service is unavailable");
                            }})
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(role -> {
                                    if (app.getApplicantId().equals(actorId)) {
                                        return true;
                                    }
                                    return "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name());
                                })
                                .defaultIfEmpty(false)
                )
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsAdmin(UUID actorId) {
        return Mono.fromCallable(() -> {
            try {
                return userServiceClient.getUserRole(actorId);
            } catch (ServiceUnavailableException e) {
                throw new ServiceUnavailableException("User service is unavailable");
            }}).subscribeOn(Schedulers.boundedElastic())
                .map(role -> "ROLE_ADMIN".equals(role.name()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorCanViewHistory(UUID applicationId, UUID actorId) {
        return findById(applicationId)
                .flatMap(app ->
                        Mono.fromCallable(() -> {
                            try {
                                return userServiceClient.getUserRole(actorId);
                            } catch (ServiceUnavailableException e) {
                                throw new ServiceUnavailableException("User service is unavailable");
                            }}).subscribeOn(Schedulers.boundedElastic())
                                .map(role -> {
                                    if (app.getApplicantId().equals(actorId)) {
                                        return true;
                                    }
                                    return "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name());
                                })
                                .defaultIfEmpty(false)
                )
                .defaultIfEmpty(false);
    }

    public Mono<Long> count() {
        return Mono.fromCallable(applicationRepository::count
        ).subscribeOn(Schedulers.boundedElastic());
    }
}