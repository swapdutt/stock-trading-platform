package com.trading.marketdataservice.controller;

import com.trading.marketdataservice.dto.StockPriceDto;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.service.MarketDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping(path = "/api/v1/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final MarketDataService marketDataService;

    /**
     * Get all stocks with current price.
     */

    @GetMapping(path = "/stocks")
    public ResponseEntity<List<StockPriceDto>> getAllStocks() {
        return ResponseEntity.ok(marketDataService.getAllPrices());
    }

    /**
     * Get current price for specific stock.
     */

    @GetMapping(path = "/stocks/{symbol}")
    public ResponseEntity<StockPriceDto> getStockPrice(@PathVariable String symbol) {
        return ResponseEntity.ok(marketDataService.getStockPrice(symbol.toUpperCase()));
    }

    /**
     * Get all stocks. Shows which stocks are available for trading.
     */

    @GetMapping(path = "/stocks/list")
    public ResponseEntity<List<Stock>> getStockPriceLists() {
        return ResponseEntity.ok(marketDataService.getStockPriceLists());
    }


}
