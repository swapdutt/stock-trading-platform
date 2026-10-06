package com.trading.portfolioservice.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ErrorResponse {

    String guid;
    String errorCode;
    String errorMessage;
    Integer statusCode;
    String statusName;
    LocalDateTime timestamp;

}
