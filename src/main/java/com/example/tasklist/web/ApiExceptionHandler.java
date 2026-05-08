package com.example.tasklist.web;

import com.example.tasklist.lmstudio.LmStudioException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> badRequest(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, request.getRequestURI(),
                detail.isEmpty() ? "Validation failed" : detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> illegalArgument(IllegalArgumentException ex,
                                                               HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> notFound(NoSuchElementException ex,
                                                         HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, request.getRequestURI(), "Resource not found");
    }

    @ExceptionHandler(LmStudioException.class)
    public ResponseEntity<Map<String, Object>> lmStudio(LmStudioException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatus().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return build(status, request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, request.getRequestURI(), ex.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> missingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, request.getRequestURI(),
                "Отсутствует обязательный параметр: " + ex.getParameterName());
    }

    private static ResponseEntity<Map<String, Object>> build(HttpStatus status, String path, String detail) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("path", path);
        body.put("detail", detail);
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                .body(body);
    }
}
