package ru.mirea.cyberclub.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;

/**
 * Хранилище бронирований в памяти для модульных тестов. Ник клиента и название
 * места (в JDBC-версии берутся JOIN-ом) подставляются из переданных репозиториев.
 */
public class InMemoryBookingRepository implements BookingRepository {

    private final Map<Long, Booking> storage = new LinkedHashMap<>();
    private final ClientRepository clients;
    private final StationRepository stations;
    private long nextId = 1;

    public InMemoryBookingRepository(ClientRepository clients, StationRepository stations) {
        this.clients = clients;
        this.stations = stations;
    }

    @Override
    public Booking save(Booking booking) {
        booking.setId(nextId++);
        booking.setCreatedAt(LocalDateTime.now());
        fillNames(booking);
        storage.put(booking.getId(), booking);
        return booking;
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Booking> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public boolean update(Booking booking) {
        fillNames(booking);
        return storage.replace(booking.getId(), booking) != null;
    }

    @Override
    public boolean deleteById(Long id) {
        return storage.remove(id) != null;
    }

    @Override
    public long count() {
        return storage.size();
    }

    @Override
    public List<Booking> searchByClientNickname(String query) {
        return filter(b -> b.getClientNickname().toLowerCase().contains(query.toLowerCase()));
    }

    @Override
    public List<Booking> searchByStationName(String query) {
        return filter(b -> b.getStationName().toLowerCase().contains(query.toLowerCase()));
    }

    @Override
    public List<Booking> findByDate(LocalDate date) {
        return filter(b -> b.getStartTime().toLocalDate().equals(date));
    }

    @Override
    public List<Booking> searchByComment(String query) {
        return filter(b -> b.getComment() != null && b.getComment().toLowerCase().contains(query.toLowerCase()));
    }

    @Override
    public List<Booking> findByStatus(BookingStatus status) {
        return filter(b -> b.getStatus() == status);
    }

    @Override
    public List<Booking> findByStationType(StationType type) {
        return filter(b -> stations.findById(b.getStationId()).map(s -> s.getType() == type).orElse(false));
    }

    @Override
    public List<Booking> findByPeriod(LocalDateTime from, LocalDateTime to) {
        return filter(b -> !b.getStartTime().isBefore(from) && !b.getStartTime().isAfter(to));
    }

    @Override
    public List<Booking> findByClientId(Long clientId) {
        return filter(b -> b.getClientId().equals(clientId));
    }

    @Override
    public List<Booking> findByStationId(Long stationId) {
        return filter(b -> b.getStationId().equals(stationId));
    }

    @Override
    public List<Booking> findOverlapping(Long stationId, LocalDateTime start, LocalDateTime end, Long excludeBookingId) {
        return filter(b -> b.getStationId().equals(stationId)
                && b.getStatus().isOccupying()
                && !b.getId().equals(excludeBookingId)
                && b.overlaps(start, end));
    }

    @Override
    public boolean existsUnfinishedByClientId(Long clientId) {
        return storage.values().stream().anyMatch(b -> b.getClientId().equals(clientId) && b.getStatus().isUnfinished());
    }

    @Override
    public boolean existsByStationId(Long stationId) {
        return storage.values().stream().anyMatch(b -> b.getStationId().equals(stationId));
    }

    private List<Booking> filter(Predicate<Booking> predicate) {
        return storage.values().stream().filter(predicate).toList();
    }

    private void fillNames(Booking booking) {
        clients.findById(booking.getClientId()).ifPresent(c -> booking.setClientNickname(c.getNickname()));
        stations.findById(booking.getStationId()).ifPresent(s -> booking.setStationName(s.getName()));
    }
}
