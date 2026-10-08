package com.trading.apigatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {

    private final JwtParser jwtParser;

    public JwtAuthFilter(@Value("${jwt.secret}") String secret) {
        super(Config.class);
        jwtParser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)))
                .build();
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            List<String> authorization = exchange.getRequest().getHeaders()
                    .get(HttpHeaders.AUTHORIZATION);

            if (authorization == null || authorization.isEmpty()) {
                return unauthorizedResponse(exchange, "Authorization Header missing");
            }

            if (authorization.size() != 1) {
                return unauthorizedResponse(exchange, "Invalid Authorization format");
            }

            String header = authorization.getFirst();
            if(header.length() <= 7 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return unauthorizedResponse(exchange, "Invalid Authorization format");
            }

            String token = header.substring(7).trim();
            if (token.isEmpty()) {
                return unauthorizedResponse(exchange, "Bearer token missing");
            }

            String userId;
            try {
                Claims claims = jwtParser.parseSignedClaims(token).getPayload();
                userId = claims.get("userId", String.class);
                if (userId == null || userId.isBlank()) {
                    return unauthorizedResponse(exchange, "JWT user id claim missing");
                }
            } catch (JwtException | IllegalArgumentException exception) {
                log.warn("Invalid or Expired JWT for path: {}", exchange.getRequest().getPath());
                return unauthorizedResponse(exchange,"Invalid or Expired token");
            }

            ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> headers.set("X-User-Id", userId)).build();
            return chain.filter(exchange.mutate().request(request).build());

        };
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        log.warn("Unauthorized: {}", message);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }


    public static class Config {

    }


}
