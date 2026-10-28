package ru.mirea.cyberclub.backend.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.mirea.cyberclub.backend.domain.Role;

/** Модели администрирования пользователей. Хеш пароля наружу не отдаётся никогда. */
public final class UserDtos {
    private UserDtos() {
    }

    /**
     * Регистрация пользователя администратором. Для роли CLIENT email должен совпадать
     * с email карточки клиента — по нему учётная запись привязывается к клиенту.
     */
    public record CreateUserRequest(
            @Schema(example = "operator@cyberclub.local")
            @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 100)
            String email,
            @Schema(example = "Иван Петров")
            @NotBlank(message = "Укажите имя") @Size(min = 2, max = 100, message = "Имя: от 2 до 100 символов")
            String displayName,
            @Schema(example = "Club2026pass")
            @NotBlank(message = "Укажите пароль") @Size(max = 128)
            String password,
            @Schema(example = "[\"OPERATOR\"]")
            @NotEmpty(message = "Выберите хотя бы одну роль")
            Set<@NotNull Role> roles) {
    }

    public record RolesRequest(
            @NotEmpty(message = "Выберите хотя бы одну роль")
            Set<@NotNull Role> roles) {
    }

    public record EnabledRequest(
            @NotNull(message = "Укажите enabled")
            Boolean enabled) {
    }

    public record PasswordResetRequest(
            @NotBlank(message = "Укажите новый пароль") @Size(max = 128)
            String password) {
    }

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            Set<Role> roles,
            @Schema(description = "Карточка клиента (только у роли CLIENT)")
            Long clientId,
            boolean enabled,
            boolean locked,
            Instant lockedUntil,
            Instant lastLoginAt,
            Instant createdAt) {
    }
}
