package ru.mirea.cyberclub.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import ru.mirea.cyberclub.exception.AppException;

/**
 * Параметры подключения к PostgreSQL. Читаются из файла database.properties
 * (src/main/resources), любой параметр можно переопределить переменной окружения.
 * Пароль в исходном коде не хранится.
 */
public record DatabaseConfig(String url, String user, String password) {

    private static final String RESOURCE = "/database.properties";

    public static DatabaseConfig load() {
        Properties props = new Properties();
        try (InputStream in = DatabaseConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new AppException("Файл настроек " + RESOURCE + " не найден.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new AppException("Не удалось прочитать " + RESOURCE + ": " + e.getMessage(), e);
        }
        return new DatabaseConfig(
                env("CYBERCLUB_DB_URL", props.getProperty("db.url", "jdbc:postgresql://localhost:5432/cyberclub")),
                env("CYBERCLUB_DB_USER", props.getProperty("db.user", "postgres")),
                env("CYBERCLUB_DB_PASSWORD", props.getProperty("db.password", "")));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    /** Пароль не попадает в toString(), а значит и в журнал. */
    @Override
    public String toString() {
        return "DatabaseConfig{url=" + url + ", user=" + user + "}";
    }
}
