package com.minupay.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minupay.common.exception.ErrorCode;
import com.minupay.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Api-Key";
    private static final String PATH_PREFIX = "/api/internal/";

    private final String expectedKey;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!isInternalPath(request)) {
            chain.doFilter(request, response);
            return;
        }
        String provided = request.getHeader(HEADER);
        if (!isValid(provided)) {
            writeForbidden(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isInternalPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith(PATH_PREFIX);
    }

    private boolean isValid(String provided) {
        if (!StringUtils.hasText(expectedKey)) {
            return false;
        }
        return expectedKey.equals(provided);
    }

    private void writeForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
