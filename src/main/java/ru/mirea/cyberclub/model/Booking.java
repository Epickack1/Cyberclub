package ru.mirea.cyberclub.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Бронирование игрового места — основная сущность системы.
 * Ссылается на клиента и игровое место по идентификаторам (внешние ключи в БД).
 */
public class Booking extends BaseEntity {

    private Long clientId;
    private Long stationId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BookingStatus status = BookingStatus.CREATED;
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private String comment;
    private LocalDateTime createdAt;

    // Поля только для отображения: заполняются репозиторием через JOIN, в таблице bookings их нет
    private String clientNickname;
    private String stationName;

    public Booking() {
    }

    public Booking(Long clientId, Long stationId, LocalDateTime startTime, LocalDateTime endTime, String comment) {
        this.clientId = clientId;
        this.stationId = stationId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.comment = comment;
    }

    /** Длительность брони. */
    public Duration getDuration() {
        if (startTime == null || endTime == null) {
            return Duration.ZERO;
        }
        return Duration.between(startTime, endTime);
    }

    /** Пересекается ли интервал брони с указанным (интервалы полуоткрытые [start, end)). */
    public boolean overlaps(LocalDateTime otherStart, LocalDateTime otherEnd) {
        return startTime.isBefore(otherEnd) && endTime.isAfter(otherStart);
    }

    public Long getClientId() {
        return clientId;
    }

    public void setClientId(Long clientId) {
        this.clientId = clientId;
    }

    public Long getStationId() {
        return stationId;
    }

    public void setStationId(Long stationId) {
        this.stationId = stationId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getClientNickname() {
        return clientNickname;
    }

    public void setClientNickname(String clientNickname) {
        this.clientNickname = clientNickname;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    @Override
    public String describe() {
        return "Бронирование #" + getId() + " (" + clientNickname + ", " + stationName + ", " + status.getTitle() + ")";
    }

    @Override
    public String toString() {
        return "Booking{id=" + getId() + ", clientId=" + clientId + ", stationId=" + stationId
                + ", start=" + startTime + ", end=" + endTime + ", status=" + status
                + ", totalPrice=" + totalPrice + "}";
    }
}
