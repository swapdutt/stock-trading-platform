package com.trading.orderservice.dto;

import com.trading.orderservice.entity.enums.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OrderRequest {

    String userId;

    @NotBlank(message = "Stock symbol is required")
    String symbol;

    @NotBlank(message = "Order type is required - BUY or SELL")
    OrderType orderType;

    @Min(value = 1, message = "Quantity must be at least 1")
    Integer quantity;

}
