package ru.mirea.cyberclub.repository.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import ru.mirea.cyberclub.exception.DataAccessException;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * Общий код JDBC-репозиториев. Каждый метод выполняет один параметризованный
 * запрос по схеме: Connection -> PreparedStatement -> ResultSet, все три ресурса
 * закрываются автоматически (try-with-resources). SQLException переводится в
 * исключения приложения, чтобы слой сервисов не зависел от JDBC.
 */
public abstract class AbstractJdbcRepository {

    /** Заполняет параметры подготовленного запроса (ps.setXxx(index, value)). */
    @FunctionalInterface
    protected interface ParameterSetter {
        void set(PreparedStatement ps) throws SQLException;
    }

    /** Преобразует текущую строку ResultSet в объект. */
    @FunctionalInterface
    protected interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    protected static final ParameterSetter NO_PARAMS = ps -> { };

    protected final DatabaseManager db;

    protected AbstractJdbcRepository(DatabaseManager db) {
        this.db = db;
    }

    /** SELECT, возвращающий список объектов. */
    protected <T> List<T> queryList(String sql, ParameterSetter params, RowMapper<T> mapper) {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            params.set(statement);
            try (ResultSet rs = statement.executeQuery()) {
                List<T> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "выборка данных");
        }
    }

    /** SELECT, возвращающий не более одной строки. */
    protected <T> Optional<T> queryOne(String sql, ParameterSetter params, RowMapper<T> mapper) {
        List<T> list = queryList(sql, params, mapper);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    /** SELECT COUNT(*) или другой запрос с единственным числовым значением. */
    protected long queryLong(String sql, ParameterSetter params) {
        return queryOne(sql, params, rs -> rs.getLong(1)).orElse(0L);
    }

    /** INSERT/UPDATE/DELETE; возвращает число изменённых строк. */
    protected int executeUpdate(String sql, ParameterSetter params) {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            params.set(statement);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "изменение данных");
        }
    }

    /** INSERT с возвратом сгенерированного первичного ключа (BIGSERIAL id). */
    protected long executeInsert(String sql, ParameterSetter params) {
        try (Connection connection = db.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            params.set(statement);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong("id");
                }
                throw new DataAccessException("База данных не вернула идентификатор новой записи.");
            }
        } catch (SQLException e) {
            throw SqlErrorTranslator.translate(e, "добавление записи");
        }
    }

    /** Шаблон для поиска подстроки без учёта регистра (ILIKE %...%), спецсимволы LIKE экранируются. */
    protected static String likePattern(String query) {
        String escaped = query.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
