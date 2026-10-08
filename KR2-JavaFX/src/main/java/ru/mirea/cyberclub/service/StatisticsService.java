package ru.mirea.cyberclub.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.repository.StatisticsRepository;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Статистика системы: собирает показатели и формирует строки отчёта
 * для вкладки «Статистика» и для экспорта.
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

    /**
     * Общие показатели клуба в виде «название — значение» (порядок = порядок вывода).
     * Распределение по статусам показывается отдельно — круговой диаграммой.
     */
    public List<Indicator> toIndicators(Statistics s) {
        List<Indicator> list = new ArrayList<>();
        list.add(new Indicator("Всего клиентов", String.valueOf(s.totalClients())));
        list.add(new Indicator("Игровых мест (активных / всего)", s.activeStations() + " / " + s.totalStations()));
        list.add(new Indicator("Всего бронирований", String.valueOf(s.totalBookings())));
        list.add(new Indicator("Бронирований на сегодня", String.valueOf(s.bookingsToday())));
        list.add(new Indicator("Выручка по завершённым броням", DateTimeUtil.formatMoney(s.revenueCompleted())));
        list.add(new Indicator("Ожидаемая выручка (незавершённые брони)", DateTimeUtil.formatMoney(s.revenuePlanned())));
        list.add(new Indicator("Средняя длительность брони", DateTimeUtil.formatMinutes(s.averageDurationMinutes())));
        list.add(new Indicator("Самое популярное место", s.mostPopularStation() + " (" + s.mostPopularStationBookings() + " бр.)"));
        list.add(new Indicator("Самый активный клиент", s.mostActiveClient() + " (" + s.mostActiveClientBookings() + " бр.)"));
        return list;
    }

    /**
     * Показатели для произвольного набора бронирований — например, для текущей
     * выборки в таблице с учётом поиска и фильтров. Считаются в Java через Stream API.
     * Отменённые брони не входят в денежные показатели и длительность.
     */
    public List<Indicator> forBookings(List<Booking> bookings) {
        List<Booking> paid = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .toList();
        long cancelled = bookings.size() - paid.size();

        BigDecimal revenue = paid.stream()
                .map(Booking::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal average = paid.isEmpty() ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(paid.size()), 2, RoundingMode.HALF_UP);
        Optional<BigDecimal> min = paid.stream().map(Booking::getTotalPrice).min(Comparator.naturalOrder());
        Optional<BigDecimal> max = paid.stream().map(Booking::getTotalPrice).max(Comparator.naturalOrder());
        double avgMinutes = paid.stream().mapToLong(b -> b.getDuration().toMinutes()).average().orElse(0);
        double cancelledShare = bookings.isEmpty() ? 0 : cancelled * 100.0 / bookings.size();

        List<Indicator> list = new ArrayList<>();
        list.add(new Indicator("Бронирований в выборке", String.valueOf(bookings.size())));
        list.add(new Indicator("Сумма (без отменённых)", DateTimeUtil.formatMoney(revenue)));
        list.add(new Indicator("Средняя стоимость брони", DateTimeUtil.formatMoney(average)));
        list.add(new Indicator("Минимальная стоимость брони", min.map(DateTimeUtil::formatMoney).orElse("-")));
        list.add(new Indicator("Максимальная стоимость брони", max.map(DateTimeUtil::formatMoney).orElse("-")));
        list.add(new Indicator("Средняя длительность", DateTimeUtil.formatMinutes(avgMinutes)));
        list.add(new Indicator("Доля отменённых", String.format(Locale.ROOT, "%.1f %%", cancelledShare)));
        return list;
    }

    /** Сумма неотменённых броней по типам игровых мест (для столбчатой диаграммы). */
    public Map<StationType, BigDecimal> revenueByStationType(List<Booking> bookings) {
        Map<StationType, BigDecimal> result = new EnumMap<>(StationType.class);
        for (StationType type : StationType.values()) {
            result.put(type, BigDecimal.ZERO);
        }
        bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED && b.getStationType() != null)
                .forEach(b -> result.merge(b.getStationType(), b.getTotalPrice(), BigDecimal::add));
        return result;
    }
}
