package com.minupay.auth.infrastructure;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        extractToken(request)
                .flatMap(jwtProvider::parse)
                .ifPresent(this::setAuthentication);
        chain.doFilter(request, response);
    }

    private void setAuthentication(Claims claims) {
        Long userId = Long.parseLong(claims.getSubject());
        String role = claims.get("role", String.class);
        LoginUser loginUser = new LoginUser(userId, role);
        var authentication = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearer) || !bearer.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(bearer.substring(BEARER_PREFIX.length()));
    }
}
