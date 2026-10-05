package com.trading.userservice.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthResponse {

    String userId;
    String email;
    String password;
    String firstName;
    String lastName;
    BigDecimal walletBalance;
    String accessToken;
    String refreshToken;
    String tokenType = "Bearer";

}
