package ru.mirea.cyberclub.backend.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Сводные показатели клуба. Архивные брони в группировки не входят.
 *
 * @param totalBookings      всего бронирований (включая архив)
 * @param activeBookings     действующих (не в архиве)
 * @param archivedBookings   в архиве
 * @param paidBookings       оплаченных среди действующих
 * @param averagePrice       средняя стоимость брони
 * @param completedRevenue   выручка по завершённым броням
 * @param byStatus           количество по статусам
 * @param byStation          количество по игровым местам
 * @param byStationType      количество по зонам
 */
public record AnalyticsResponse(
        long totalBookings,
        long activeBookings,
        long archivedBookings,
        long paidBookings,
        BigDecimal averagePrice,
        BigDecimal completedRevenue,
        Map<String, Long> byStatus,
        Map<String, Long> byStation,
        Map<String, Long> byStationType) {
}
