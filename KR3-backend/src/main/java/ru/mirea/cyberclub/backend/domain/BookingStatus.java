package ru.mirea.cyberclub.backend.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Статус бронирования и допустимые переходы.
 *
 * <pre>
 * CREATED   -> CONFIRMED | CANCELLED
 * CONFIRMED -> ACTIVE    | CANCELLED
 * ACTIVE    -> COMPLETED
 * COMPLETED, CANCELLED — конечные
 * </pre>
 */
public enum BookingStatus {
    CREATED("Создано"),
    CONFIRMED("Подтверждено"),
    ACTIVE("Активно"),
    COMPLETED("Завершено"),
    CANCELLED("Отменено");

    private final String title;

    BookingStatus(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public Set<BookingStatus> allowedTransitions() {
        return switch (this) {
            case CREATED -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(ACTIVE, CANCELLED);
            case ACTIVE -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(BookingStatus.class);
        };
    }

    public boolean canTransitionTo(BookingStatus next) {
        return next != null && allowedTransitions().contains(next);
    }

    /** Место и время можно менять только до начала игры. */
    public boolean isEditable() {
        return this == CREATED || this == CONFIRMED;
    }

    public boolean isUnfinished() {
        return this == CREATED || this == CONFIRMED || this == ACTIVE;
    }
}
