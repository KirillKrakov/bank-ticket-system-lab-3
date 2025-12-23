package com.example.userservice.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebExchangeBindException;
import java.time.Instant;
import java.util.*;
import org.springframework.validation.FieldError;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import reactor.core.publisher.Mono;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    public Mono<ResponseEntity<Object>> handleBadRequest(BadRequestException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<Object>> handleConflict(UnauthorizedException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public Mono<ResponseEntity<Object>> handleConflict(ForbiddenException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(NotFoundException.class)
    public Mono<ResponseEntity<Object>> handleNotFound(NotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(ConflictException.class)
    public Mono<ResponseEntity<Object>> handleConflict(ConflictException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public Mono<ResponseEntity<Object>> handleUnavailableService(ServiceUnavailableException ex) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(),null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Object>> handleConstraint(DataIntegrityViolationException ex) {
        return buildError(HttpStatus.CONFLICT, "Database constraint violation", null);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    protected Mono<ResponseEntity<Object>> handleValidation(WebExchangeBindException ex) {
        List<Map<String,String>> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            Map<String,String> e = new HashMap<>();
            e.put("field", fe.getField());
            e.put("message", fe.getDefaultMessage());
            errors.add(e);
        }
        return buildError(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Mono<ResponseEntity<Object>> handleUUIDException(MethodArgumentTypeMismatchException ex) {
        if (ex.getRequiredType() == UUID.class) {
            throw new BadRequestException("Invalid UUID: " + ex.getValue());
        }
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Object>> handleGeneric(Exception ex) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(),null);
    }

    private Mono<ResponseEntity<Object>> buildError(HttpStatus status, String message, List<Map<String,String>> errors) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        if (errors != null && !errors.isEmpty()) {
            body.put("errors", errors);
        }
        return Mono.just(new ResponseEntity<>(body, new HttpHeaders(), status));
    }
}