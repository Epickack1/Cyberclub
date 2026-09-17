package ru.mirea.cyberclub.service;

import java.util.Comparator;

import ru.mirea.cyberclub.model.Booking;

/**
 * Способы сортировки списка бронирований. Каждый элемент перечисления
 * хранит свой {@link Comparator} — сортировка выполняется средствами
 * Java Collections Framework / Stream API, а не SQL.
 */
public enum BookingSortField {
    START_TIME("По времени начала", Comparator.comparing(Booking::getStartTime)),
    PRICE("По стоимости", Comparator.comparing(Booking::getTotalPrice)),
    STATUS("По статусу", Comparator.comparing(Booking::getStatus)),
    CLIENT("По нику клиента", Comparator.comparing(Booking::getClientNickname, String.CASE_INSENSITIVE_ORDER)),
    DURATION("По длительности", Comparator.comparing(Booking::getDuration));

    private final String title;
    private final Comparator<Booking> comparator;

    BookingSortField(String title, Comparator<Booking> comparator) {
        this.title = title;
        this.comparator = comparator;
    }

    public String getTitle() {
        return title;
    }

    /** Компаратор с учётом направления; при равенстве ключей — по ID. */
    public Comparator<Booking> comparator(boolean ascending) {
        Comparator<Booking> base = ascending ? comparator : comparator.reversed();
        return base.thenComparing(Booking::getId);
    }

    @Override
    public String toString() {
        return title;
    }
}
