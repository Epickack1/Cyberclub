package ru.mirea.cyberclub.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;

/**
 * Хранилище бронирований. Методы поиска и фильтрации выполняются в БД
 * параметризованными SQL-запросами; сортировка — в сервисе средствами Stream API.
 */
public interface BookingRepository extends CrudRepository<Booking, Long> {

    // --- поиск ---

    List<Booking> searchByClientNickname(String query);

    List<Booking> searchByStationName(String query);

    List<Booking> findByDate(LocalDate date);

    List<Booking> searchByComment(String query);

    // --- фильтрация ---

    List<Booking> findByStatus(BookingStatus status);

    List<Booking> findByStationType(StationType type);

    List<Booking> findByPeriod(LocalDateTime from, LocalDateTime to);

    List<Booking> findByClientId(Long clientId);

    List<Booking> findByStationId(Long stationId);

    /**
     * Неотменённые брони указанного места, пересекающиеся с интервалом [start, end).
     *
     * @param excludeBookingId бронь, которую нужно исключить из проверки (при редактировании), может быть null
     */
    List<Booking> findOverlapping(Long stationId, LocalDateTime start, LocalDateTime end, Long excludeBookingId);

    /** Есть ли у клиента незавершённые брони (CREATED, CONFIRMED, ACTIVE). */
    boolean existsUnfinishedByClientId(Long clientId);

    boolean existsByStationId(Long stationId);
}
