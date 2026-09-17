package ru.mirea.cyberclub.repository.jdbc;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.repository.StatisticsRepository;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * Сводные показатели клуба: агрегирующие SQL-запросы (COUNT, SUM, AVG, GROUP BY).
 */
public class JdbcStatisticsRepository extends AbstractJdbcRepository implements StatisticsRepository {

    /** Пара «название — количество» для лидеров рейтинга. */
    private record Leader(String name, long count) {
    }

    public JdbcStatisticsRepository(DatabaseManager db) {
        super(db);
    }

    @Override
    public Statistics collect() {
        long totalClients = queryLong("SELECT COUNT(*) FROM clients", NO_PARAMS);
        long totalStations = queryLong("SELECT COUNT(*) FROM stations", NO_PARAMS);
        long activeStations = queryLong("SELECT COUNT(*) FROM stations WHERE active = TRUE", NO_PARAMS);
        long totalBookings = queryLong("SELECT COUNT(*) FROM bookings", NO_PARAMS);

        Map<BookingStatus, Long> byStatus = new EnumMap<>(BookingStatus.class);
        queryList("SELECT status, COUNT(*) AS cnt FROM bookings GROUP BY status", NO_PARAMS,
                rs -> Map.entry(BookingStatus.valueOf(rs.getString("status")), rs.getLong("cnt")))
                .forEach(entry -> byStatus.put(entry.getKey(), entry.getValue()));

        BigDecimal revenueCompleted = queryOne(
                "SELECT COALESCE(SUM(total_price), 0) FROM bookings WHERE status = 'COMPLETED'",
                NO_PARAMS, rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO);

        BigDecimal revenuePlanned = queryOne(
                "SELECT COALESCE(SUM(total_price), 0) FROM bookings WHERE status IN ('CREATED', 'CONFIRMED', 'ACTIVE')",
                NO_PARAMS, rs -> rs.getBigDecimal(1)).orElse(BigDecimal.ZERO);

        double avgMinutes = queryOne(
                "SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (end_time - start_time)) / 60), 0) "
                        + "FROM bookings WHERE status <> 'CANCELLED'",
                NO_PARAMS, rs -> rs.getDouble(1)).orElse(0.0);

        Optional<Leader> popularStation = queryOne("""
                SELECT s.name, COUNT(*) AS cnt
                FROM bookings b JOIN stations s ON s.id = b.station_id
                WHERE b.status <> 'CANCELLED'
                GROUP BY s.name
                ORDER BY cnt DESC, s.name
                LIMIT 1
                """, NO_PARAMS, rs -> new Leader(rs.getString("name"), rs.getLong("cnt")));

        Optional<Leader> activeClient = queryOne("""
                SELECT c.nickname, COUNT(*) AS cnt
                FROM bookings b JOIN clients c ON c.id = b.client_id
                WHERE b.status <> 'CANCELLED'
                GROUP BY c.nickname
                ORDER BY cnt DESC, c.nickname
                LIMIT 1
                """, NO_PARAMS, rs -> new Leader(rs.getString("nickname"), rs.getLong("cnt")));

        long bookingsToday = queryLong(
                "SELECT COUNT(*) FROM bookings WHERE CAST(start_time AS DATE) = CURRENT_DATE AND status <> 'CANCELLED'",
                NO_PARAMS);

        return new Statistics(
                totalClients, totalStations, activeStations, totalBookings, byStatus,
                revenueCompleted, revenuePlanned, avgMinutes,
                popularStation.map(Leader::name).orElse("-"), popularStation.map(Leader::count).orElse(0L),
                activeClient.map(Leader::name).orElse("-"), activeClient.map(Leader::count).orElse(0L),
                bookingsToday);
    }
}
