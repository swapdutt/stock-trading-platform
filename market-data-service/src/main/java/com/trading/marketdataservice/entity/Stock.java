package com.trading.marketdataservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "stocks")
public class Stock {

    @Id
    @Column(nullable = false, unique = true)
    String symbol;

    @Column(nullable = false)
    String companyName;

    @Column(nullable = false, precision = 15, scale = 2)
    BigDecimal initialPrice;

    @Column(nullable = false)
    String exchange;

    @Column(nullable = false)
    String currency;

    Boolean active = Boolean.TRUE;

}
