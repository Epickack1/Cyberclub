package ru.mirea.cyberclub.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.AuthDtos.TokenResponse;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.RegisterRequest;
import ru.mirea.cyberclub.backend.service.AuthService;
import ru.mirea.cyberclub.backend.service.ClientSelfService;

/** Самостоятельная регистрация клиента на сайте. Открыто без входа (правило в SecurityConfig). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Авторизация")
public class RegistrationController {
    private final ClientSelfService self;
    private final AuthService auth;

    public RegistrationController(ClientSelfService self, AuthService auth) {
        this.self = self;
        this.auth = auth;
    }

    /** Регистрация и сразу вход: регистрация фиксируется своей транзакцией, затем обычный вход. */
    @PostMapping("/register")
    @SecurityRequirements
    @Operation(summary = "Регистрация клиента клуба",
            description = "Создаёт карточку клиента и учётную запись CLIENT, возвращает токены, как вход. "
                    + "Пароль: от 8 символов, буквы и цифры, без имени из email.")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        self.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.login(request.email(), request.password()));
    }
}
