package ru.mirea.cyberclub.backend.domain;

import java.math.BigDecimal;

/**
 * Риск неоплаты брони — вычисляемый показатель. Клиент его не задаёт: сервер пересчитывает
 * значение при каждом сохранении брони методом {@link #of}. Те же правила — в миграции V5.
 *
 * <pre>
 * NONE   — оплачено или отменено
 * HIGH   — не оплачено, а игра уже идёт или закончилась, либо сумма от 2000 руб.
 * MEDIUM — не оплачено, сумма от 800 руб.
 * LOW    — не оплачено, сумма меньше 800 руб.
 * </pre>
 */
public enum RiskLevel {
    NONE("Нет"),
    LOW("Низкий"),
    MEDIUM("Средний"),
    HIGH("Высокий");

    private final String title;

    RiskLevel(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public static final BigDecimal MEDIUM_FROM = new BigDecimal("800");
    public static final BigDecimal HIGH_FROM = new BigDecimal("2000");

    public static RiskLevel of(BookingStatus status, boolean paid, BigDecimal price) {
        if (paid || status == BookingStatus.CANCELLED) {
            return NONE;
        }
        BigDecimal sum = price == null ? BigDecimal.ZERO : price;
        if (status == BookingStatus.ACTIVE || status == BookingStatus.COMPLETED || sum.compareTo(HIGH_FROM) >= 0) {
            return HIGH;
        }
        return sum.compareTo(MEDIUM_FROM) >= 0 ? MEDIUM : LOW;
    }
}
