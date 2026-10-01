package com.libera.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** La operación es válida pero choca con el estado actual (ya vendido, duplicado, noches ocupadas, etc.). */
@ResponseStatus(HttpStatus.CONFLICT)
public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
