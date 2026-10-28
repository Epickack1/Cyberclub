package ru.mirea.cyberclub.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import ru.mirea.cyberclub.exception.AppException;

/**
 * Настройки приложения из app.properties; любой параметр переопределяется переменной окружения.
 *
 * <p>Откуда приложение берёт данные: напрямую из PostgreSQL (JDBC) или через REST API
 * (JavaFX → REST API → Spring Boot → PostgreSQL) — CYBERCLUB_DATA_MODE и CYBERCLUB_API_URL.
 * Вход сотрудников и регистрация пользователей идут через сервер КР3 — CYBERCLUB_AUTH_ENABLED
 * и CYBERCLUB_AUTH_URL.</p>
 *
 * @param mode        режим работы с данными
 * @param apiUrl      адрес REST API данных, например http://localhost:8082/api
 * @param authEnabled нужен ли вход при запуске (false — приложение открывается сразу, как в КР2)
 * @param authUrl     адрес API сервера КР3 для входа и пользователей, например http://localhost:8081/api
 */
public record AppConfig(DataMode mode, String apiUrl, boolean authEnabled, String authUrl) {

    private static final String RESOURCE = "/app.properties";

    /** Способ доступа к данным. */
    public enum DataMode {
        /** JDBC-подключение к базе КР1 (требование КР2). */
        JDBC,
        /** Обращение к собственному REST API (cyberclub-backend). */
        REST
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new AppException("Не удалось прочитать " + RESOURCE + ": " + e.getMessage(), e);
        }
        String mode = env("CYBERCLUB_DATA_MODE", props.getProperty("data.mode", "rest"));
        String apiUrl = env("CYBERCLUB_API_URL", props.getProperty("api.url", "http://localhost:8082/api"));
        String authEnabled = env("CYBERCLUB_AUTH_ENABLED", props.getProperty("auth.enabled", "true"));
        String authUrl = env("CYBERCLUB_AUTH_URL", props.getProperty("auth.url", "http://localhost:8081/api"));
        DataMode dataMode;
        try {
            dataMode = DataMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AppException("Неизвестный режим работы с данными \"" + mode + "\": допустимо rest или jdbc.");
        }
        return new AppConfig(dataMode, stripSlash(apiUrl), Boolean.parseBoolean(authEnabled), stripSlash(authUrl));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue.trim() : value.trim();
    }

    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
