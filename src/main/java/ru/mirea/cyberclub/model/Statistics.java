package ru.mirea.cyberclub.model;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Сводные показатели работы клуба (пункт меню «Статистика»).
 * Неизменяемый объект-значение: record.
 */
public record Statistics(
        long totalClients,
        long totalStations,
        long activeStations,
        long totalBookings,
        Map<BookingStatus, Long> bookingsByStatus,
        BigDecimal revenueCompleted,
        BigDecimal revenuePlanned,
        double averageDurationMinutes,
        String mostPopularStation,
        long mostPopularStationBookings,
        String mostActiveClient,
        long mostActiveClientBookings,
        long bookingsToday
) {

    public long countByStatus(BookingStatus status) {
        return bookingsByStatus.getOrDefault(status, 0L);
    }
}
