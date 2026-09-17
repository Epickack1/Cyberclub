package ru.mirea.cyberclub.service;

import java.util.ArrayList;
import java.util.List;

import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.repository.StatisticsRepository;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Статистика системы: собирает показатели и формирует строки отчёта,
 * общие для консоли и экспорта.
 */
public class StatisticsService {

    /** Одна строка отчёта: название показателя и его значение. */
    public record Indicator(String name, String value) {
    }

    private final StatisticsRepository statisticsRepository;

    public StatisticsService(StatisticsRepository statisticsRepository) {
        this.statisticsRepository = statisticsRepository;
    }

    public Statistics collect() {
        return statisticsRepository.collect();
    }

    /** Показатели в виде списка «название — значение» (порядок = порядок вывода). */
    public List<Indicator> toIndicators(Statistics s) {
        List<Indicator> list = new ArrayList<>();
        list.add(new Indicator("Всего клиентов", String.valueOf(s.totalClients())));
        list.add(new Indicator("Игровых мест (активных / всего)", s.activeStations() + " / " + s.totalStations()));
        list.add(new Indicator("Всего бронирований", String.valueOf(s.totalBookings())));
        for (BookingStatus status : BookingStatus.values()) {
            list.add(new Indicator("  - в статусе \"" + status.getTitle() + "\"", String.valueOf(s.countByStatus(status))));
        }
        list.add(new Indicator("Бронирований на сегодня", String.valueOf(s.bookingsToday())));
        list.add(new Indicator("Выручка по завершённым броням", DateTimeUtil.formatMoney(s.revenueCompleted())));
        list.add(new Indicator("Ожидаемая выручка (создано/подтверждено/активно)", DateTimeUtil.formatMoney(s.revenuePlanned())));
        list.add(new Indicator("Средняя длительность брони", DateTimeUtil.formatMinutes(s.averageDurationMinutes())));
        list.add(new Indicator("Самое популярное место", s.mostPopularStation() + " (" + s.mostPopularStationBookings() + " бр.)"));
        list.add(new Indicator("Самый активный клиент", s.mostActiveClient() + " (" + s.mostActiveClientBookings() + " бр.)"));
        return list;
    }
}
