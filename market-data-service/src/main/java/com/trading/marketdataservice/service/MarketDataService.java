package com.trading.marketdataservice.service;

import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.exception.StockNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Simulates real time stock price updates.
 * In production → connects to market data provider (NSE, BSE, NYSE)
 * For demo → generates realistic price movements using random walk algorithm
 * <p>
 * Every 2 seconds:
 * 1. Update stock prices (random walk)
 * 2. Cache latest prices in Redis
 * 3. Broadcast via WebSocket → clients see real time updates
 * 4. Publish to Kafka → other services react to price changes
 * </p>
 *
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketDataService {

    private final Random random = new Random();
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final String PRICE_UPDATED_TOPIC = "stock.price.updated";

    private final Map<String, StockPrice> stockPrices = new HashMap<>() {{
        put("RELIANCE", new StockPrice("RELIANCE",
                "Reliance Industries", BigDecimal.valueOf(2850.00),
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(2900.00),
                BigDecimal.valueOf(2800.00),
                BigDecimal.valueOf(2840.00), 1500000L,
                LocalDateTime.now()));
        put("TCS", new StockPrice("TCS",
                "Tata Consultancy Services", BigDecimal.valueOf(3920.00),
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(3980.00),
                BigDecimal.valueOf(3890.00),
                BigDecimal.valueOf(3910.00), 800000L,
                LocalDateTime.now()));
        put("INFY", new StockPrice("INFY",
                "Infosys", BigDecimal.valueOf(1650.00),
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1680.00),
                BigDecimal.valueOf(1630.00),
                BigDecimal.valueOf(1645.00), 1200000L,
                LocalDateTime.now()));
        put("AAPL", new StockPrice("AAPL",
                "Apple Inc", BigDecimal.valueOf(189.50),
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(191.00),
                BigDecimal.valueOf(188.00),
                BigDecimal.valueOf(189.00), 5000000L,
                LocalDateTime.now()));
        put("GOOGL", new StockPrice("GOOGL",
                "Alphabet Inc", BigDecimal.valueOf(141.80),
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(143.00),
                BigDecimal.valueOf(140.50),
                BigDecimal.valueOf(141.20), 3000000L,
                LocalDateTime.now()));
        put("MSFT", new StockPrice("MSFT",
                "Microsoft Corp", BigDecimal.valueOf(378.90),
        BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(381.00),
                BigDecimal.valueOf(377.00),
                BigDecimal.valueOf(378.00), 4000000L,
                LocalDateTime.now()));
    }} ;


    /**
     * Scheduled price update - every 2 seconds
     */

    @Scheduled(fixedRateString = "${market.price-update-interval}")
    public void updatePrices() {

        stockPrices.forEach((symbol, currentPrice) -> {
            BigDecimal oldPrice = currentPrice.getPrice();

            // Random price movement — up or down by 0-0.5%
            double priceMovement = (random.nextDouble() - 0.5) * 0.5;
            BigDecimal priceChange = oldPrice.multiply(BigDecimal.valueOf(priceMovement / 100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newPrice = oldPrice.add(priceChange).setScale(2, RoundingMode.HALF_UP);

            // Ensure price doesn't go negative
            if (newPrice.compareTo(BigDecimal.valueOf(1)) < 0) {
                newPrice = BigDecimal.valueOf(1);
            }

            BigDecimal changeAmount = newPrice.subtract(currentPrice.getOpen())
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal changePercent = changeAmount.divide(currentPrice.getOpen(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // update high and low prices
            BigDecimal newHigh = newPrice.compareTo(currentPrice.getHigh()) > 0
                    ? newPrice : currentPrice.getHigh();
            BigDecimal newLow = newPrice.compareTo(currentPrice.getLow()) > 0
                    ? newPrice : currentPrice.getLow();

            currentPrice.setPrice(newPrice);
            currentPrice.setChange(changeAmount);
            currentPrice.setChangePercent(changePercent);
            currentPrice.setHigh(newHigh);
            currentPrice.setLow(newLow);
            currentPrice.setTimestamp(LocalDateTime.now(ZoneId.systemDefault()));

            /*
             * Redis caching part
             */

            // 1. Cache in Redis
            redisTemplate.opsForValue().set(PRICE_KEY_PREFIX + symbol, currentPrice);

            // 2. Broadcast via WebSocket -> all subscribed clients
            messagingTemplate.convertAndSend("/topic/prices" + symbol, currentPrice);

            // 3. Publish to Kafka → Order Service needs current prices
            Map<String, Object> priceUpdatedEvent = new HashMap<>();
            priceUpdatedEvent.put("symbol", symbol);
            priceUpdatedEvent.put("price", newPrice);
            priceUpdatedEvent.put("timestamp", LocalDateTime.now(ZoneId.systemDefault()));

            kafkaTemplate.send(PRICE_UPDATED_TOPIC, symbol, priceUpdatedEvent);
        });

        log.debug("Prices updated for {} stocks", stockPrices.size());

    }

    /**
     * Get all active stock prices
     */

    public List<StockPrice> getAllPrices() {
        return stockPrices.values().stream().toList();
    }

    /**
     * Get current stock price
     */

    public StockPrice getStockPrice(String symbol) {

        Object cached = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + symbol);
        if (cached != null) {
            return (StockPrice) cached;
        }

        // Fallback
        StockPrice priceDto = stockPrices.get(symbol.toUpperCase());
        if (priceDto == null) {
            throw new StockNotFoundException("404", "Stock not found: " + symbol, HttpStatus.NOT_FOUND);

        }
        return priceDto;
    }

}
