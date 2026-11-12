package ru.mirea.cyberclub.backend.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO самостоятельной регистрации клиента и его личного кабинета на сайте. */
public final class ClientSelfDtos {
    private ClientSelfDtos() {
    }

    /**
     * Регистрация на сайте: создаются карточка клиента и учётная запись с ролью CLIENT.
     * Поля карточки проверяются по тем же правилам, что и у администратора (КР1).
     */
    public record RegisterRequest(
            @Schema(example = "Gamer_77")
            @NotBlank(message = "Ник обязателен")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "Ник: 3-20 символов, латиница, цифры и _")
            String nickname,
            @Schema(example = "Сидоров Пётр Ильич")
            @NotBlank(message = "ФИО обязательно")
            @Size(max = 100, message = "ФИО не длиннее 100 символов")
            String fullName,
            @Schema(example = "+79991234567")
            @NotBlank(message = "Телефон обязателен")
            @Pattern(regexp = "^\\+7\\d{10}$", message = "Телефон в формате +7XXXXXXXXXX")
            String phone,
            @Schema(example = "petr@mail.ru")
            @NotBlank(message = "Email обязателен")
            @Email(message = "Некорректный email")
            @Size(max = 100, message = "Email не длиннее 100 символов")
            String email,
            @NotNull(message = "Дата рождения обязательна")
            @Past(message = "Дата рождения должна быть в прошлом")
            LocalDate birthDate,
            @Schema(example = "Igra2026night")
            @NotBlank(message = "Укажите пароль") @Size(max = 128)
            String password) {
    }

    /**
     * Бронь из личного кабинета. Клиента сервер берёт из учётной записи, оплата всегда «не оплачено» —
     * её подтверждает администратор после того, как клиент заплатит на стойке.
     */
    public record SelfBookingRequest(
            @NotNull(message = "Игровое место обязательно") Long stationId,
            @NotNull(message = "Время начала обязательно") LocalDateTime startTime,
            @NotNull(message = "Время окончания обязательно") LocalDateTime endTime,
            @Size(max = 255, message = "Комментарий не длиннее 255 символов") String comment) {
    }

    /** Занятый интервал места — без сведений о том, кто его занял. */
    public record BusySlot(LocalDateTime startTime, LocalDateTime endTime) {
    }

    /** Подтверждение оплаты администратором; {@code version} — необязательная защита от гонки. */
    public record PaymentRequest(Long version) {
    }
}
