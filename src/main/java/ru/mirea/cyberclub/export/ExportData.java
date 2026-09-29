package ru.mirea.cyberclub.export;

import java.util.List;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Данные для экспорта: всё, что нужно любому формату выгрузки.
 * Меню собирает один раз и передаёт выбранному {@link Exporter}.
 */
public record ExportData(
        List<Client> clients,
        List<Station> stations,
        List<Booking> bookings,
        List<StatisticsService.Indicator> indicators) {
}
