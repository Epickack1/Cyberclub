package ru.mirea.cyberclub.service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;

/**
 * Критерии поиска и фильтрации списка бронирований.
 * Значение null в любом поле означает «не фильтровать по этому полю».
 *
 * <p>Контроллер JavaFX только собирает значения из элементов управления, а правило
 * отбора (какая бронь подходит) описано здесь — в слое бизнес-логики, и проверяется тестами.</p>
 *
 * @param text        строка поиска: № брони, ник или ФИО клиента, название места, комментарий
 * @param status      статус бронирования
 * @param stationType тип (зона) игрового места
 * @param clientId    клиент
 * @param from        начало периода (дата начала брони, включительно)
 * @param to          конец периода (включительно)
 */
public record BookingFilter(String text, BookingStatus status, StationType stationType,
                            Long clientId, LocalDate from, LocalDate to) {

    /** Фильтр, пропускающий все записи. */
    public static final BookingFilter NONE = new BookingFilter(null, null, null, null, null, null);

    /**
     * Проверяет критерии и строит предикат для {@link javafx.collections.transformation.FilteredList}.
     *
     * @throws ValidationException если конец периода раньше начала
     */
    public Predicate<Booking> toPredicate() {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("period", "Конец периода раньше его начала.");
        }
        List<String> words = words(text);
        return booking -> matchesText(booking, words)
                && (status == null || booking.getStatus() == status)
                && (stationType == null || booking.getStationType() == stationType)
                && (clientId == null || clientId.equals(booking.getClientId()))
                && (from == null || !booking.getStartTime().toLocalDate().isBefore(from))
                && (to == null || !booking.getStartTime().toLocalDate().isAfter(to));
    }

    /** Есть ли хотя бы один критерий (для подписи «найдено N из M»). */
    public boolean isActive() {
        return !words(text).isEmpty() || status != null || stationType != null
                || clientId != null || from != null || to != null;
    }

    /**
     * Поиск по нескольким смысловым полям: каждое слово запроса должно встретиться
     * хотя бы в одном поле. Например, «shadow vip» найдёт брони клиента Shadow на местах VIP.
     */
    private static boolean matchesText(Booking booking, List<String> words) {
        if (words.isEmpty()) {
            return true;
        }
        String haystack = String.join("\n",
                "#" + booking.getId(),
                nullToEmpty(booking.getClientNickname()),
                nullToEmpty(booking.getClientFullName()),
                nullToEmpty(booking.getStationName()),
                nullToEmpty(booking.getComment())).toLowerCase(Locale.ROOT);
        return words.stream().allMatch(word -> word.startsWith("#")
                ? String.valueOf(booking.getId()).equals(word.substring(1))
                : haystack.contains(word));
    }

    private static List<String> words(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.trim().toLowerCase(Locale.ROOT).split("\\s+")).toList();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
