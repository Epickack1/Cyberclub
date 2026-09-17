package ru.mirea.cyberclub.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import ru.mirea.cyberclub.exception.DataAccessException;

/**
 * Управляет подключением к PostgreSQL через JDBC.
 * Каждый запрос репозитория получает отдельное соединение и закрывает его
 * (try-with-resources) — для консольного приложения пул соединений не нужен.
 */
public class DatabaseManager {

    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager(AppConfig config) {
        this(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
    }

    public DatabaseManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    /** Открывает новое соединение. Вызывающий код обязан закрыть его. */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /**
     * Проверяет доступность базы данных при старте приложения.
     *
     * @throws DataAccessException если подключиться не удалось
     */
    public void checkConnection() {
        try (Connection connection = getConnection()) {
            if (!connection.isValid(3)) {
                throw new DataAccessException("Соединение с базой данных " + url + " недействительно.");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Не удалось подключиться к базе данных " + url
                    + " (пользователь " + user + "): " + e.getMessage(), e);
        }
    }

    public String getUrl() {
        return url;
    }
}
