package com.trading.userservice.entity;

import com.trading.userservice.entity.enums.UserStatus;
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
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String password;

    @Column(nullable = false)
    String firstName;

    @Column(nullable = false)
    String lastName;

    @Column(nullable = false, precision = 15, scale = 2)
    BigDecimal walletBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    LocalDateTime createAt;

}
