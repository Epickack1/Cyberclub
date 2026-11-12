package ru.mirea.cyberclub.backend.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.mirea.cyberclub.backend.dto.ApiError;

/**
 * Временный пароль (выдан администратором) нельзя использовать как постоянный: пока он не сменён,
 * API отвечает 403 PASSWORD_CHANGE_REQUIRED на всё, кроме сведений о себе, смены пароля,
 * обновления токена и выхода. Подключается в SecurityConfig сразу после проверки JWT.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
    private static final Set<String> ALLOWED = Set.of(
            "/api/auth/me",
            "/api/auth/password",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/login");

    private final ObjectMapper objectMapper;

    public PasswordChangeRequiredFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI();
        if (path.startsWith("/api/") && !ALLOWED.contains(path)
                && auth != null && auth.getPrincipal() instanceof CurrentUser user && user.mustChangePassword()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), 403,
                    "PASSWORD_CHANGE_REQUIRED",
                    "Пароль временный: смените его (POST /api/auth/password), затем войдите заново.",
                    path, MDC.get("correlationId"), null));
            return;
        }
        chain.doFilter(request, response);
    }
}
