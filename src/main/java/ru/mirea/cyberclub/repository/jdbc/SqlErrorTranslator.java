package ru.mirea.cyberclub.repository.jdbc;

import java.sql.SQLException;
import java.util.Map;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.DataAccessException;
import ru.mirea.cyberclub.exception.ValidationException;

/**
 * Переводит {@link SQLException} в исключения приложения по коду SQLSTATE.
 * Ограничения БД — последняя линия защиты: сервисы проверяют правила заранее,
 * но если запрос всё же нарушил ограничение, пользователь получит понятное сообщение.
 */
public final class SqlErrorTranslator {

    /** Понятные сообщения для именованных ограничений из db/01_schema.sql. */
    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.ofEntries(
            Map.entry("clients_nickname_key", "Клиент с таким ником уже существует."),
            Map.entry("clients_phone_key", "Клиент с таким телефоном уже существует."),
            Map.entry("clients_email_key", "Клиент с таким email уже существует."),
            Map.entry("stations_name_key", "Игровое место с таким названием уже существует."),
            Map.entry("chk_clients_nickname", "Ник: 3-20 символов, только латиница, цифры и \"_\"."),
            Map.entry("chk_clients_phone", "Телефон должен быть в формате +7XXXXXXXXXX."),
            Map.entry("chk_clients_email", "Некорректный email."),
            Map.entry("chk_clients_birth_date", "Дата рождения не может быть в будущем."),
            Map.entry("chk_stations_type", "Недопустимый тип игрового места."),
            Map.entry("chk_stations_rate", "Тариф должен быть больше нуля."),
            Map.entry("chk_bookings_status", "Недопустимый статус бронирования."),
            Map.entry("chk_bookings_price", "Стоимость не может быть отрицательной."),
            Map.entry("chk_bookings_time", "Время окончания должно быть позже времени начала."),
            Map.entry("excl_bookings_overlap", "Игровое место уже занято в это время (ограничение БД)."),
            Map.entry("fk_bookings_client", "Указанный клиент не существует."),
            Map.entry("fk_bookings_station", "Указанное игровое место не существует.")
    );

    private SqlErrorTranslator() {
    }

    public static AppException translate(SQLException e, String action) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        String constraint = constraintName(e);
        String known = constraint == null ? null : CONSTRAINT_MESSAGES.get(constraint);

        if (state.startsWith("08")) {
            return new DataAccessException("Нет соединения с базой данных: " + e.getMessage(), e);
        }
        return switch (state) {
            case "23505" -> new ValidationException(known != null ? known
                    : "Нарушение уникальности данных (" + constraint + ").");
            case "23503" -> new BusinessRuleException(known != null ? known
                    : "Запись связана с другими данными и не может быть изменена или удалена (" + constraint + ").");
            case "23514" -> new ValidationException(known != null ? known
                    : "Нарушено ограничение базы данных (" + constraint + ").");
            case "23P01" -> new BusinessRuleException(known != null ? known
                    : "Нарушено ограничение-исключение базы данных (" + constraint + ").");
            case "23502" -> new ValidationException("Обязательное поле не заполнено: " + e.getMessage());
            default -> new DataAccessException("Ошибка выполнения SQL-запроса (" + action + "): " + e.getMessage(), e);
        };
    }

    private static String constraintName(SQLException e) {
        if (e instanceof PSQLException psql) {
            ServerErrorMessage message = psql.getServerErrorMessage();
            if (message != null) {
                return message.getConstraint();
            }
        }
        return null;
    }
}
