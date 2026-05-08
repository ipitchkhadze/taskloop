package com.example.tasklist.lmstudio;

import org.springframework.http.HttpStatus;

public class LmStudioException extends RuntimeException {

    private final HttpStatus status;

    public LmStudioException(String message) {
        this(message, HttpStatus.BAD_GATEWAY, null);
    }

    public LmStudioException(String message, Throwable cause) {
        this(message, HttpStatus.BAD_GATEWAY, cause);
    }

    public LmStudioException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status != null ? status : HttpStatus.BAD_GATEWAY;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
