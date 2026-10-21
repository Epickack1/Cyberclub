package ru.mirea.cyberclub.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;

/**
 * Бизнес-правила бронирования (перенесены из КР1/КР2 без изменений).
 * Класс не зависит от базы и Spring, поэтому правила проверяются обычными unit-тестами.
 */
public final class BookingRules {
    public static final int MIN_DURATION_MINUTES = 30;
    public static final int MAX_DURATION_HOURS = 12;
    public static final int PAST_GRACE_MINUTES = 10;
    public static final int ADULT_AGE = 18;
    public static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    public static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private BookingRules() {
    }

    /**
     * Стоимость = тариф × часы, длительность округляется вверх до 30 минут.
     * Например, 1 ч 40 мин за 150 руб./ч считается как 2 ч = 300 руб.
     */
    public static BigDecimal calculatePrice(BigDecimal hourlyRate, LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        long halfHours = (minutes + 29) / 30;
        return hourlyRate.multiply(BigDecimal.valueOf(halfHours))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    /** Интервал: конец позже начала, от 30 минут до 12 часов, начало не в прошлом (с запасом 10 минут). */
    public static void validateInterval(LocalDateTime start, LocalDateTime end, boolean mustBeInFuture,
                                        LocalDateTime now) {
        if (!end.isAfter(start)) {
            throw new BadRequestException("endTime", "Время окончания должно быть позже времени начала.");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < MIN_DURATION_MINUTES) {
            throw new BusinessRuleException("Минимальная длительность бронирования - " + MIN_DURATION_MINUTES + " минут.");
        }
        if (minutes > MAX_DURATION_HOURS * 60L) {
            throw new BusinessRuleException("Максимальная длительность бронирования - " + MAX_DURATION_HOURS + " часов.");
        }
        if (mustBeInFuture && start.isBefore(now.minusMinutes(PAST_GRACE_MINUTES))) {
            throw new BusinessRuleException("Нельзя создать бронирование в прошлом (начало " + format(start) + ").");
        }
    }

    /** Место на обслуживании бронировать нельзя. */
    public static void checkStationActive(Station station) {
        if (!station.isActive()) {
            throw new BusinessRuleException("Игровое место " + station.getName()
                    + " находится на обслуживании и недоступно для бронирования.");
        }
    }

    /** VIP и буткемп, а также ночное время 22:00-06:00 — только для клиентов 18+. */
    public static void checkAgeRestrictions(Client client, Station station, LocalDateTime start, LocalDateTime end) {
        int age = client.getAge(start.toLocalDate());
        if (age >= ADULT_AGE) {
            return;
        }
        if (station.getType().isAdultsOnly()) {
            throw new BusinessRuleException("Зона \"" + station.getType().getTitle()
                    + "\" доступна только клиентам 18+ (клиенту " + client.getNickname() + " - " + age + ").");
        }
        if (touchesNight(start, end)) {
            throw new BusinessRuleException("Ночное время (22:00-06:00) доступно только клиентам 18+ (клиенту "
                    + client.getNickname() + " - " + age + ").");
        }
    }

    /** Захватывает ли интервал [start, end) ночные часы 22:00-06:00. */
    public static boolean touchesNight(LocalDateTime start, LocalDateTime end) {
        if (isNight(start.toLocalTime())) {
            return true;
        }
        LocalDateTime nextNight = start.toLocalDate().atTime(NIGHT_START);
        if (!nextNight.isAfter(start)) {
            nextNight = nextNight.plusDays(1);
        }
        return nextNight.isBefore(end);
    }

    private static boolean isNight(LocalTime time) {
        return !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END);
    }

    public static String format(LocalDateTime time) {
        return time.format(FORMAT);
    }
}
