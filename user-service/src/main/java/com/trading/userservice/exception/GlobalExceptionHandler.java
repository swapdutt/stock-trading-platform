package com.trading.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException exception) {
        var guid = UUID.randomUUID().toString();
        log.info("Global Exception: Email Already Exists Exception : Error GUID = {}, Error message = {}", guid, exception.getMessage());

        var response = new ErrorResponse(
                guid, exception.getErrorCode(), exception.getErrorMessage(),
                exception.getStatus().value(), exception.getStatus().name(),
                LocalDateTime.now(ZoneId.systemDefault()));

        return new ResponseEntity<>(response, exception.getStatus());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(UserNotFoundException exception) {
        var guid = UUID.randomUUID().toString();
        log.info("Global Exception: User Not Found Exception : Error GUID = {}, Error message = {}", guid, exception.getMessage());

        var response = new ErrorResponse(
                guid, exception.getErrorCode(), exception.getErrorMessage(),
                exception.getStatus().value(), exception.getStatus().name(),
                LocalDateTime.now(ZoneId.systemDefault()));

        return new ResponseEntity<>(response, exception.getStatus());
    }

}
