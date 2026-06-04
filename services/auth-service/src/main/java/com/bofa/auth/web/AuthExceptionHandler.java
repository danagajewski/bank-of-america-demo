package com.bofa.auth.web;

import com.bofa.auth.InvalidCredentialsException;
import com.bofa.auth.InvalidTokenException;
import com.bofa.auth.TokenExpiredException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps authentication failures to 401 responses without leaking detail. */
@RestControllerAdvice
public class AuthExceptionHandler {

    @ExceptionHandler({
            TokenExpiredException.class,
            InvalidTokenException.class,
            InvalidCredentialsException.class
    })
    public ResponseEntity<Map<String, String>> handleUnauthorized(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthorized", "reason", ex.getMessage()));
    }
}
