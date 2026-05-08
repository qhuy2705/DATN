package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.modules.ratelimit.web.RateLimitInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final Environment environment;

    @Value("${app.cors.allowed-origin-patterns}")
    private String[] allowedOriginPatterns;

    @PostConstruct
    void validateCorsConfiguration() {
        boolean prodProfile = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        boolean wildcardOrigin = allowedOriginPatterns != null
                && Arrays.stream(allowedOriginPatterns)
                         .filter(pattern -> pattern != null)
                         .map(String::trim)
                         .anyMatch(pattern -> pattern.contains("*"));

        if (prodProfile && wildcardOrigin) {
            throw new IllegalStateException("Wildcard CORS origins are not allowed in prod when credentials are enabled");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()
                )
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.AUTHORIZATION, HttpHeaders.LOCATION, HttpHeaders.CONTENT_DISPOSITION)
                .allowCredentials(true)
                .maxAge(3600);

        registry.addMapping("/ws/**")
                .allowedOriginPatterns(allowedOriginPatterns)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
