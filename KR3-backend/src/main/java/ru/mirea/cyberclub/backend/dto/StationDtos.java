package ru.mirea.cyberclub.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.mirea.cyberclub.backend.domain.StationType;

/** DTO игровых мест (справочника). */
public final class StationDtos {
    private StationDtos() {
    }

    public record StationRequest(
            @NotBlank(message = "Название обязательно")
            @Pattern(regexp = "^[A-Z0-9-]{2,20}$", message = "Название: 2-20 символов, латинские заглавные, цифры и дефис (PC-01)")
            String name,
            @NotNull(message = "Тип места обязателен")
            StationType type,
            @NotNull(message = "Тариф обязателен")
            @DecimalMin(value = "0.01", message = "Тариф должен быть больше нуля")
            @DecimalMax(value = "100000", message = "Тариф не больше 100000 руб./час")
            BigDecimal hourlyRate,
            @Size(max = 255, message = "Характеристики не длиннее 255 символов")
            String specs,
            Long version) {
    }

    /** Включение и отключение места без удаления. */
    public record StationActiveRequest(@NotNull(message = "Укажите active") Boolean active) {
    }

    public record StationResponse(
            Long id,
            String name,
            StationType type,
            String typeTitle,
            BigDecimal hourlyRate,
            String specs,
            boolean active,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
