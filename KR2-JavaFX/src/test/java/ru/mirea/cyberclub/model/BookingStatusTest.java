package ru.mirea.cyberclub.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Схема переходов статусов бронирования.
 */
class BookingStatusTest {

    @Test
    @DisplayName("Из CREATED можно перейти только в CONFIRMED или CANCELLED")
    void createdTransitions() {
        assertEquals(EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED),
                BookingStatus.CREATED.allowedTransitions());
        assertTrue(BookingStatus.CREATED.canTransitionTo(BookingStatus.CONFIRMED));
        assertFalse(BookingStatus.CREATED.canTransitionTo(BookingStatus.ACTIVE));
        assertFalse(BookingStatus.CREATED.canTransitionTo(BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("Полная цепочка CREATED -> CONFIRMED -> ACTIVE -> COMPLETED разрешена")
    void happyPath() {
        assertTrue(BookingStatus.CREATED.canTransitionTo(BookingStatus.CONFIRMED));
        assertTrue(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.ACTIVE));
        assertTrue(BookingStatus.ACTIVE.canTransitionTo(BookingStatus.COMPLETED));
    }

    @Test
    @DisplayName("COMPLETED и CANCELLED — конечные статусы")
    void finalStatuses() {
        assertTrue(BookingStatus.COMPLETED.isFinal());
        assertTrue(BookingStatus.CANCELLED.isFinal());
        assertFalse(BookingStatus.ACTIVE.canTransitionTo(BookingStatus.CANCELLED));
        assertFalse(BookingStatus.COMPLETED.canTransitionTo(BookingStatus.ACTIVE));
        assertFalse(BookingStatus.CANCELLED.canTransitionTo(BookingStatus.CREATED));
    }

    @Test
    @DisplayName("Переход в null невозможен")
    void nullTransition() {
        assertFalse(BookingStatus.CREATED.canTransitionTo(null));
    }

    @Test
    @DisplayName("Отменённая бронь не занимает место, завершённая — не редактируется")
    void flags() {
        assertFalse(BookingStatus.CANCELLED.isOccupying());
        assertTrue(BookingStatus.CONFIRMED.isOccupying());
        assertTrue(BookingStatus.CREATED.isEditable());
        assertFalse(BookingStatus.COMPLETED.isEditable());
        assertTrue(BookingStatus.ACTIVE.isUnfinished());
        assertFalse(BookingStatus.COMPLETED.isUnfinished());
    }
}
