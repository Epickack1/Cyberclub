package ru.mirea.cyberclub.exception;

/**
 * Ошибка доступа к базе данных: нет соединения, ошибка выполнения SQL-запроса.
 * Оборачивает {@link java.sql.SQLException}, чтобы слои выше не зависели от JDBC.
 */
public class DataAccessException extends AppException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
