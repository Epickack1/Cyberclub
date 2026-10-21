package ru.mirea.cyberclub.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.StationType;

/** DTO бронирований. */
public final class BookingDtos {
    private BookingDtos() {
    }

    /**
     * Создание и изменение брони. Стоимость не передаётся — её считает сервер.
     * При изменении клиент не меняется, а {@code version} обязателен.
     */
    public record BookingRequest(
            @NotNull(message = "Клиент обязателен") Long clientId,
            @NotNull(message = "Игровое место обязательно") Long stationId,
            @NotNull(message = "Время начала обязательно") LocalDateTime startTime,
            @NotNull(message = "Время окончания обязательно") LocalDateTime endTime,
            @Size(max = 255, message = "Комментарий не длиннее 255 символов") String comment,
            boolean paid,
            Long version) {
    }

    /** Смена статуса по допустимой схеме переходов. */
    public record StatusRequest(
            @NotNull(message = "Новый статус обязателен") BookingStatus status,
            Long version) {
    }

    public record BookingResponse(
            UUID id,
            Long number,
            Long clientId,
            String clientNickname,
            String clientFullName,
            Long stationId,
            String stationName,
            StationType stationType,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long durationMinutes,
            BookingStatus status,
            String statusTitle,
            BigDecimal totalPrice,
            boolean paid,
            String comment,
            boolean archived,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
