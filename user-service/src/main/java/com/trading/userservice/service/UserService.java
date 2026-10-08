package com.trading.userservice.service;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.entity.User;
import com.trading.userservice.exception.EmailAlreadyExistsException;
import com.trading.userservice.exception.InsufficientWalletBalanceException;
import com.trading.userservice.exception.InvalidCredentialsException;
import com.trading.userservice.exception.UserNotFoundException;
import com.trading.userservice.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refreshTokenExpiration}")
    private long refreshTokenExpiration;


    private static final String USER_REGISTERED_TOPIC = "user.register";

    private static final String USER_ID = "userId";
    private static final String USER_NOT_FOUND = "User not found with id : ";

    /**
     * Register a new trader
     */

    public AuthResponse register(RegisterRequest request) {
        log.info("Registering user : {}", request.getEmail());

        if (Boolean.TRUE.equals(userRepository.existsByEmail(request.getEmail()))) {
            throw new EmailAlreadyExistsException("409", "Requested Email : " + request.getEmail() + " already registered", HttpStatus.CONFLICT);
        }

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setWalletBalance(request.getInitialDeposit() != null
                ? request.getInitialDeposit() : BigDecimal.valueOf(10000));

        User savedUser = userRepository.save(user);

        log.info("User registered successfully : {}", savedUser.getId());

        // Publish Event : user.registered to Kafka
        Map<String, Object> userRegisteredEvent = new HashMap<>();
        userRegisteredEvent.put(USER_ID, savedUser.getId());
        userRegisteredEvent.put("email", savedUser.getEmail());
        userRegisteredEvent.put("firstName", savedUser.getFirstName());
        userRegisteredEvent.put("lastName", savedUser.getLastName());
        userRegisteredEvent.put("walletBalance", savedUser.getWalletBalance());

        kafkaTemplate.send(USER_REGISTERED_TOPIC, savedUser.getId(), userRegisteredEvent);

        String accessToken = generateAccessToken(savedUser.getId(), savedUser.getEmail());
        String refreshToken = generateRefreshToken(savedUser.getId());

        return buildAuthResponse(savedUser, accessToken, refreshToken);

    }

    /**
     * Login
     */

    public AuthResponse login(@Valid LoginRequest request) {

        log.info("Login attempt : {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("404", "User not found with email id : " + request.getEmail(), HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("401", "Invalid credentials", HttpStatus.UNAUTHORIZED);
        } else {
            log.info("Login successfully : {}", user.getId());
        }

        String accessToken = generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());

        return buildAuthResponse(user, accessToken, refreshToken);

    }

    /**
     * Get User Profile by id.
     * Called by other services by OpenFeign internally.
     */

    public UserResponse getUserProfileById(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("404", USER_NOT_FOUND + userId, HttpStatus.NOT_FOUND));
        return mapToUserResponse(user);

    }


    /**
     * Add funds to wallet
     */

    public UserResponse addFunds(String userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("404", USER_NOT_FOUND + userId, HttpStatus.NOT_FOUND));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        log.info("Funds added : {}  successfully to user : {}", amount, userId);
        return mapToUserResponse(userRepository.save(user));

    }

    /**
     * Deduct funds from wallet - Called on BUY order
     */

    public UserResponse deductFunds(String userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("404", USER_NOT_FOUND + userId, HttpStatus.NOT_FOUND));
        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw new InsufficientWalletBalanceException("404", "Insufficient Wallet Balance", HttpStatus.NOT_FOUND);
        }
        user.setWalletBalance(user.getWalletBalance().subtract(amount));
        log.info("Funds deducted : {}  successfully to user : {}", amount, userId);
        return mapToUserResponse(userRepository.save(user));

    }

    /**
     * Credit funds to wallet - Called on SELL order
     */

    public UserResponse creditFunds(String userId, BigDecimal amount) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("404", USER_NOT_FOUND + userId, HttpStatus.NOT_FOUND));
        user.setWalletBalance(user.getWalletBalance().add(amount));
        log.info("Funds credited : {}  successfully to user : {}", amount, userId);
        return mapToUserResponse(userRepository.save(user));

    }


    /*
     * Utility methods
     */


    private String generateAccessToken(String id, String email) {

        return Jwts.builder()
                .claim(USER_ID, id)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();

    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String generateRefreshToken(String id) {

        return Jwts.builder()
                .claim(USER_ID, id)
                .subject(id)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSignInKey())
                .compact();

    }

    private AuthResponse buildAuthResponse(User savedUser, String accessToken, String refreshToken) {

        AuthResponse response = new AuthResponse();
        response.setUserId(savedUser.getId());
        response.setEmail(savedUser.getEmail());
        response.setFirstName(savedUser.getFirstName());
        response.setLastName(savedUser.getLastName());
        response.setWalletBalance(savedUser.getWalletBalance());
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);

        return response;

    }

    private UserResponse mapToUserResponse(User user) {

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setWalletBalance(user.getWalletBalance());
        response.setStatus(user.getStatus());
        response.setCreateAt(user.getCreateAt());

        return response;

    }


}
