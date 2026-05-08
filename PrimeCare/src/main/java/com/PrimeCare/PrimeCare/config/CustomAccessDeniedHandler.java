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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String principal = authentication != null ? String.valueOf(authentication.getName()) : "anonymous";

        log.warn("Access denied path={} principal={} reason={}",
                request.getRequestURI(), principal, accessDeniedException.getMessage());

        ApiError err = ApiError.builder()
                               .success(false)
                               .code(ErrorCode.ACCESS_DENIED.name())
                               .message(ErrorCode.ACCESS_DENIED.getDefaultMessage())
                               .timestamp(Instant.now())
                               .status(HttpServletResponse.SC_FORBIDDEN)
                               .path(request.getRequestURI())
                               .build();

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), err);
    }
}