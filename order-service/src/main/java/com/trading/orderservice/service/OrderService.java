package com.trading.orderservice.service;

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
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_EXECUTED_TOPIC = "order.executed";
    private static final String ORDER_FAILED_TOPIC = "order.failed";

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

        /**
         * AI service integration is pending for scanning the trade
         * whether the trade is fraud one or not. This will be implemented
         * when the AI service implementation will start.
         */

        log.info("Placing {} order : {} x {} for user: {}",
                request.getOrderType(), request.getQuantity(), request.getSymbol(), request.getUserId());

        Map<String, Object> priceData = marketDataClient.getStockPrice(request.getSymbol());
        BigDecimal currentPrice = new BigDecimal(priceData.get("price").toString());
        BigDecimal totalAmount = currentPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        // Create order
        Order savedOrder = orderRepository.save(Order.builder()
                .userId(request.getUserId())
                .symbol(request.getSymbol())
                .orderType(request.getOrderType())
                .orderStatus(OrderStatus.PENDING)
                .quantity(request.getQuantity())
                .price(currentPrice)
                .totalAmount(totalAmount).build());

        log.info("Order Created : {}", savedOrder.getId());

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
