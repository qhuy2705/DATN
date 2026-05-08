package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.shared.common.ApiError;
import com.PrimeCare.PrimeCare.shared.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        log.warn("Authentication failed path={} reason={}", request.getRequestURI(), authException.getMessage());

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.AUTH_TOKEN_INVALID.name())
                               .message(ErrorCode.AUTH_TOKEN_INVALID.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(HttpServletResponse.SC_UNAUTHORIZED)
                               .path(request.getRequestURI())
                               .build();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), err);
    }
}