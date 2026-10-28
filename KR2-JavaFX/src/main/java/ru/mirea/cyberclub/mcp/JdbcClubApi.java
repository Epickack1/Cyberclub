package ru.mirea.cyberclub.mcp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.mirea.cyberclub.AppServices;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * {@link ClubApi} поверх сервисов КР2 — прямое обращение к PostgreSQL, как было изначально.
 * Это режим по умолчанию: MCP работает сам по себе, REST API не нужен.
 */
public final class JdbcClubApi implements ClubApi {

    private final AppServices services;

    public JdbcClubApi(AppServices services) {
        this.services = services;
    }

    @Override
    public String describe() {
        return "прямое подключение к БД (КР2)";
    }

    @Override
    public List<Booking> allBookings() {
        return services.bookings().getAll();
    }

    @Override
    public List<Booking> bookingsByStatus(BookingStatus status) {
        return services.bookings().filterByStatus(status);
    }

    @Override
    public List<Booking> bookingsByDate(LocalDate date) {
        return services.bookings().searchByDate(date);
    }

    @Override
    public List<Booking> bookingsByPeriod(LocalDateTime from, LocalDateTime to) {
        return services.bookings().filterByPeriod(from, to);
    }

    @Override
    public List<Booking> bookingsByClient(long clientId) {
        return services.bookings().filterByClient(clientId);
    }

    @Override
    public List<Booking> searchBookings(String query) {
        List<Booking> found = new ArrayList<>();
        found.addAll(services.bookings().searchByClientNickname(query));
        found.addAll(services.bookings().searchByStationName(query));
        found.addAll(services.bookings().searchByComment(query));
        return distinct(found);
    }

    @Override
    public Booking booking(long id) {
        return services.bookings().getById(id);
    }

    @Override
    public Booking createBooking(long clientId, long stationId, LocalDateTime start, LocalDateTime end,
                                 String comment) {
        return services.bookings().create(clientId, stationId, start, end, comment);
    }

    @Override
    public Booking changeBookingStatus(long id, BookingStatus status) {
        return services.bookings().changeStatus(id, status);
    }

    @Override
    public Booking rescheduleBooking(long id, long stationId, LocalDateTime start, LocalDateTime end,
                                     String comment) {
        return services.bookings().update(id, stationId, start, end, comment);
    }

    @Override
    public List<Client> allClients() {
        return services.clients().getAll();
    }

    @Override
    public List<Client> searchClients(String query) {
        return services.clients().search(query);
    }

    @Override
    public Client client(long id) {
        return services.clients().getById(id);
    }

    @Override
    public Client createClient(Client client) {
        return services.clients().create(client);
    }

    @Override
    public Client updateClient(Client client) {
        return services.clients().update(client);
    }

    @Override
    public void deleteClient(long id) {
        services.clients().delete(id);
    }

    @Override
    public List<Station> allStations() {
        return services.stations().getAll();
    }

    @Override
    public List<Station> stationsByType(StationType type) {
        return services.stations().getByType(type);
    }

    @Override
    public List<Station> activeStations() {
        return services.stations().getActive();
    }

    @Override
    public String statisticsText() {
        var stats = services.statistics().collect();
        StringBuilder out = new StringBuilder("Статистика клуба:\n");
        for (var ind : services.statistics().toIndicators(stats)) {
            out.append("  - ").append(ind.name()).append(": ").append(ind.value()).append('\n');
        }
        return out.toString().stripTrailing();
    }

    /** Поиск по трём полям даёт дубли — одна бронь совпадает и по нику, и по комментарию. */
    private static List<Booking> distinct(List<Booking> source) {
        Map<Long, Booking> byId = new LinkedHashMap<>();
        for (Booking b : source) {
            if (b.getId() != null) {
                byId.putIfAbsent(b.getId(), b);
            }
        }
        return new ArrayList<>(byId.values());
    }
}
