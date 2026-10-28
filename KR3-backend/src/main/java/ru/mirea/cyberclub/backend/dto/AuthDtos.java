package ru.mirea.cyberclub.backend.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;

/** Входные и выходные модели авторизации. */
public final class AuthDtos {
    private AuthDtos() {
    }

    public record LoginRequest(
            @Schema(example = "admin@cyberclub.local")
            @NotBlank(message = "Укажите email") @Email(message = "Некорректный email") @Size(max = 100)
            String email,
            @NotBlank(message = "Укажите пароль") @Size(max = 128)
            String password) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Укажите refreshToken") @Size(max = 200)
            String refreshToken) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Укажите текущий пароль") @Size(max = 128)
            String currentPassword,
            @NotBlank(message = "Укажите новый пароль") @Size(max = 128)
            String newPassword) {
    }

    /**
     * Ответ на вход и обновление токена.
     *
     * @param accessToken      JWT для заголовка {@code Authorization: Bearer ...} (в Swagger — кнопка Authorize)
     * @param refreshToken     одноразовый токен для {@code POST /api/auth/refresh}
     */
    public record TokenResponse(
            String accessToken,
            String tokenType,
            Instant accessExpiresAt,
            String refreshToken,
            Instant refreshExpiresAt,
            UserResponse user) {
    }
}
