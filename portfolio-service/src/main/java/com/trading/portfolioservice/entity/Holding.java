package com.trading.portfolioservice.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "holdings", uniqueConstraints = @UniqueConstraint(
        columnNames = {"user_id", "symbol"}
))
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "user_id", nullable = false)
    String userId;

    @Column(nullable = false)
    String symbol;

    @Column(nullable = false)
    String companyName;

    Integer quantity;

    @Column(precision = 15, scale = 2)
    BigDecimal averageBuyPrice;

    @Column(precision = 15, scale = 2)
    BigDecimal totalInvested;

    @UpdateTimestamp
    LocalDateTime updatedAt;

}
