package ru.mirea.cyberclub.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.domain.StationType;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;

/** Бизнес-правила бронирования без базы данных. */
class BookingRulesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);
    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 15, 14, 0);

    private final Client adult = new Client("Shadow", "Иванов Иван", "+79161234567", "a@b.ru", LocalDate.of(2001, 3, 15));
    private final Client teen = new Client("Kirya2010", "Смирнов Кирилл", "+79161234569", "k@b.ru", LocalDate.of(2010, 11, 5));
    private final Station pc = new Station("PC-01", StationType.STANDARD, new BigDecimal("150.00"), null);
    private final Station vip = new Station("VIP-01", StationType.VIP, new BigDecimal("300.00"), null);

    @Test
    void priceRoundsDurationUpToHalfHour() {
        // 1 ч 40 мин -> 2 ч по 150 руб.
        assertThat(BookingRules.calculatePrice(new BigDecimal("150.00"), DAY, DAY.plusMinutes(100)))
                .isEqualByComparingTo("300.00");
        // ровно 1,5 ч -> 225 руб.
        assertThat(BookingRules.calculatePrice(new BigDecimal("150.00"), DAY, DAY.plusMinutes(90)))
                .isEqualByComparingTo("225.00");
    }

    @Test
    void durationMustBeBetween30MinutesAnd12Hours() {
        assertThatThrownBy(() -> BookingRules.validateInterval(DAY, DAY.plusMinutes(20), true, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Минимальная");
        assertThatThrownBy(() -> BookingRules.validateInterval(DAY, DAY.plusHours(12).plusMinutes(1), true, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Максимальная");
        assertThatCode(() -> BookingRules.validateInterval(DAY, DAY.plusHours(12), true, NOW)).doesNotThrowAnyException();
    }

    @Test
    void endMustBeAfterStartAndStartNotInPast() {
        assertThatThrownBy(() -> BookingRules.validateInterval(DAY, DAY.minusHours(1), true, NOW))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> BookingRules.validateInterval(NOW.minusHours(2), NOW.minusHours(1), true, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("в прошлом");
        // при изменении без смены времени прошлое допустимо
        assertThatCode(() -> BookingRules.validateInterval(NOW.minusHours(2), NOW.minusHours(1), false, NOW))
                .doesNotThrowAnyException();
    }

    @Test
    void vipZoneOnlyForAdults() {
        assertThatThrownBy(() -> BookingRules.checkAgeRestrictions(teen, vip, DAY, DAY.plusHours(2)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("18+");
        assertThatCode(() -> BookingRules.checkAgeRestrictions(adult, vip, DAY, DAY.plusHours(2))).doesNotThrowAnyException();
        assertThatCode(() -> BookingRules.checkAgeRestrictions(teen, pc, DAY, DAY.plusHours(2))).doesNotThrowAnyException();
    }

    @Test
    void nightTimeOnlyForAdults() {
        LocalDateTime evening = DAY.withHour(20);
        assertThat(BookingRules.touchesNight(evening, evening.plusHours(3))).isTrue();   // 20:00-23:00
        assertThat(BookingRules.touchesNight(evening, evening.plusHours(2))).isFalse();  // 20:00-22:00
        assertThatThrownBy(() -> BookingRules.checkAgeRestrictions(teen, pc, evening, evening.plusHours(3)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Ночное");
    }

    @Test
    void stationUnderMaintenanceCannotBeBooked() {
        Station broken = new Station("PC-04", StationType.STANDARD, new BigDecimal("150.00"), null);
        broken.setActive(false);
        assertThatThrownBy(() -> BookingRules.checkStationActive(broken))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("обслуживании");
    }
}
