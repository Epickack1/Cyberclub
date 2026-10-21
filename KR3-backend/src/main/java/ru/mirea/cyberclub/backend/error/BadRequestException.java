package ru.mirea.cyberclub.backend.error;

/** Неверные данные запроса — HTTP 400. Поле {@code field} подсказывает клиенту, что подсветить. */
public class BadRequestException extends RuntimeException {
    private final String field;

    public BadRequestException(String message) {
        this(null, message);
    }

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() { return field; }
}
