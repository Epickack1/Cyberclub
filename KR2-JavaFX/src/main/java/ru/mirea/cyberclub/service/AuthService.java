package ru.mirea.cyberclub.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.mirea.cyberclub.auth.AuthApiClient;
import ru.mirea.cyberclub.auth.AuthApiClient.Tokens;
import ru.mirea.cyberclub.auth.Role;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.AuthenticationException;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Вход сотрудника и его сессия: хранит access- и refresh-токены сервера КР3 и сам
 * продлевает access-токен (он живёт 15 минут), когда тот истёк. Пароль не хранится.
 */
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    /** Обновлять access-токен чуть заранее, чтобы он не истёк по дороге к серверу. */
    private static final long REFRESH_MARGIN_SECONDS = 30;

    private final AuthApiClient client;
    private final Clock clock;
    private Tokens session;

    public AuthService(AuthApiClient client) {
        this(client, Clock.systemUTC());
    }

    public AuthService(AuthApiClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    /** Проверяет поля формы и выполняет вход. */
    public UserInfo login(String email, String password) {
        String normalized = email == null ? "" : email.trim();
        if (normalized.isEmpty()) {
            throw new ValidationException("email", "Введите email.");
        }
        if (!EMAIL.matcher(normalized).matches()) {
            throw new ValidationException("email", "Некорректный email.");
        }
        if (password == null || password.isEmpty()) {
            throw new ValidationException("password", "Введите пароль.");
        }
        try {
            session = client.login(normalized, password);
        } catch (ValidationException e) {
            throw new ValidationException("password", e.getMessage());
        }
        if (session.user().isClient()) {
            // Пароль верный, но это клиент клуба: сразу закрываем его сессию на сервере.
            logout();
            throw new AuthenticationException("Это учётная запись клиента клуба — она для входа на сайт. "
                    + "В программу администратора входят только сотрудники.");
        }
        log.info("Вход в систему: {}", session.user().email());
        return session.user();
    }

    /** Выход: сервер закрывает refresh-сессию. Ошибка связи выход не блокирует. */
    public void logout() {
        Tokens current = session;
        session = null;
        if (current != null) {
            try {
                client.logout(current.refreshToken());
            } catch (AppException e) {
                log.warn("Сервер авторизации не подтвердил выход: {}", e.getMessage());
            }
        }
    }

    public boolean isLoggedIn() {
        return session != null;
    }

    public Optional<UserInfo> currentUser() {
        return Optional.ofNullable(session).map(Tokens::user);
    }

    public boolean hasRole(Role role) {
        return currentUser().map(user -> user.hasRole(role)).orElse(false);
    }

    public String serverUrl() {
        return client.baseUrl();
    }

    /**
     * Выполняет запрос к серверу с действующим access-токеном. Если токен истёк, сначала
     * получает новый по refresh-токену; если и сессия закончилась — нужно войти заново.
     */
    <T> T withToken(Function<String, T> call) {
        if (session == null) {
            throw new AuthenticationException("Сначала войдите в систему.");
        }
        if (session.accessExpiresAt().isBefore(Instant.now(clock).plusSeconds(REFRESH_MARGIN_SECONDS))) {
            refresh();
        }
        try {
            return call.apply(session.accessToken());
        } catch (AuthenticationException e) {
            refresh();
            return call.apply(session.accessToken());
        }
    }

    private void refresh() {
        try {
            session = client.refresh(session.refreshToken());
        } catch (AuthenticationException e) {
            session = null;
            throw new AuthenticationException("Сессия закончилась. Выйдите и войдите заново.");
        }
    }
}
