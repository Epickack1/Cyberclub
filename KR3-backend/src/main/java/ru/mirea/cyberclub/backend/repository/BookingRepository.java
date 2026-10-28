package ru.mirea.cyberclub.backend.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ru.mirea.cyberclub.backend.domain.Booking;
import ru.mirea.cyberclub.backend.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    Optional<Booking> findByNumber(Long number);

    boolean existsByClientId(Long clientId);

    /**
     * Действующие брони этого места, пересекающиеся с интервалом [start, end).
     * Отменённые и архивные место не занимают. {@code excludeId} — бронь, которую сейчас
     * редактируют (для новой брони передаётся 0, такой записи не бывает).
     */
    @Query("""
            select b from Booking b
            where b.station.id = :stationId
              and b.status <> ru.mirea.cyberclub.backend.domain.BookingStatus.CANCELLED
              and b.archived = false
              and b.startTime < :end and b.endTime > :start
              and b.id <> :excludeId
            order by b.startTime""")
    List<Booking> findOverlapping(@Param("stationId") Long stationId,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end,
                                  @Param("excludeId") Long excludeId);

    // --- аналитика (только неархивные брони) ---

    long countByArchived(boolean archived);

    long countByArchivedFalseAndPaid(boolean paid);

    @Query("select avg(b.totalPrice) from Booking b where b.archived = false")
    BigDecimal averagePrice();

    @Query("select coalesce(sum(b.totalPrice), 0) from Booking b where b.archived = false and b.status = :status")
    BigDecimal sumPriceByStatus(@Param("status") BookingStatus status);

    @Query("select b.status, count(b) from Booking b where b.archived = false group by b.status")
    List<Object[]> countByStatus();

    @Query("""
            select s.name, count(b) from Booking b join b.station s
            where b.archived = false group by s.name order by count(b) desc, s.name""")
    List<Object[]> countByStation();

    @Query("""
            select s.type, count(b) from Booking b join b.station s
            where b.archived = false group by s.type""")
    List<Object[]> countByStationType();
}
