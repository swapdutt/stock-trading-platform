package com.trading.marketdataservice.service;

import com.trading.marketdataservice.dto.StockPriceDto;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.exception.StockNotFoundException;
import com.trading.marketdataservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
@Order(2)
public class MarketDataService implements CommandLineRunner {

    private final StockRepository stockRepository;
    private final Map<String, StockPriceDto> stockPrices = new ConcurrentHashMap<>(); // ConcurrentHashMap is used here for thread safety
    private final Random random = new Random();
    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final String PRICE_UPDATED_TOPIC = "stock.price.updated";


    /**
     * Initialize the stock prices
     */

    @Override
    public void run(String... args) {
        log.info("Loading stock price");
        List<Stock> stocks = stockRepository.findByActiveTrue();

        if (stocks.isEmpty()) {
            log.warn("No Active stocks found");
            return;
        }

        stocks.forEach(stock -> {
            StockPriceDto dto = new StockPriceDto();
            dto.setSymbol(stock.getSymbol());
            dto.setCompanyName(stock.getCompanyName());
            dto.setPrice(stock.getInitialPrice());
            dto.setChange(BigDecimal.ZERO);
            dto.setChangePercent(BigDecimal.ZERO);
            dto.setHigh(stock.getInitialPrice());
            dto.setLow(stock.getInitialPrice());
            dto.setOpen(stock.getInitialPrice());
            dto.setVolume(0L);
            dto.setTimestamp(LocalDateTime.now(ZoneId.systemDefault()));

            stockPrices.put(stock.getSymbol(), dto);
            log.info("Loaded Stock : {} at {}", stock.getSymbol(), stock.getInitialPrice());

        });

        log.info("Initialized Stocks : {}", stockPrices.size());

    }

    /**
     * Scheduled price update - every 2 seconds
     */

    @Scheduled(fixedRateString = "${market.price-update-interval}")
    public void updatePrices() {
        log.info("Updating stock price");

        if (stockPrices.isEmpty()) {
            log.warn("No Stocks loaded");
            return;
        }

        stockPrices.forEach((symbol, currentPrice) -> {
            BigDecimal oldPrice = currentPrice.getPrice();

            // +- 0.5 price movement
            double priceMovement = (random.nextDouble() - 0.5) * 0.5;
            BigDecimal priceChange = oldPrice.multiply(BigDecimal.valueOf(priceMovement / 100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newPrice = oldPrice.add(priceChange).setScale(2, RoundingMode.HALF_UP);

            // Prevent negative price
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

            // 1. Caching in Redis
            redisTemplate.opsForValue().set(PRICE_KEY_PREFIX + symbol, currentPrice);

            // 2. Broadcast via WebSocket -> all clients
            messagingTemplate.convertAndSend("/topic/prices" + symbol, currentPrice);

            // Publish to Kafka
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

    public List<StockPriceDto> getAllPrices() {
        return stockPrices.values().stream().toList();
    }

    /**
     * Get current stock price
     */

    public StockPriceDto getStockPrice(String symbol) {

        Object cached = redisTemplate.opsForValue().get(PRICE_KEY_PREFIX + symbol);
        if (cached != null) {
            return (StockPriceDto) cached;
        }

        // Fallback
        StockPriceDto priceDto = stockPrices.get(symbol.toUpperCase());
        if (priceDto == null) {
            throw new StockNotFoundException("404", "Stock not found: " + symbol, HttpStatus.NOT_FOUND);

        }
        return priceDto;
    }

    /**
     * Get all stocks
     */

    public List<Stock> getStockPriceLists() {
        return stockRepository.findByActiveTrue();
    }
}
