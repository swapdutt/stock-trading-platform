package com.trading.orderservice.service;

import com.trading.orderservice.client.AIServiceClient;
import com.trading.orderservice.client.MarketDataClient;
import com.trading.orderservice.client.UserServiceClient;
import com.trading.orderservice.dto.OrderRequest;
import com.trading.orderservice.entity.Order;
import com.trading.orderservice.entity.enums.OrderStatus;
import com.trading.orderservice.entity.enums.OrderType;
import com.trading.orderservice.exception.OrderNotFoundException;
import com.trading.orderservice.repository.OrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MarketDataClient marketDataClient;
    private final UserServiceClient userServiceClient;
    private final AIServiceClient aiServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_EXECUTED_TOPIC = "order.executed";
    private static final String ORDER_FAILED_TOPIC = "order.failed";
    private static final String ORDER_FLAGGED_TOPIC = "order.flagged";

    /**
     * Place a BUY/SELL order
     * <p>
     * FLOW ->
     * 1. Get current price from market
     * 2. Calculate the total amount
     * 3. Execute
     * 4. Publish order event to Kafka
     * </p>
     */

    public Order placeOrder(@Valid OrderRequest request) {

        log.info("Placing {} order : {} x {} for user: {}",
                request.getOrderType(), request.getQuantity(), request.getSymbol(), request.getUserId());

        Map<String, Object> priceData = marketDataClient.getStockPrice(request.getSymbol());
        BigDecimal currentPrice = new BigDecimal(priceData.get("price").toString());
        BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        // Create order as AI check in start
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setSymbol(request.getSymbol());
        order.setOrderType(request.getOrderType());
        order.setOrderStatus(OrderStatus.AI_CHECK);
        order.setQuantity(request.getQuantity());
        order.setPrice(currentPrice);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        log.info("Order Created : {}", savedOrder.getId());

        // AI fraud check
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("orderId", savedOrder.getId());
        orderData.put("userId", savedOrder.getUserId());
        orderData.put("symbol", savedOrder.getSymbol());
        orderData.put("type", savedOrder.getOrderType());
        orderData.put("quantity", savedOrder.getQuantity());
        orderData.put("totalAmount", savedOrder.getTotalAmount());
        orderData.put("price", savedOrder.getPrice());

        Long recentOrderCount = orderRepository.countByUserIdAndCreatedAtAfter(request.getUserId(), LocalDateTime.now(ZoneId.systemDefault()).minusHours(1));
        orderData.put("recentOrderCount", recentOrderCount);

        try {

            Map<String, Object> aiResult = aiServiceClient.checkFraud(orderData);
            Boolean isSuspicious = (Boolean) aiResult.getOrDefault("isSuspicious", Boolean.FALSE);
            String aiReason = (String) aiResult.getOrDefault("reason", "");
            Double fraudScore = Double.parseDouble(aiResult.getOrDefault("fraudScore", "0.0").toString());

            log.info("AI Fraud Check - orderId: {} suspicious: {} score: {}", savedOrder.getId(), isSuspicious, fraudScore);

            if (isSuspicious == Boolean.TRUE) {
                // Flag order - don't execute
                savedOrder.setOrderStatus(OrderStatus.FLAGGED);
                savedOrder.setFlaggedByAI(Boolean.TRUE);
                savedOrder.setAiReason(aiReason);
                savedOrder.setFailureReason("Flagged by AI : " +aiReason);
                orderRepository.save(savedOrder);

                // Publish Flagged Event
                publishOrderEvent(ORDER_FLAGGED_TOPIC, savedOrder, aiReason);

                log.warn("Order FLAGGED by AI: {} reason: {}", savedOrder.getId(), aiReason);
                return savedOrder;
            }

        } catch (Exception e) {
            log.warn("AI service unavailable — proceeding without check: {}", e.getMessage());
        }

        try {

            if(request.getOrderType().equals(OrderType.BUY)) {
                userServiceClient.deductFunds(request.getUserId(), totalAmount);
            } else {
                userServiceClient.creditFunds(request.getUserId(), totalAmount);
            }

            savedOrder.setOrderStatus(OrderStatus.EXECUTED);
            savedOrder.setExecutedAt(LocalDateTime.now(ZoneId.systemDefault()));
            orderRepository.save(savedOrder);

            log.info("Order EXECUTED : {} {} x {} at {} ",
                    request.getOrderType(), request.getQuantity(), request.getSymbol(), currentPrice);

            publishOrderEvent(ORDER_EXECUTED_TOPIC, savedOrder, null);

        } catch (Exception e) {
            savedOrder.setOrderStatus(OrderStatus.FAILED);
            savedOrder.setFailureReason(e.getMessage());
            orderRepository.save(savedOrder);

            log.error("Order FAILED : {} reason : {}", savedOrder.getId(), e.getMessage());

            publishOrderEvent(ORDER_FAILED_TOPIC, savedOrder, e.getMessage());
        }

        return savedOrder;

    }

    public Order getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("404", "Order Not Found: " + orderId, HttpStatus.NOT_FOUND));
    }

    public List<Order> getCurrentUserOrders(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }


    private void publishOrderEvent(String topic, Order order, String reason) {

        Map<String, Object> orderEvent = new HashMap<>();
        orderEvent.put("orderId", order.getId());
        orderEvent.put("userId", order.getUserId());
        orderEvent.put("symbol", order.getSymbol());
        orderEvent.put("type", order.getOrderType().name());
        orderEvent.put("status", order.getOrderStatus().name());
        orderEvent.put("quantity", order.getQuantity());
        orderEvent.put("price", order.getPrice());
        orderEvent.put("totalAmount", order.getTotalAmount());

        if (reason != null) {
            orderEvent.put("reason", reason);
        }

        kafkaTemplate.send(topic, order.getId(), orderEvent);

        log.info("Event Published : {} for order : {}", topic, order.getId());

    }
}
