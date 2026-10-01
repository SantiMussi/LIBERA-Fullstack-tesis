package com.libera.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSplitBookingException extends RuntimeException {
    public InvalidSplitBookingException(String message) {
        super(message);
    }
}
