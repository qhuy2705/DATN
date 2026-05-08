package com.PrimeCare.PrimeCare.config;

import com.PrimeCare.PrimeCare.modules.auth.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                // CSRF is disabled because API authorization is stateless JWT and server-side
                // sessions are disabled below. Current cookie use is limited to refresh-token
                // handling; cookie/session-authenticated state-changing endpoints must add CSRF
                // token protection or re-enable CSRF before going live.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/password/forgot", "/api/auth/password/inspect", "/api/auth/password/complete", "/api/auth/patient/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/auth/change-password").authenticated()

                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/files/public/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        .requestMatchers("/api/admin/dashboard/**").hasAnyRole("SYSTEM_ADMIN", "OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/audit-logs/**").hasAnyRole("SYSTEM_ADMIN", "OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/doctor-schedules/**").hasRole("OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/doctor-leaves/**").hasRole("OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/branch-specialties/**").hasRole("OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/specialties/**").hasRole("OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/medical-services/**").hasRole("OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/medications/**").hasRole("OPERATIONS_ADMIN")
                        .requestMatchers("/api/admin/public-contact/**").hasAnyRole("SYSTEM_ADMIN", "OPERATIONS_ADMIN")

                        .requestMatchers("/api/cashier/**").hasRole("CASHIER")
                        .requestMatchers(HttpMethod.GET, "/api/service-desk/results/*/pdf").hasAnyRole("SERVICE_TECHNICIAN", "OPERATIONS_ADMIN", "DOCTOR")
                        .requestMatchers("/api/service-desk/**").hasAnyRole("SERVICE_TECHNICIAN", "OPERATIONS_ADMIN")

                        .requestMatchers("/api/admin/appointments/**").hasAnyRole("OPERATIONS_ADMIN", "STAFF")
                        .requestMatchers("/api/reception/**").hasAnyRole("OPERATIONS_ADMIN", "STAFF")
                        .requestMatchers("/api/admin/patients/**").hasAnyRole("SYSTEM_ADMIN", "OPERATIONS_ADMIN", "STAFF")
                        .requestMatchers("/api/doctor/profile/**").hasRole("DOCTOR")
                        .requestMatchers("/api/doctor/appointments/**").hasRole("DOCTOR")
                        .requestMatchers("/api/pharmacy/**").hasAnyRole("PHARMACIST", "OPERATIONS_ADMIN")
                        .requestMatchers("/api/doctor/patients/**").hasRole("DOCTOR")
                        .requestMatchers("/api/doctor/medications/**").hasRole("DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/api/doctor/encounters/*/reopen").hasAnyRole("DOCTOR", "OPERATIONS_ADMIN")
                        .requestMatchers("/api/doctor/encounters/*/prescriptions/**").hasRole("DOCTOR")
                        .requestMatchers("/api/doctor/encounters/**").hasRole("DOCTOR")
                        .requestMatchers("/api/doctor/icd10-codes/**").hasRole("DOCTOR")
                        .requestMatchers("/api/doctor/prescription-pdf-jobs/**").hasRole("DOCTOR")
                        .requestMatchers("/api/files/**").hasAnyRole("SYSTEM_ADMIN", "OPERATIONS_ADMIN", "STAFF", "SERVICE_TECHNICIAN", "DOCTOR", "CASHIER", "PATIENT")

                        .requestMatchers("/api/account/notification-preferences/**").hasRole("PATIENT")
                        .requestMatchers("/api/patient/**").hasRole("PATIENT")
                        .requestMatchers("/api/admin/**").hasRole("SYSTEM_ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
