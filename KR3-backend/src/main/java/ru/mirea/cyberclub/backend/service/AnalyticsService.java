package ru.mirea.cyberclub.backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.StationType;
import ru.mirea.cyberclub.backend.dto.AnalyticsResponse;
import ru.mirea.cyberclub.backend.repository.BookingRepository;

/** Сводные показатели клуба. Все числа считает PostgreSQL агрегатными запросами. */
@Service
public class AnalyticsService {
    private final BookingRepository bookings;

    public AnalyticsService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse summary() {
        long active = bookings.countByArchived(false);
        long archived = bookings.countByArchived(true);
        BigDecimal average = bookings.averagePrice();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus status : BookingStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        bookings.countByStatus().forEach(row -> byStatus.put(((BookingStatus) row[0]).name(), (Long) row[1]));

        Map<String, Long> byStationType = new LinkedHashMap<>();
        for (StationType type : StationType.values()) {
            byStationType.put(type.name(), 0L);
        }
        bookings.countByStationType().forEach(row -> byStationType.put(((StationType) row[0]).name(), (Long) row[1]));

        return new AnalyticsResponse(
                active + archived,
                active,
                archived,
                bookings.countByArchivedFalseAndPaid(true),
                average == null ? BigDecimal.ZERO : average.setScale(2, RoundingMode.HALF_UP),
                bookings.sumPriceByStatus(BookingStatus.COMPLETED),
                byStatus,
                toMap(bookings.countByStation()),
                byStationType);
    }

    private static Map<String, Long> toMap(List<Object[]> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put((String) row[0], (Long) row[1]));
        return result;
    }
}
