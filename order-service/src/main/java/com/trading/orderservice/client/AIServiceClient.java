package com.trading.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name = "ai-service", url = "${ai.service.url}")
public interface AIServiceClient {

    /**
     * Call Python AI service to check if order is suspicious.
     * Returns fraud score and recommendation.
     */
    @PostMapping("/api/fraud/check")
    Map<String, Object> checkFraud(@RequestBody Map<String, Object> orderData);

}
