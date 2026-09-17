package ru.mirea.cyberclub.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import ru.mirea.cyberclub.exception.AppException;

/**
 * Параметры подключения к БД: читаются из db.properties (classpath),
 * любой из них можно переопределить переменной окружения.
 */
public final class AppConfig {

    private static final String RESOURCE = "/db.properties";

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    private AppConfig(String dbUrl, String dbUser, String dbPassword) {
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }

    public static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new AppException("Файл настроек " + RESOURCE + " не найден в classpath.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new AppException("Не удалось прочитать " + RESOURCE + ": " + e.getMessage(), e);
        }
        return new AppConfig(
                env("CYBERCLUB_DB_URL", props.getProperty("db.url", "jdbc:postgresql://localhost:5432/cyberclub")),
                env("CYBERCLUB_DB_USER", props.getProperty("db.user", "postgres")),
                env("CYBERCLUB_DB_PASSWORD", props.getProperty("db.password", "")));
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }

    public String getDbPassword() {
        return dbPassword;
    }
}
