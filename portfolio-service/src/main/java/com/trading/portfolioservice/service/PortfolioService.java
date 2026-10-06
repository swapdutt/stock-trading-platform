package com.trading.portfolioservice.service;

import com.trading.portfolioservice.client.MarketDataClient;
import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final HoldingRepository holdingRepository;
    private final MarketDataClient marketDataClient;

    /**
     * Get portfolio with P&L calculation.
     */


    public Map<String, Object> getPortfolio(String userId) {

        List<Holding> holdings = holdingRepository.findByUserId(userId);
        List<Map<String, Object>> portfolioItems = new ArrayList<>();
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;

        for (Holding holding : holdings) {

            try {

                // Get current market price
                Map<String, Object> priceData = marketDataClient.getStockPrice(holding.getSymbol());
                BigDecimal currentPrice = new BigDecimal(priceData.get("price").toString());
                BigDecimal holdingValue = currentPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));
                BigDecimal invested = holding.getTotalInvested();
                BigDecimal pnlCalc = holdingValue.subtract(invested);
                BigDecimal pnlPercent = pnlCalc.divide(invested, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);

                Map<String, Object> item = new HashMap<>();
                item.put("symbol", holding.getSymbol());
                item.put("quantity", holding.getQuantity());
                item.put("averageBuyPrice", holding.getAverageBuyPrice());
                item.put("currentPrice", currentPrice);
                item.put("currentValue", holdingValue);
                item.put("invested", invested);
                item.put("pnlPercent", pnlPercent);
                item.put("isProfit", pnlCalc.compareTo(BigDecimal.ZERO) > 0);

                portfolioItems.add(item);
                totalInvested = totalInvested.add(invested);
                currentValue = currentValue.add(holdingValue);

            } catch (Exception e) {
                log.error("Error fetching price for {} : {}", holding.getSymbol(), e.getMessage());
            }
        }

        BigDecimal totalPnl = currentValue.subtract(totalInvested);
        BigDecimal totalPnlPercent = totalInvested.compareTo(BigDecimal.ZERO) > 0
                ? totalPnl.divide(totalInvested, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String,Object> portfolio = new HashMap<>();
        portfolio.put("userId", userId);
        portfolio.put("holdings", portfolioItems);
        portfolio.put("totalInvested", totalInvested);
        portfolio.put("currentValue", currentValue);
        portfolio.put("totalPnl",totalPnl);
        portfolio.put("totalPnlPercent", totalPnlPercent);
        portfolio.put("isProfit", totalPnl.compareTo(BigDecimal.ZERO) > 0);
        return portfolio;
    }

}
