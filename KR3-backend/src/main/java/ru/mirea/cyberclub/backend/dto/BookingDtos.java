package ru.mirea.cyberclub.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.RiskLevel;
import ru.mirea.cyberclub.backend.domain.StationType;

/** DTO бронирований. */
public final class BookingDtos {
    private BookingDtos() {
    }

    /**
     * Создание и изменение брони. Стоимость и риск неоплаты не передаются — их считает сервер.
     * При изменении клиент не меняется, а {@code version} обязателен.
     * {@code tags}: при создании {@code null} — без тегов, при изменении {@code null} — теги не меняются.
     */
    public record BookingRequest(
            @NotNull(message = "Клиент обязателен") Long clientId,
            @NotNull(message = "Игровое место обязательно") Long stationId,
            @NotNull(message = "Время начала обязательно") LocalDateTime startTime,
            @NotNull(message = "Время окончания обязательно") LocalDateTime endTime,
            @Size(max = 255, message = "Комментарий не длиннее 255 символов") String comment,
            boolean paid,
            @Schema(example = "[\"турнир\", \"постоянный\"]")
            @Size(max = 10, message = "Не больше 10 тегов") Set<String> tags,
            Long version) {

        public BookingRequest(Long clientId, Long stationId, LocalDateTime startTime, LocalDateTime endTime,
                              String comment, boolean paid, Long version) {
            this(clientId, stationId, startTime, endTime, comment, paid, null, version);
        }
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
            @Schema(description = "Риск неоплаты — вычисляется сервером")
            RiskLevel riskLevel,
            String riskTitle,
            List<String> tags,
            String comment,
            boolean archived,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }

    /** Действия массовой операции над набором броней. */
    public enum BulkAction { ARCHIVE, RESTORE, SET_STATUS, ADD_TAG, REMOVE_TAG, MARK_PAID }

    /**
     * Массовая операция: {@code status} нужен для SET_STATUS, {@code tag} — для ADD_TAG и REMOVE_TAG.
     * Каждая бронь обрабатывается отдельно: ошибка одной не отменяет остальные.
     */
    public record BulkRequest(
            @NotNull(message = "Укажите брони")
            @Size(min = 1, max = 500, message = "От 1 до 500 броней за раз") Set<@NotNull UUID> ids,
            @NotNull(message = "Укажите действие") BulkAction action,
            BookingStatus status,
            @Size(max = 30, message = "Тег не длиннее 30 символов") String tag) {
    }

    public record BulkFailure(UUID id, String message) {
    }

    public record BulkResult(BulkAction action, int requested, int succeeded, List<UUID> succeededIds,
                             List<BulkFailure> failures) {
    }
}
