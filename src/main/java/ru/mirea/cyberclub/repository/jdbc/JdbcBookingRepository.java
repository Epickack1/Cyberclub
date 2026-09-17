package ru.mirea.cyberclub.repository.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * JDBC-реализация хранилища бронирований (таблица bookings).
 * Все выборки соединяют bookings с clients и stations, чтобы сразу получить
 * ник клиента и название места для отображения.
 */
public class JdbcBookingRepository extends AbstractJdbcRepository implements BookingRepository {

    private static final String SELECT = """
            SELECT b.id, b.client_id, b.station_id, b.start_time, b.end_time, b.status,
                   b.total_price, b.comment, b.created_at,
                   c.nickname AS client_nickname, s.name AS station_name
            FROM bookings b
            JOIN clients  c ON c.id = b.client_id
            JOIN stations s ON s.id = b.station_id
            """;

    private static final String ORDER = " ORDER BY b.start_time, b.id";

    public JdbcBookingRepository(DatabaseManager db) {
        super(db);
    }

    @Override
    public Booking save(Booking booking) {
        String sql = "INSERT INTO bookings (client_id, station_id, start_time, end_time, status, total_price, comment) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        long id = executeInsert(sql, ps -> {
            ps.setLong(1, booking.getClientId());
            ps.setLong(2, booking.getStationId());
            ps.setTimestamp(3, Timestamp.valueOf(booking.getStartTime()));
            ps.setTimestamp(4, Timestamp.valueOf(booking.getEndTime()));
            ps.setString(5, booking.getStatus().name());
            ps.setBigDecimal(6, booking.getTotalPrice());
            ps.setString(7, booking.getComment());
        });
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return queryOne(SELECT + " WHERE b.id = ?", ps -> ps.setLong(1, id), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findAll() {
        return queryList(SELECT + ORDER, NO_PARAMS, JdbcBookingRepository::mapRow);
    }

    @Override
    public boolean update(Booking booking) {
        String sql = "UPDATE bookings SET client_id = ?, station_id = ?, start_time = ?, end_time = ?, "
                + "status = ?, total_price = ?, comment = ? WHERE id = ?";
        return executeUpdate(sql, ps -> {
            ps.setLong(1, booking.getClientId());
            ps.setLong(2, booking.getStationId());
            ps.setTimestamp(3, Timestamp.valueOf(booking.getStartTime()));
            ps.setTimestamp(4, Timestamp.valueOf(booking.getEndTime()));
            ps.setString(5, booking.getStatus().name());
            ps.setBigDecimal(6, booking.getTotalPrice());
            ps.setString(7, booking.getComment());
            ps.setLong(8, booking.getId());
        }) > 0;
    }

    @Override
    public boolean deleteById(Long id) {
        return executeUpdate("DELETE FROM bookings WHERE id = ?", ps -> ps.setLong(1, id)) > 0;
    }

    @Override
    public long count() {
        return queryLong("SELECT COUNT(*) FROM bookings", NO_PARAMS);
    }

    // ------------------------------------------------------------------ поиск

    @Override
    public List<Booking> searchByClientNickname(String query) {
        return queryList(SELECT + " WHERE c.nickname ILIKE ?" + ORDER,
                ps -> ps.setString(1, likePattern(query)), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> searchByStationName(String query) {
        return queryList(SELECT + " WHERE s.name ILIKE ?" + ORDER,
                ps -> ps.setString(1, likePattern(query)), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findByDate(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = from.plusDays(1);
        return queryList(SELECT + " WHERE b.start_time >= ? AND b.start_time < ?" + ORDER, ps -> {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
        }, JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> searchByComment(String query) {
        return queryList(SELECT + " WHERE b.comment ILIKE ?" + ORDER,
                ps -> ps.setString(1, likePattern(query)), JdbcBookingRepository::mapRow);
    }

    // ------------------------------------------------------------- фильтрация

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return queryList(SELECT + " WHERE b.status = ?" + ORDER,
                ps -> ps.setString(1, status.name()), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findByStationType(StationType type) {
        return queryList(SELECT + " WHERE s.type = ?" + ORDER,
                ps -> ps.setString(1, type.name()), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findByPeriod(LocalDateTime from, LocalDateTime to) {
        return queryList(SELECT + " WHERE b.start_time >= ? AND b.start_time <= ?" + ORDER, ps -> {
            ps.setTimestamp(1, Timestamp.valueOf(from));
            ps.setTimestamp(2, Timestamp.valueOf(to));
        }, JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findByClientId(Long clientId) {
        return queryList(SELECT + " WHERE b.client_id = ?" + ORDER,
                ps -> ps.setLong(1, clientId), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findByStationId(Long stationId) {
        return queryList(SELECT + " WHERE b.station_id = ?" + ORDER,
                ps -> ps.setLong(1, stationId), JdbcBookingRepository::mapRow);
    }

    @Override
    public List<Booking> findOverlapping(Long stationId, LocalDateTime start, LocalDateTime end, Long excludeBookingId) {
        // Интервалы полуоткрытые: [start, end) пересекается с [s, e), если start < e и end > s
        String sql = SELECT + """
                 WHERE b.station_id = ?
                   AND b.status <> 'CANCELLED'
                   AND b.start_time < ?
                   AND b.end_time > ?
                   AND b.id <> ?
                """ + ORDER;
        return queryList(sql, ps -> {
            ps.setLong(1, stationId);
            ps.setTimestamp(2, Timestamp.valueOf(end));
            ps.setTimestamp(3, Timestamp.valueOf(start));
            ps.setLong(4, excludeBookingId == null ? -1L : excludeBookingId);
        }, JdbcBookingRepository::mapRow);
    }

    @Override
    public boolean existsUnfinishedByClientId(Long clientId) {
        String sql = "SELECT COUNT(*) FROM bookings WHERE client_id = ? AND status IN ('CREATED', 'CONFIRMED', 'ACTIVE')";
        return queryLong(sql, ps -> ps.setLong(1, clientId)) > 0;
    }

    @Override
    public boolean existsByStationId(Long stationId) {
        return queryLong("SELECT COUNT(*) FROM bookings WHERE station_id = ?", ps -> ps.setLong(1, stationId)) > 0;
    }

    static Booking mapRow(ResultSet rs) throws SQLException {
        Booking booking = new Booking();
        booking.setId(rs.getLong("id"));
        booking.setClientId(rs.getLong("client_id"));
        booking.setStationId(rs.getLong("station_id"));
        booking.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        booking.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        booking.setStatus(BookingStatus.valueOf(rs.getString("status")));
        booking.setTotalPrice(rs.getBigDecimal("total_price"));
        booking.setComment(rs.getString("comment"));
        booking.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        booking.setClientNickname(rs.getString("client_nickname"));
        booking.setStationName(rs.getString("station_name"));
        return booking;
    }
}
