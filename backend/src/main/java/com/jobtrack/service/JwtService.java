package com.jobtrack.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private final Algorithm algorithm;
    private final long expirationMillis;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes:480}") long expirationMinutes) {
        
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or empty");
        }

        byte[] decodedKey;
        try {
            decodedKey = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            // Fallback for non-base64 keys in local development/testing profiles
            decodedKey = secret.getBytes();
        }

        if (decodedKey.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 256 bits (32 bytes)");
        }

        this.algorithm = Algorithm.HMAC256(decodedKey);
        this.expirationMillis = expirationMinutes * 60 * 1000;
    }

    public String generateToken(String email) {
        return JWT.create()
                .withSubject(email)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMillis))
                .sign(algorithm);
    }

    public String validateTokenAndGetEmail(String token) {
        try {
            DecodedJWT decodedJWT = JWT.require(algorithm)
                    .build()
                    .verify(token);
            return decodedJWT.getSubject();
        } catch (JWTVerificationException e) {
            throw new IllegalArgumentException("Invalid or expired JWT token: " + e.getMessage(), e);
        }
    }
}
