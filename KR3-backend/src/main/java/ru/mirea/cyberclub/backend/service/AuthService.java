package ru.mirea.cyberclub.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.config.SecurityProperties;
import ru.mirea.cyberclub.backend.domain.RefreshSession;
import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.dto.AuthDtos.TokenResponse;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;
import ru.mirea.cyberclub.backend.error.AccountLockedException;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.error.UnauthorizedException;
import ru.mirea.cyberclub.backend.repository.RefreshSessionRepository;
import ru.mirea.cyberclub.backend.repository.UserAccountRepository;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.security.JwtService;
import ru.mirea.cyberclub.backend.security.PasswordPolicy;

/**
 * Вход, обновление токенов, выход и смена своего пароля.
 *
 * <p>Сообщение при неверном email и при неверном пароле одинаковое — по ответу нельзя узнать,
 * существует ли учётная запись. После {@code maxFailedLogins} неудач подряд вход блокируется.</p>
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String BAD_CREDENTIALS = "Неверный email или пароль.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final JwtService jwtService;
    private final SecurityProperties properties;
    private final Clock clock;
    /** Хеш для сравнения, когда пользователя нет: время ответа не выдаёт, есть ли такой email. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, RefreshSessionRepository sessions, PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy, JwtService jwtService, SecurityProperties properties, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.jwtService = jwtService;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    /**
     * Неудачная попытка входа сохраняется в базе даже при ответе 401,
     * поэтому транзакция не откатывается на UnauthorizedException.
     */
    @Transactional(noRollbackFor = {UnauthorizedException.class, AccountLockedException.class})
    public TokenResponse login(String email, String password) {
        Instant now = clock.instant();
        UserAccount user = users.findByEmail(UserAccount.normalizeEmail(email)).orElse(null);
        if (user == null) {
            passwordEncoder.matches(password, dummyHash);
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (user.isLocked(now)) {
            throw new AccountLockedException(lockedMessage(user, now));
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.registerFailedLogin(now, properties.maxFailedLogins(), Duration.ofMinutes(properties.lockMinutes()));
            log.warn("Неудачный вход: userId={}", user.getId());
            if (user.isLocked(now)) {
                throw new AccountLockedException(lockedMessage(user, now));
            }
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Учётная запись отключена. Обратитесь к администратору.");
        }
        user.registerSuccessfulLogin(now);
        log.info("Вход в систему: userId={}", user.getId());
        return issueTokens(user, now);
    }

    /** Обменивает refresh-токен на новую пару токенов; старый refresh-токен больше не действует. */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public TokenResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshSession session = sessions.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Сессия не найдена. Войдите заново."));
        UserAccount user = session.getUser();
        if (session.getRevokedAt() != null) {
            // Повторное использование уже обменянного токена — похоже на кражу: закрываем все сессии.
            sessions.revokeAllForUser(user.getId(), now);
            log.warn("Повторное использование refresh-токена, все сессии отозваны: userId={}", user.getId());
            throw new UnauthorizedException("Сессия уже использована. Войдите заново.");
        }
        if (!session.isActive(now)) {
            throw new UnauthorizedException("Сессия истекла. Войдите заново.");
        }
        if (!user.isEnabled() || user.isLocked(now)) {
            session.revoke(now);
            throw new UnauthorizedException("Учётная запись отключена или заблокирована.");
        }
        session.revoke(now);
        return issueTokens(user, now);
    }

    /** Выход: отзывает refresh-сессию. Ответ одинаковый, даже если токен неизвестен. */
    @Transactional
    public void logout(String refreshToken) {
        sessions.findByTokenHash(hash(refreshToken)).ifPresent(session -> session.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(CurrentUser current) {
        return UserMapper.toResponse(requireUser(current), clock.instant());
    }

    /** Смена своего пароля; все остальные сессии пользователя закрываются. */
    @Transactional
    public void changePassword(CurrentUser current, String currentPassword, String newPassword) {
        UserAccount user = requireUser(current);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("currentPassword", "Текущий пароль указан неверно.");
        }
        passwordPolicy.validate(newPassword, user.getEmail(), "newPassword");
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        sessions.revokeAllForUser(user.getId(), clock.instant());
    }

    private UserAccount requireUser(CurrentUser current) {
        return users.findById(current.id()).orElseThrow(() -> new NotFoundException("Пользователь не найден."));
    }

    private TokenResponse issueTokens(UserAccount user, Instant now) {
        JwtService.IssuedToken access = jwtService.issue(user);
        String refreshToken = newRefreshToken();
        Instant refreshExpiresAt = now.plus(properties.refreshDays(), ChronoUnit.DAYS);
        sessions.save(new RefreshSession(user, hash(refreshToken), now, refreshExpiresAt));
        return new TokenResponse(access.token(), "Bearer", access.expiresAt(), refreshToken, refreshExpiresAt,
                UserMapper.toResponse(user, now));
    }

    private static String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** В базе хранится только SHA-256 refresh-токена. */
    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private static String lockedMessage(UserAccount user, Instant now) {
        long minutes = Math.max(1, Duration.between(now, user.getLockedUntil()).toMinutes() + 1);
        return "Слишком много неудачных попыток входа. Повторите через " + minutes + " мин.";
    }
}
