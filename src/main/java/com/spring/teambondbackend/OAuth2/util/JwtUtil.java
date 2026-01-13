package com.spring.teambondbackend.OAuth2.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {
    private static String SECRET_KEY;
    private static SecretKey KEY;
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hour

    @org.springframework.beans.factory.annotation.Value("${JWT_SECRET_KEY}")
    public void setSecretKey(String secretKey) {
        JwtUtil.SECRET_KEY = secretKey;
        JwtUtil.KEY = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken(String id, String username, String email, String status) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", id);
        claims.put("username", username);
        claims.put("status", status);
        claims.put("email", email);

        return Jwts.builder()
                .setSubject(id)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static String getUserIdFromToken(String token) {
        return validateToken(token).getSubject();
    }
}
