package ru.mirea.cyberclub.backend.security;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ru.mirea.cyberclub.backend.error.UnauthorizedException;
import ru.mirea.cyberclub.backend.repository.UserAccountRepository;

/**
 * Читает {@code Authorization: Bearer <JWT>} и, если токен действителен, кладёт пользователя
 * в SecurityContext. Пользователь и роли перечитываются из базы: отключённая учётная запись
 * или снятая роль действуют сразу, без ожидания истечения токена.
 * Если токена нет или он плохой — запрос идёт дальше анонимно, и закрытый адрес получит 401.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserAccountRepository users;
    private final Clock clock;

    public JwtAuthenticationFilter(JwtService jwtService, UserAccountRepository users, Clock clock) {
        this.jwtService = jwtService;
        this.users = users;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(header.substring(7).trim());
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            UUID userId = jwtService.parseUserId(token);
            users.findById(userId)
                    .filter(user -> user.isEnabled() && !user.isLocked(clock.instant()))
                    .map(CurrentUser::from)
                    .ifPresent(user -> SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(user, null, user.authorities())));
        } catch (UnauthorizedException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
        }
    }
}
