package ru.mirea.cyberclub.backend.error;

/**
 * Нарушено бизнес-правило клуба (VIP только 18+, место на обслуживании, неверный переход статуса...).
 * Данные запроса корректны по формату, но действие запрещено — HTTP 400 с кодом BUSINESS_RULE.
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) { super(message); }
}
