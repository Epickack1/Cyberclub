package ru.mirea.cyberclub.backend.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Бронирование игрового места — основная сущность системы.
 *
 * <p>Идентификатор — UUID, для людей есть отдельный номер брони ({@code number}),
 * который выдаёт PostgreSQL. Обычное удаление заменено архивированием.</p>
 */
@Entity
@Table(name = "bookings")
public class Booking extends AuditedVersionedEntity {

    @Id
    private UUID id;

    /** Номер брони (#1, #2, ...). Генерирует база, Hibernate читает его после вставки. */
    @Generated
    @Column(insertable = false, updatable = false, unique = true)
    private Long number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CREATED;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false)
    private boolean paid;

    @Column(length = 255)
    private String comment;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Booking() {
    }

    public Booking(UUID id, Client client, Station station) {
        this.id = id;
        this.client = client;
        this.station = station;
    }

    public UUID getId() { return id; }
    public Long getNumber() { return number; }
    public Client getClient() { return client; }
    public void setClient(Client client) { this.client = client; }
    public Station getStation() { return station; }
    public void setStation(Station station) { this.station = station; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public boolean isArchived() { return archived; }
    public Instant getArchivedAt() { return archivedAt; }

    public void archive() {
        this.archived = true;
        this.archivedAt = Instant.now();
    }

    public void restore() {
        this.archived = false;
        this.archivedAt = null;
    }
}
