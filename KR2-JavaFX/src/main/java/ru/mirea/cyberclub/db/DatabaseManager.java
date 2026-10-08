package ru.mirea.cyberclub.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import ru.mirea.cyberclub.exception.DataAccessException;

/**
 * Выдаёт JDBC-соединения с PostgreSQL. Каждый запрос DAO открывает своё соединение
 * и закрывает его через try-with-resources: соединение не «висит» между действиями
 * пользователя и не делится между потоками JavaFX.
 */
public class DatabaseManager {

    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private final DatabaseConfig config;

    public DatabaseManager(DatabaseConfig config) {
        this.config = config;
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
    }

    /** Открывает новое соединение. Вызывающий код обязан закрыть его. */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    /**
     * Проверяет доступность базы данных при старте приложения.
     *
     * @throws DataAccessException если подключиться не удалось
     */
    public void checkConnection() {
        try (Connection connection = getConnection()) {
            if (!connection.isValid(LOGIN_TIMEOUT_SECONDS)) {
                throw new DataAccessException("Соединение с базой данных " + config.url() + " недействительно.");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Не удалось подключиться к базе данных " + config.url()
                    + " (пользователь " + config.user() + "): " + e.getMessage(), e);
        }
    }

    /** Строка для строки состояния: адрес базы и пользователь (без пароля). */
    public String describe() {
        return config.url() + " · " + config.user();
    }
}
