package ru.mirea.cyberclub.exception;

/**
 * Некорректные данные объекта: пустое обязательное поле, неверный формат,
 * нарушение уникальности и т.п.
 *
 * <p>Необязательный ключ поля ({@link #getField()}) позволяет форме JavaFX
 * подсветить именно тот элемент ввода, который заполнен неверно.</p>
 */
public class ValidationException extends AppException {

    private final String field;

    public ValidationException(String message) {
        this(null, message);
    }

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Ключ поля формы (например "phone") или null, если ошибка не относится к одному полю. */
    public String getField() {
        return field;
    }
}
