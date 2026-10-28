package ru.mirea.cyberclub.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.AuthDtos.ChangePasswordRequest;
import ru.mirea.cyberclub.backend.dto.AuthDtos.LoginRequest;
import ru.mirea.cyberclub.backend.dto.AuthDtos.RefreshRequest;
import ru.mirea.cyberclub.backend.dto.AuthDtos.TokenResponse;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.service.AuthService;

/** Вход, обновление токена, выход и данные о себе. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Авторизация", description = "Войдите через login, затем нажмите Authorize и вставьте accessToken")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Вход по email и паролю",
            description = "Возвращает accessToken (для кнопки Authorize) и refreshToken. "
                    + "После нескольких неудачных попыток подряд вход временно блокируется (423).")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Новая пара токенов по refreshToken (старый refreshToken перестаёт действовать)")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Выход: закрыть refresh-сессию")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        auth.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Кто я: email, имя и роли вошедшего пользователя")
    public UserResponse me(@AuthenticationPrincipal CurrentUser user) {
        return auth.me(user);
    }

    @PostMapping("/password")
    @Operation(summary = "Сменить свой пароль (остальные сессии закрываются)")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal CurrentUser user,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        auth.changePassword(user, request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
