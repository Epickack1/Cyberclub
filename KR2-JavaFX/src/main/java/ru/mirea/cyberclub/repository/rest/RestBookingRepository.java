package ru.mirea.cyberclub.repository.rest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.type.TypeReference;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.BookingRepository;

/**
 * REST-реализация хранилища бронирований: запросы к /api/bookings.
 * Поиск и фильтры передаются параметрами запроса и выполняются на сервере теми же SQL-запросами.
 */
public class RestBookingRepository implements BookingRepository {

    private static final TypeReference<List<Booking>> LIST = new TypeReference<>() { };

    private final RestClient rest;

    public RestBookingRepository(RestClient rest) {
        this.rest = rest;
    }

    @Override
    public Booking save(Booking booking) {
        return rest.post("/bookings", booking, Booking.class);
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return rest.find("/bookings/" + id, Booking.class);
    }

    @Override
    public List<Booking> findAll() {
        return list(Map.of());
    }

    @Override
    public boolean update(Booking booking) {
        if (booking.getComment() == null) {
            booking.setComment("");     // на сервере null означает «не менять», пустая строка — «очистить»
        }
        rest.put("/bookings/" + booking.getId(), booking, Booking.class);
        return true;
    }

    @Override
    public boolean deleteById(Long id) {
        rest.delete("/bookings/" + id);
        return true;
    }

    @Override
    public long count() {
        return rest.get("/bookings/count", Map.of(), new TypeReference<Long>() { });
    }

    @Override
    public List<Booking> searchByClientNickname(String query) {
        return list(Map.of("clientNickname", query));
    }

    @Override
    public List<Booking> searchByStationName(String query) {
        return list(Map.of("stationName", query));
    }

    @Override
    public List<Booking> findByDate(LocalDate date) {
        return list(Map.of("date", date));
    }

    @Override
    public List<Booking> searchByComment(String query) {
        return list(Map.of("comment", query));
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return list(Map.of("status", status.name()));
    }

    @Override
    public List<Booking> findByStationType(StationType type) {
        return list(Map.of("stationType", type.name()));
    }

    @Override
    public List<Booking> findByPeriod(LocalDateTime from, LocalDateTime to) {
        return list(Map.of("from", from, "to", to));
    }

    @Override
    public List<Booking> findByClientId(Long clientId) {
        return list(Map.of("clientId", clientId));
    }

    @Override
    public List<Booking> findByStationId(Long stationId) {
        return list(Map.of("stationId", stationId));
    }

    @Override
    public List<Booking> findOverlapping(Long stationId, LocalDateTime start, LocalDateTime end, Long excludeBookingId) {
        Map<String, Object> params = new HashMap<>();
        params.put("stationId", stationId);
        params.put("start", start);
        params.put("end", end);
        params.put("excludeId", excludeBookingId);
        return rest.get("/bookings/overlapping", params, LIST);
    }

    @Override
    public boolean existsUnfinishedByClientId(Long clientId) {
        return rest.get("/bookings/exists", Map.of("clientId", clientId), new TypeReference<Boolean>() { });
    }

    @Override
    public boolean existsByStationId(Long stationId) {
        return rest.get("/bookings/exists", Map.of("stationId", stationId), new TypeReference<Boolean>() { });
    }

    private List<Booking> list(Map<String, ?> params) {
        return rest.get("/bookings", params, LIST);
    }
}
