package com.trading.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "user-service", url = "${user.service.url}")
public interface UserServiceClient {

    @PostMapping(path = "/api/v1/users/{userId}/funds/deduct")
    Map<String, Object> deductFunds(@PathVariable String userId, @RequestParam BigDecimal amount);

    @PostMapping(path = "/api/v1/users/{userId}/funds/credit")
    Map<String, Object> creditFunds(@PathVariable String userId, @RequestParam BigDecimal amount);

}
