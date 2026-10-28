package ru.mirea.cyberclub.backend.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import ru.mirea.cyberclub.backend.config.SecurityProperties;
import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.error.UnauthorizedException;

/**
 * Выпускает и проверяет короткоживущие access-токены JWT (подпись HMAC-SHA256).
 * В токене только идентификатор, email и роли; права при каждом запросе всё равно
 * перечитываются из базы, поэтому отключённый пользователь теряет доступ сразу.
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecurityProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(secretBytes(properties.jwtSecretBase64()));
    }

    private static byte[] secretBytes(String base64) {
        if (base64 == null || base64.isBlank()) {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            log.warn("JWT_SECRET_BASE64 не задан: ключ подписи сгенерирован случайно, "
                    + "после перезапуска сервера все пользователи войдут заново.");
            return random;
        }
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(base64.trim());
        } catch (RuntimeException ex) {
            throw new IllegalStateException("JWT_SECRET_BASE64 должен быть корректной строкой Base64", ex);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException("JWT_SECRET_BASE64 должен содержать не меньше 32 случайных байт");
        }
        return decoded;
    }

    /** Выпускает access-токен для пользователя. */
    public IssuedToken issue(UserAccount user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.accessMinutes(), ChronoUnit.MINUTES);
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
        String token = Jwts.builder()
                .issuer(properties.jwtIssuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** Проверяет подпись, издателя и срок действия; возвращает идентификатор пользователя. */
    public UUID parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(properties.jwtIssuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return UUID.fromString(claims.getSubject());
        } catch (RuntimeException ex) {
            throw new UnauthorizedException("Токен недействителен или истёк. Войдите заново.");
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
