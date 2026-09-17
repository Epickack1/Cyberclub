package ru.mirea.cyberclub.exception;

/**
 * Нарушение бизнес-правила предметной области: запрещённый переход статуса,
 * пересечение броней, бронирование неактивного места и т.п.
 */
public class BusinessRuleException extends AppException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
