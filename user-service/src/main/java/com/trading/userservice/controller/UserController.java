package com.trading.userservice.controller;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping(path = "/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping(path = "/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request));
    }

    @PostMapping(path = "/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }

    @GetMapping(path = "/userDetail")
    public ResponseEntity<UserResponse>  getCurrentUserProfile(@RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(userService.getUserProfileById(userId));
    }

    @GetMapping(path = "/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable(value = "userId") String userId) {
        return ResponseEntity.ok(userService.getUserProfileById(userId));
    }

    @PostMapping(path = "/{userId}/funds/add")
    public ResponseEntity<UserResponse> addFunds(@PathVariable(value = "userId") String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.addFunds(userId, amount));
    }

    @PostMapping(path = "/{userId}/funds/deduct")
    public ResponseEntity<UserResponse> deductFunds(@PathVariable(value = "userId") String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.deductFunds(userId, amount));
    }

    @PostMapping(path = "/{userId}/funds/credit")
    public ResponseEntity<UserResponse> creditFunds(@PathVariable(value = "userId") String userId, @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(userService.creditFunds(userId, amount));
    }

}
