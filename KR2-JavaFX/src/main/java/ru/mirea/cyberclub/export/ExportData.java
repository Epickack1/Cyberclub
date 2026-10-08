package ru.mirea.cyberclub.export;

import java.util.List;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Данные для выгрузки: бронирования в том порядке, в каком они показаны в таблице,
 * и показатели по этой выборке.
 */
public record ExportData(List<Booking> bookings, List<StatisticsService.Indicator> indicators) {
}
