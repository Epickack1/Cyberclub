package ru.mirea.cyberclub.backend.dto;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTO клиентов. JPA-сущность наружу не отдаётся. */
public final class ClientDtos {
    private ClientDtos() {
    }

    /** Создание и изменение клиента. {@code version} обязателен при изменении. */
    public record ClientRequest(
            @NotBlank(message = "Ник обязателен")
            @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "Ник: 3-20 символов, латиница, цифры и _")
            String nickname,
            @NotBlank(message = "ФИО обязательно")
            @Size(max = 100, message = "ФИО не длиннее 100 символов")
            String fullName,
            @NotBlank(message = "Телефон обязателен")
            @Pattern(regexp = "^\\+7\\d{10}$", message = "Телефон в формате +7XXXXXXXXXX")
            String phone,
            @NotBlank(message = "Email обязателен")
            @Email(message = "Некорректный email")
            @Size(max = 100, message = "Email не длиннее 100 символов")
            String email,
            @NotNull(message = "Дата рождения обязательна")
            @Past(message = "Дата рождения должна быть в прошлом")
            LocalDate birthDate,
            Long version) {
    }

    public record ClientResponse(
            Long id,
            String nickname,
            String fullName,
            String phone,
            String email,
            LocalDate birthDate,
            int age,
            Instant createdAt,
            Instant updatedAt,
            long version) {
    }
}
