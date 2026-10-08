package com.trading.userservice.dto;

import com.trading.userservice.entity.enums.UserStatus;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {

    String id;
    String email;
    String firstName;
    String lastName;
    BigDecimal walletBalance;
    UserStatus status;
    LocalDateTime createdAt;

}
