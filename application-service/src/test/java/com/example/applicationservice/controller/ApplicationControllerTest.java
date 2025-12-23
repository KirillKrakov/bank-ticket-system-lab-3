package com.example.applicationservice.controller;

import com.example.applicationservice.controller.ApplicationController;
import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.service.ApplicationService;
import com.example.applicationservice.util.ApplicationPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationControllerTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ApplicationController applicationController;

    private ApplicationDto createSampleApplicationDto() {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(UUID.randomUUID());
        dto.setApplicantId(UUID.randomUUID());
        dto.setProductId(UUID.randomUUID());
        dto.setStatus(com.example.applicationservice.model.enums.ApplicationStatus.SUBMITTED);
        dto.setCreatedAt(Instant.now());
        return dto;
    }

    private ApplicationRequest createSampleApplicationRequest() {
        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(UUID.randomUUID());
        request.setProductId(UUID.randomUUID());
        return request;
    }

    // -----------------------
    // createApplication tests
    // -----------------------
    @Test
    public void createApplication_success_returnsCreated() {
        ApplicationRequest request = createSampleApplicationRequest();
        ApplicationDto responseDto = createSampleApplicationDto();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(applicationController.createApplication(request))
                .expectNext(responseDto)
                .verifyComplete();
    }

    @Test
    public void createApplication_serviceThrowsBadRequest_returnsError() {
        ApplicationRequest request = createSampleApplicationRequest();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.error(new BadRequestException("Invalid request")));

        StepVerifier.create(applicationController.createApplication(request))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void createApplication_serviceThrowsNotFound_returnsError() {
        ApplicationRequest request = createSampleApplicationRequest();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.createApplication(request))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void createApplication_serviceThrowsServiceUnavailable_returnsError() {
        ApplicationRequest request = createSampleApplicationRequest();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.error(new ServiceUnavailableException("Service unavailable")));

        StepVerifier.create(applicationController.createApplication(request))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    // -----------------------
    // listApplications tests
    // -----------------------
    @Test
    public void listApplications_validParameters_returnsFlux() {
        ApplicationDto dto1 = createSampleApplicationDto();
        ApplicationDto dto2 = createSampleApplicationDto();

        when(applicationService.findAll(0, 20))
                .thenReturn(Flux.just(dto1, dto2));

        StepVerifier.create(applicationController.listApplications(0, 20))
                .expectNext(dto1)
                .expectNext(dto2)
                .verifyComplete();
    }

    @Test
    public void listApplications_sizeExceedsMax_returnsBadRequest() {
        StepVerifier.create(applicationController.listApplications(0, 100))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void listApplications_negativePage_returnsResults() {
        ApplicationDto dto = createSampleApplicationDto();

        when(applicationService.findAll(-1, 10))
                .thenReturn(Flux.just(dto));

        StepVerifier.create(applicationController.listApplications(-1, 10))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    public void listApplications_serviceThrowsBadRequest_returnsError() {
        when(applicationService.findAll(0, 20))
                .thenReturn(Flux.error(new BadRequestException("Invalid parameters")));

        StepVerifier.create(applicationController.listApplications(0, 20))
                .expectError(BadRequestException.class)
                .verify();
    }

    // -----------------------
    // getApplication tests
    // -----------------------
    @Test
    public void getApplication_found_returnsDto() {
        UUID appId = UUID.randomUUID();
        ApplicationDto dto = createSampleApplicationDto();
        dto.setId(appId);

        when(applicationService.findById(appId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(applicationController.getApplication(appId))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    public void getApplication_notFound_returnsError() {
        UUID appId = UUID.randomUUID();

        when(applicationService.findById(appId))
                .thenReturn(Mono.error(new NotFoundException("Application not found")));

        StepVerifier.create(applicationController.getApplication(appId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void getApplication_serviceThrowsException_returnsError() {
        UUID appId = UUID.randomUUID();

        when(applicationService.findById(appId))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(applicationController.getApplication(appId))
                .expectError(RuntimeException.class)
                .verify();
    }

    // -----------------------
    // streamApplications tests
    // -----------------------
    @Test
    public void streamApplications_success_returnsPage() {
        ApplicationDto dto = createSampleApplicationDto();
        ApplicationPage page = new ApplicationPage(List.of(dto), "next-cursor-123");

        when(applicationService.streamWithNextCursor(null, 20))
                .thenReturn(Mono.just(page));

        StepVerifier.create(applicationController.streamApplications(null, 20))
                .expectNext(page)
                .verifyComplete();
    }

    @Test
    public void streamApplications_withCursor_success_returnsPage() {
        ApplicationDto dto = createSampleApplicationDto();
        ApplicationPage page = new ApplicationPage(List.of(dto), "next-cursor-456");
        String cursor = "cursor123";

        when(applicationService.streamWithNextCursor(cursor, 20))
                .thenReturn(Mono.just(page));

        StepVerifier.create(applicationController.streamApplications(cursor, 20))
                .expectNext(page)
                .verifyComplete();
    }

    @Test
    public void streamApplications_limitExceedsMax_returnsBadRequest() {
        StepVerifier.create(applicationController.streamApplications(null, 100))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void streamApplications_serviceThrowsBadRequest_returnsError() {
        when(applicationService.streamWithNextCursor(null, 20))
                .thenReturn(Mono.error(new BadRequestException("Invalid cursor")));

        StepVerifier.create(applicationController.streamApplications(null, 20))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void streamApplications_negativeLimit_handledByService() {
        when(applicationService.streamWithNextCursor(null, -1))
                .thenReturn(Mono.error(new BadRequestException("Invalid limit")));

        StepVerifier.create(applicationController.streamApplications(null, -1))
                .expectError(BadRequestException.class)
                .verify();
    }

    // -----------------------
    // addTags tests
    // -----------------------
    @Test
    public void addTags_success_returnsVoid() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1", "tag2");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .verifyComplete();
    }

    @Test
    public void addTags_emptyTagsList_success() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of();

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .verifyComplete();
    }

    @Test
    public void addTags_forbidden_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void addTags_notFound_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new NotFoundException("Application not found")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void addTags_conflict_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ConflictException("Tag conflict")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    public void addTags_serviceUnavailable_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ServiceUnavailableException("Tag service unavailable")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    // -----------------------
    // removeTags tests
    // -----------------------
    @Test
    public void removeTags_success_returnsVoid() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .verifyComplete();
    }

    @Test
    public void removeTags_forbidden_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void removeTags_notFound_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void removeTags_serviceUnavailable_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ServiceUnavailableException("User service unavailable")));

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    // -----------------------
    // changeStatus tests
    // -----------------------
    @Test
    public void changeStatus_success_returnsDto() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";
        ApplicationDto dto = createSampleApplicationDto();

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .expectNext(dto)
                .verifyComplete();
    }

    @Test
    public void changeStatus_forbidden_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void changeStatus_notFound_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void changeStatus_conflict_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ConflictException("Status conflict")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    public void changeStatus_emptyStatus_handledByService() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ConflictException("Invalid status")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .expectError(ConflictException.class)
                .verify();
    }

    @Test
    public void changeStatus_serviceUnavailable_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ServiceUnavailableException("User service unavailable")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    // -----------------------
    // deleteApplication tests
    // -----------------------
    @Test
    public void deleteApplication_success_returnsVoid() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .verifyComplete();
    }

    @Test
    public void deleteApplication_forbidden_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void deleteApplication_notFound_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void deleteApplication_serviceUnavailable_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.error(new ServiceUnavailableException("User service unavailable")));

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    // -----------------------
    // getApplicationHistory tests
    // -----------------------
    @Test
    public void getApplicationHistory_success_returnsFlux() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApplicationHistoryDto historyDto = new ApplicationHistoryDto();
        historyDto.setId(UUID.randomUUID());

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.just(historyDto));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .expectNext(historyDto)
                .verifyComplete();
    }

    @Test
    public void getApplicationHistory_forbidden_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .expectError(ForbiddenException.class)
                .verify();
    }

    @Test
    public void getApplicationHistory_notFound_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    public void getApplicationHistory_serviceUnavailable_returnsError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.error(new ServiceUnavailableException("User service unavailable")));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .expectError(ServiceUnavailableException.class)
                .verify();
    }

    // -----------------------
    // internal endpoints tests
    // -----------------------
    @Test
    public void deleteApplicationsByUserId_success_returnsVoid() {
        UUID userId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByUserId(userId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.deleteApplicationsByUserId(userId))
                .verifyComplete();
    }

    @Test
    public void deleteApplicationsByUserId_error_returnsError() {
        UUID userId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByUserId(userId))
                .thenReturn(Mono.error(new RuntimeException("Error")));

        StepVerifier.create(applicationController.deleteApplicationsByUserId(userId))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    public void deleteApplicationsByProductId_success_returnsVoid() {
        UUID productId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByProductId(productId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.deleteApplicationsByProductId(productId))
                .verifyComplete();
    }

    @Test
    public void deleteApplicationsByProductId_error_returnsError() {
        UUID productId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByProductId(productId))
                .thenReturn(Mono.error(new RuntimeException("Error")));

        StepVerifier.create(applicationController.deleteApplicationsByProductId(productId))
                .expectError(RuntimeException.class)
                .verify();
    }

    // -----------------------
    // getApplicationsByTag tests
    // -----------------------
    @Test
    public void getApplicationsByTag_success_returnsList() {
        String tagName = "important";
        ApplicationInfoDto infoDto = new ApplicationInfoDto();
        infoDto.setId(UUID.randomUUID());

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.just(List.of(infoDto)));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .expectNext(List.of(infoDto))
                .verifyComplete();
    }

    @Test
    public void getApplicationsByTag_badRequest_returnsError() {
        String tagName = "invalid";

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.error(new BadRequestException("Invalid tag")));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void getApplicationsByTag_emptyTag_handledByService() {
        String tagName = "";

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.error(new BadRequestException("Empty tag")));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .expectError(BadRequestException.class)
                .verify();
    }

    @Test
    public void getApplicationsByTag_genericError_returnsError() {
        String tagName = "test";

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .expectError(RuntimeException.class)
                .verify();
    }
}