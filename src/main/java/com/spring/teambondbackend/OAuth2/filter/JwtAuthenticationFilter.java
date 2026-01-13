package com.spring.teambondbackend.OAuth2.filter;

import com.spring.teambondbackend.OAuth2.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/oauth2/success");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        logger.info("Starting filter for request URI: {}", request.getRequestURI());

        // Try to get token from Authorization header
        String token = null;
        final String header = request.getHeader("Authorization");
        logger.info("Authorization header: {}", header);

        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
            logger.info("JWT token found in Authorization header.");
        } else {
            // If no token in header, try to get it from cookie named "jwtToken"
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if ("jwtToken".equals(cookie.getName())) {
                        token = cookie.getValue();
                        logger.info("JWT token found in cookie.");
                        break;
                    }
                }
            }
        }

        if (token == null || token.isBlank()) {
            logger.info("No JWT token found, continuing filter chain.");
            filterChain.doFilter(request, response);
            return;
        }

        logger.info("Extracted token: {}", token);

        try {
            Claims claims = JwtUtil.validateToken(token);
            logger.info("Token validated successfully.");

            String userId = claims.getSubject();
            String status = (String) claims.get("status"); // expected "paid" or "not paid"
            logger.info("Extracted userId: {}", userId);
            logger.info("Extracted status before normalization: {}", status);

            if (status != null) {
                status = status.trim().toUpperCase().replace(" ", "_"); // normalize to "PAID" or "NOT_PAID"
            } else {
                status = "NOT_PAID"; // default fallback if missing
            }
            logger.info("Normalized status/authority: {}", status);

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority(status))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            logger.info("Authentication set in SecurityContextHolder.");

        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (MalformedJwtException e) {
            logger.warn("Invalid JWT token format: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (SignatureException e) {
            logger.warn("Invalid JWT signature: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            logger.error("Could not set user authentication in security context: {}", e.getMessage(), e);
            SecurityContextHolder.clearContext();
        }

        logger.info("Continuing filter chain.");
        filterChain.doFilter(request, response);
    }
}
