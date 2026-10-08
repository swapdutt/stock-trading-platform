package com.trading.notificationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class NotificationService {

    private static final String USER_ID = "userId";

    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(@Payload Map<String, Object> payload) {

        try {

            String userId = payload.get(USER_ID).toString();
            String type = payload.get("type").toString();
            String symbol = payload.get("symbol").toString();
            Object quantity = payload.get("quantity");
            Object price = payload.get("price");
            Object totalAmount = payload.get("totalAmount");

            sendNotification(userId, type.equals("BUY") ? "ORDER_EXECUTED - BUY" : "ORDER_EXECUTED - SELL",
                    String.format("%s %s shares of %s at %s. Total: %s", type, quantity, symbol, price, totalAmount));

        } catch (Exception e) {
            log.error("Error in consuming order execution: {}", e.getMessage());
        }


    }

    @KafkaListener(topics = "order.failed")
    public void consumeOrderFailed(@Payload Map<String, Object> payload) {

        try {

            String userId = payload.get(USER_ID).toString();
            String symbol = payload.get("symbol").toString();
            String reason = payload.get("reason").toString();

            sendNotification(userId, "Order Failed",
                    String.format("Your order for %s has failed. Reason: %s", symbol, reason));

        } catch (Exception e) {
            log.error("Error in consuming order failure: {}", e.getMessage());
        }


    }

    @KafkaListener(topics = "user.register")
    public void consumeUserRegistration(@Payload Map<String, Object> payload) {

        try {

            String userId = payload.get(USER_ID).toString();
            String firstName = payload.get("firstName").toString();
            Object walletBalance = payload.get("walletBalance");

            sendNotification(userId, "Welcome to Stock Trading Platform",
                    String.format("Welcome %s! %s added to your wallet", firstName, walletBalance));

        } catch (Exception e) {
            log.error("Error in consuming user registration: {}", e.getMessage());
        }

    }

    private void sendNotification(String userId, String title, String message) {

        log.info("------------------------------------------------------------");
        log.info("NOTIFICATION SENT");
        log.info("TO USER: {}", userId);
        log.info("TITLE: {}", title);
        log.info("MESSAGE: {}", message);
        log.info("------------------------------------------------------------");


        /**
         * TODO : Implementation of sending notification through Email & SMS will be done later stage
         */


    }


}
