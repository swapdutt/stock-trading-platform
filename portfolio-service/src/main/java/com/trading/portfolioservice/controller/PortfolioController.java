package com.trading.portfolioservice.controller;

import com.trading.portfolioservice.service.PortfolioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioService portfolioService;

    /**
     * Get current user's portfolio with P&L
     */

    @GetMapping(path = "/current-portfolio")
    public ResponseEntity<Map<String, Object>> getCurrentUserPortfolio(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }

    /**
     * Get portfolio by user id for internal calls
     */

    @GetMapping(path = "/{userId}")
    public ResponseEntity<Map<String, Object>> getPortfolio(@PathVariable String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }






}
