package ru.mirea.cyberclub.exception;

/**
 * Некорректные данные объекта: пустое обязательное поле, неверный формат,
 * нарушение уникальности и т.п.
 */
public class ValidationException extends AppException {

    public ValidationException(String message) {
        super(message);
    }
}
