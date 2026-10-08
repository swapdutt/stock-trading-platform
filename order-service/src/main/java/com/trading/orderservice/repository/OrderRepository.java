package com.trading.orderservice.repository;

import com.trading.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    Long countByUserIdAndCreatedAtAfter(String userId, LocalDateTime localDateTime);

}