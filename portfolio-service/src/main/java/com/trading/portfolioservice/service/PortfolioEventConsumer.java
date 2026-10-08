package com.trading.portfolioservice.service;

import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.exception.HoldingNotFoundException;
import com.trading.portfolioservice.exception.InsufficientFundsException;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioEventConsumer {

    private final HoldingRepository holdingRepository;

    @Transactional
    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(@Payload Map<String, Object> payload) {

        try {

            String userId = payload.get("userId").toString();
            String symbol = payload.get("symbol").toString();
            String type = payload.get("type").toString();
            Integer quantity = Integer.parseInt(payload.get("quantity").toString());
            BigDecimal price = new BigDecimal(payload.get("price").toString());

            log.info("Updating portfolio - user : {} {} x {} at {}", userId, symbol, quantity, price);

            if ("BUY".equals(type)) {
                addHolding(userId, symbol, quantity, price);
            } else if ("SELL".equals(type)) {
                removeHolding(userId, symbol, quantity);
            }

        } catch (Exception e) {
            log.error("Error updating portfolio; {}", e.getMessage());
        }

    }

    /**
     * Add or Update holding after buying the order. Calculates new average buy price
     */

    private void addHolding(String userId, String symbol, Integer quantity, BigDecimal price) {

        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElse(null);

        if (holding == null) {
            // First time buying this stock
            holding = new Holding();
            holding.setUserId(userId);
            holding.setSymbol(symbol);
            holding.setCompanyName(symbol);
            holding.setQuantity(quantity);
            holding.setAverageBuyPrice(price);
            holding.setTotalInvested(price.multiply(BigDecimal.valueOf(quantity)));
        } else {
            // Already holding this stock -> Update the average price
            int newQuantity = holding.getQuantity() + quantity;
            BigDecimal newInvested = holding.getTotalInvested().add(price.multiply(BigDecimal.valueOf(quantity)));
            BigDecimal newAveragePrice = newInvested
                    .divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);

            holding.setQuantity(newQuantity);
            holding.setTotalInvested(newInvested);
            holding.setAverageBuyPrice(newAveragePrice);

        }

        holdingRepository.save(holding);

        log.info("Holding updated : {} {} shared of {}", userId, holding.getQuantity(), symbol);


    }

    /**
     * Remove holding after selling the order.
     */

    private void removeHolding(String userId, String symbol, Integer quantity) {

        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new HoldingNotFoundException("404", "Holding not found : " + symbol, HttpStatus.NOT_FOUND));

        if (holding.getQuantity() < quantity) {
            throw new InsufficientFundsException("400", "Insufficient shares to SELL.", HttpStatus.BAD_REQUEST);
        }

        int newQuantity = holding.getQuantity() - quantity;
        if (newQuantity == 0) {
            holdingRepository.delete(holding);
            log.info("Holding removed: {} sold all {} shares", userId, symbol);
        } else {
            BigDecimal newInvested = holding.getAverageBuyPrice().multiply(BigDecimal.valueOf(newQuantity));

            holding.setQuantity(newQuantity);
            holding.setTotalInvested(newInvested);

            holdingRepository.save(holding);
            log.info("Holding reduced: {} now has {} shares of {}", userId, newQuantity, symbol);

        }
    }


}
