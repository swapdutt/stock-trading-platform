package com.trading.marketdataservice.exception;

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

    @ExceptionHandler(StockNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStockNotFoundException(StockNotFoundException exception) {
        var guid = UUID.randomUUID().toString();
        log.info("Global Exception: Stock Not Found Exception : Error GUID = {}, Error message = {}", guid, exception.getMessage());

        var response = new ErrorResponse(
                guid, exception.getErrorCode(), exception.getErrorMessage(),
                exception.getStatus().value(), exception.getStatus().name(),
                LocalDateTime.now(ZoneId.systemDefault()));

        return new ResponseEntity<>(response, exception.getStatus());
    }

}
