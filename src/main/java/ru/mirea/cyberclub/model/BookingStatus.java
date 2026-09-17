package ru.mirea.cyberclub.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Статус бронирования и допустимые переходы между статусами.
 *
 * <pre>
 * CREATED   -> CONFIRMED | CANCELLED
 * CONFIRMED -> ACTIVE    | CANCELLED
 * ACTIVE    -> COMPLETED
 * COMPLETED, CANCELLED — конечные состояния
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

    /** Статусы, в которые разрешён переход из текущего. */
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

    /** Конечный статус: запись больше нельзя менять. */
    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }

    /** Бронь в этом статусе занимает игровое место (учитывается при проверке пересечений). */
    public boolean isOccupying() {
        return this != CANCELLED;
    }

    /** Бронь ещё не завершена (мешает удалению клиента). */
    public boolean isUnfinished() {
        return this == CREATED || this == CONFIRMED || this == ACTIVE;
    }

    /** Время и место брони можно менять только до её начала. */
    public boolean isEditable() {
        return this == CREATED || this == CONFIRMED;
    }

    @Override
    public String toString() {
        return title;
    }
}
