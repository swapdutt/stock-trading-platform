package com.trading.portfolioservice.exception;

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

    @ExceptionHandler(HoldingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleHoldingNotFoundException(HoldingNotFoundException exception) {
        var guid = UUID.randomUUID().toString();
        log.info("Global Exception: Holding Not Found Exception : Error GUID = {}, Error message = {}", guid, exception.getMessage());

        var response = new ErrorResponse(
                guid, exception.getErrorCode(), exception.getErrorMessage(),
                exception.getStatus().value(), exception.getStatus().name(),
                LocalDateTime.now(ZoneId.of("IST")));

        return new ResponseEntity<>(response, exception.getStatus());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFundsException(InsufficientFundsException exception) {
        var guid = UUID.randomUUID().toString();
        log.info("Global Exception: Insufficient Funds Exception : Error GUID = {}, Error message = {}", guid, exception.getMessage());

        var response = new ErrorResponse(
                guid, exception.getErrorCode(), exception.getErrorMessage(),
                exception.getStatus().value(), exception.getStatus().name(),
                LocalDateTime.now(ZoneId.of("IST")));

        return new ResponseEntity<>(response, exception.getStatus());
    }

}
