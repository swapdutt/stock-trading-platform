package com.trading.marketdataservice.config;

import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private final StockRepository stockRepository;

    private static final String NASDAQ = "NASDAQ";
    private static final String NSE = "NSE";

    @Override
    public void run(String... args) throws Exception {

        if (stockRepository.count() > 0) {
            log.info("Stocks already initialized -> Skipping.");
            return;
        }

        log.info("Initializing the stock data...");

        List<Stock> stocks = List.of(
                // USA Stocks
                new Stock("APPL", "Apple Inc.", BigDecimal.valueOf(189.50), NASDAQ, "USD", true),
                new Stock("GOOGL", "Alphabet Inc.", BigDecimal.valueOf(141.80), NASDAQ, "USD", true),
                new Stock("MSFT", "Microsoft CoOperation", BigDecimal.valueOf(378.90), NASDAQ, "USD", true),

                // India Stocks
                new Stock("TCS", "Tata Consultancy Services", BigDecimal.valueOf(2850.00), NSE, "INR", true),
                new Stock("INFY", "Infosys", BigDecimal.valueOf(1650.00), NSE, "INR", true),
                new Stock("PHI", "Prudential Health Insurance", BigDecimal.valueOf(1890.50), NSE, "INR", true),
                new Stock("RELIANCE", "Reliance Industries", BigDecimal.valueOf(3920.00), NSE, "INR", true)
        );

        stockRepository.saveAll(stocks);

        log.info("Initialized the stock data successfully : {}", stocks.size());

    }
}
