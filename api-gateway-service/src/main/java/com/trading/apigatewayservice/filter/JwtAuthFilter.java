package com.trading.apigatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import javax.crypto.SecretKey;
import java.util.Objects;

@Slf4j
@Component
public class JwtAuthFilter implements HandlerFilterFunction<ServerResponse, ServerResponse> {

    @Value("${jwt.secret}")
    private String secretKey;

    @Override
    public ServerResponse filter(ServerRequest request, HandlerFunction<ServerResponse> next) throws Exception {

        String path = request.path();

        // Skip JWT auth for public endpoints.
        if (isPublicEndpoint(path)) {
            log.debug("Public endpoint - skipping JWT authentication: {}", path);
            return next.handle(request);
        }

        // Validate Authorization Header presence
        if (Objects.requireNonNull(request.headers().firstHeader("Authorization")).isEmpty()) {
            log.warn("JWT authentication header is missing : {}", path);
            return unauthorizedResponse("Authorization Header missing");
        }

        String authHeader = request.headers().firstHeader("Authorization");
        if (authHeader == null || !Objects.requireNonNull(authHeader).startsWith("Bearer ")) {
            return unauthorizedResponse("Invalid Authorization format");
        }

        String token = authHeader.substring(7);

        try {
            // Extract and Parse JWT Claims
            Claims claims = extractClaims(token);
            String userId = claims.get("userId", String.class);
            String email = claims.getSubject();

            log.debug("userId: {} email : {}", userId, email);

            // Mutate request to add downstream headers
            ServerRequest modifiedRequest = ServerRequest.from(request)
                    .header("X-User-Id").build();

            // Forward downstream
            return next.handle(modifiedRequest);

        } catch (Exception e) {
            log.warn("Invalid JWT token : {}", token);
            return unauthorizedResponse("Invalid OR Expired token");
        }

    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/register") ||
                path.contains("/login") ||
                path.contains("/health") ||
                path.contains("/ws");
    }

    private ServerResponse unauthorizedResponse(String message) {
        log.warn("Unauthorized: {}", message);
        return ServerResponse.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorBody(message));
    }

    private Claims extractClaims(String token) {
        return Jwts.parser().verifyWith(getSignInKey())
                .build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }


    private record ErrorBody(String error) {

    }


}
