package ru.mirea.cyberclub.mcp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * Куда уходят запросы MCP-инструментов: в базу напрямую или в REST API КР3.
 *
 * <p>Инструменты в {@link CyberclubTools} описаны один раз поверх этого порта, а способ
 * добраться до данных — отдельная забота {@link JdbcClubApi} (PostgreSQL, как в КР2) или
 * {@code ru.mirea.cyberclub.mcp.rest.RestClubApi} (HTTP к {@code KR3-backend}). Благодаря
 * этому модель и правила (переходы статусов, возраст клиента) продолжают решаться там же,
 * где и раньше, — меняется только транспорт.</p>
 *
 * <p>Методы возвращают доменные модели КР2: REST-ответы в них перекладывает адаптер.
 * Исключение — {@link #statisticsText()}, потому что сводка КР3 считается иначе, чем в КР2,
 * и подгонять её под модель {@code Statistics} значило бы врать в одном из режимов.</p>
 */
public interface ClubApi {

    /** Краткое описание источника данных — попадает в журнал запуска MCP-сервера. */
    String describe();

    // ------------------------------------------------------------ брони

    List<Booking> allBookings();

    List<Booking> bookingsByStatus(BookingStatus status);

    List<Booking> bookingsByDate(LocalDate date);

    List<Booking> bookingsByPeriod(LocalDateTime from, LocalDateTime to);

    List<Booking> bookingsByClient(long clientId);

    /** Поиск по нику клиента, названию стационара или комментарию. */
    List<Booking> searchBookings(String query);

    Booking booking(long id);

    Booking createBooking(long clientId, long stationId, LocalDateTime start, LocalDateTime end, String comment);

    Booking changeBookingStatus(long id, BookingStatus status);

    /**
     * Перенос брони на другое время и/или место. Клиент не меняется — так же, как в КР3.
     * Нужен ассистенту, чтобы разрешать конфликты занятости, не создавая дубль.
     */
    Booking rescheduleBooking(long id, long stationId, LocalDateTime start, LocalDateTime end, String comment);

    // ------------------------------------------------------------ клиенты

    List<Client> allClients();

    List<Client> searchClients(String query);

    Client client(long id);

    Client createClient(Client client);

    /** Изменение карточки: {@code client} уже содержит идентификатор и новые значения полей. */
    Client updateClient(Client client);

    void deleteClient(long id);

    // ------------------------------------------------------------ стационары

    List<Station> allStations();

    List<Station> stationsByType(StationType type);

    List<Station> activeStations();

    // ------------------------------------------------------------ статистика

    /**
     * Сводка клуба готовым текстом. Формат зависит от источника: в КР2 она собирается
     * через {@code StatisticsService}, в КР3 — из {@code /api/analytics/summary}.
     */
    String statisticsText();
}
