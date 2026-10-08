package com.trading.orderservice.entity;

import com.trading.orderservice.entity.enums.OrderStatus;
import com.trading.orderservice.entity.enums.OrderType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false)
    String userId;

    @Column(nullable = false)
    String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    OrderStatus orderStatus;

    @Column(nullable = false)
    Integer quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    BigDecimal price;

    @Column(nullable = false, precision = 15, scale = 2)
    BigDecimal totalAmount;

    String failureReason;

    // AI-Fraud check result
    Boolean flaggedByAI = Boolean.FALSE;

    String aiReason;

    @CreationTimestamp
    LocalDateTime createdAt;
    LocalDateTime executedAt;


}
