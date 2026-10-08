package ru.mirea.cyberclub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;

class BookingFilterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 7);

    static Booking booking(long id, long clientId, String nick, String fullName, String station, StationType type,
                           LocalDateTime start, int hours, BookingStatus status, String price, String comment) {
        Booking b = new Booking(clientId, id * 10, start, start.plusHours(hours), comment);
        b.setId(id);
        b.setClientNickname(nick);
        b.setClientFullName(fullName);
        b.setStationName(station);
        b.setStationType(type);
        b.setStatus(status);
        b.setTotalPrice(new BigDecimal(price));
        return b;
    }

    private final List<Booking> all = List.of(
            booking(1, 1, "Shadow", "Иванов Иван", "PC-01", StationType.STANDARD, DAY.atTime(18, 0), 3,
                    BookingStatus.COMPLETED, "450", "Вечерняя катка в CS2"),
            booking(2, 2, "Nova_Q", "Петрова Анна", "VIP-01", StationType.VIP, DAY.plusDays(1).atTime(19, 0), 4,
                    BookingStatus.CONFIRMED, "1200", "Стрим на Twitch"),
            booking(12, 1, "Shadow", "Иванов Иван", "VIP-02", StationType.VIP, DAY.plusDays(3).atTime(12, 0), 2,
                    BookingStatus.CANCELLED, "600", null));

    private List<Long> ids(BookingFilter filter) {
        return all.stream().filter(filter.toPredicate()).map(Booking::getId).toList();
    }

    @Test
    @DisplayName("Пустой фильтр пропускает все записи")
    void emptyFilter() {
        assertThat(ids(BookingFilter.NONE)).containsExactly(1L, 2L, 12L);
        assertThat(BookingFilter.NONE.isActive()).isFalse();
    }

    @Test
    @DisplayName("Поиск по нику, ФИО, месту и комментарию без учёта регистра")
    void textSearchAcrossFields() {
        assertThat(ids(new BookingFilter("shadow", null, null, null, null, null))).containsExactly(1L, 12L);
        assertThat(ids(new BookingFilter("ПЕТРОВА", null, null, null, null, null))).containsExactly(2L);
        assertThat(ids(new BookingFilter("vip-0", null, null, null, null, null))).containsExactly(2L, 12L);
        assertThat(ids(new BookingFilter("twitch", null, null, null, null, null))).containsExactly(2L);
    }

    @Test
    @DisplayName("Несколько слов — все должны встретиться; #N ищет по номеру брони")
    void multiWordAndIdSearch() {
        assertThat(ids(new BookingFilter("shadow vip", null, null, null, null, null))).containsExactly(12L);
        assertThat(ids(new BookingFilter("#12", null, null, null, null, null))).containsExactly(12L);
        assertThat(ids(new BookingFilter("#1", null, null, null, null, null))).containsExactly(1L);
    }

    @Test
    @DisplayName("Фильтры по статусу, зоне и клиенту комбинируются")
    void combinedFilters() {
        assertThat(ids(new BookingFilter(null, null, StationType.VIP, null, null, null))).containsExactly(2L, 12L);
        assertThat(ids(new BookingFilter(null, BookingStatus.CANCELLED, StationType.VIP, null, null, null))).containsExactly(12L);
        assertThat(ids(new BookingFilter(null, null, null, 1L, null, null))).containsExactly(1L, 12L);
        assertThat(new BookingFilter(null, null, null, 1L, null, null).isActive()).isTrue();
    }

    @Test
    @DisplayName("Период включает обе границы")
    void periodFilter() {
        assertThat(ids(new BookingFilter(null, null, null, null, DAY, DAY.plusDays(1)))).containsExactly(1L, 2L);
        assertThat(ids(new BookingFilter(null, null, null, null, DAY.plusDays(2), null))).containsExactly(12L);
    }

    @Test
    @DisplayName("Конец периода раньше начала — понятная ошибка с ключом поля")
    void invalidPeriod() {
        BookingFilter filter = new BookingFilter(null, null, null, null, DAY, DAY.minusDays(1));
        assertThatThrownBy(filter::toPredicate)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Конец периода")
                .extracting(e -> ((ValidationException) e).getField()).isEqualTo("period");
    }
}
