package ru.mirea.cyberclub.backend.config;

import java.io.IOException;
import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.mirea.cyberclub.backend.dto.ApiError;
import ru.mirea.cyberclub.backend.security.JwtAuthenticationFilter;

/**
 * Правила доступа к API. Сервер без состояния: каждый запрос несёт свой JWT в заголовке
 * {@code Authorization: Bearer ...}, поэтому cookie-сессии и CSRF-защита не нужны.
 *
 * <pre>
 * открыто всем          — вход/обновление/выход, Swagger UI, /actuator/health
 * любой вошедший        — /api/auth/me, смена своего пароля
 * только CLIENT         — личный кабинет /api/me/** (своя карточка и свои брони)
 * любой сотрудник       — все остальные GET /api/** (клиент чужие данные не видит)
 * OPERATOR или ADMIN    — изменение клиентов и броней
 * только ADMIN          — пользователи (/api/admin/**), справочник мест, окончательное удаление брони
 * </pre>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
                                                   ObjectMapper objectMapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/bookings/*/permanent").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/stations", "/api/stations/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/stations/**").hasRole("ADMIN")
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers("/api/me/**").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAnyRole("VIEWER", "OPERATOR", "ADMIN")
                        .requestMatchers("/api/**").hasAnyRole("OPERATOR", "ADMIN")
                        .anyRequest().permitAll())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) -> writeError(objectMapper, request, response,
                                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                "Требуется вход: POST /api/auth/login, затем заголовок Authorization: Bearer <accessToken>."))
                        .accessDeniedHandler((request, response, e) -> writeError(objectMapper, request, response,
                                HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Недостаточно прав для этой операции.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 401 и 403 в том же JSON-формате ApiError, что и остальные ошибки API. */
    private static void writeError(ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
                                   HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(), MDC.get("correlationId"), null));
    }
}
