package com.trading.marketdataservice.dto;

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
public class StockPrice {

    String symbol;
    String companyName;
    BigDecimal price;
    BigDecimal change;
    BigDecimal changePercent;
    BigDecimal high;
    BigDecimal low;
    BigDecimal open;
    Long volume;
    LocalDateTime timestamp;

}
