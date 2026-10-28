package ru.mirea.cyberclub.backend.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.UserDtos.CreateUserRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.EnabledRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.PasswordResetRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.RolesRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.service.UserAdminService;

/** Пользователи системы. Доступно только роли ADMIN (правило в SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Пользователи (администратор)", description = "Регистрация сотрудников и управление доступом")
public class AdminUserController {
    private final UserAdminService admin;

    public AdminUserController(UserAdminService admin) {
        this.admin = admin;
    }

    @GetMapping
    @Operation(summary = "Список пользователей")
    public List<UserResponse> list() {
        return admin.list();
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return admin.get(id);
    }

    @PostMapping
    @Operation(summary = "Зарегистрировать пользователя",
            description = "Роли: VIEWER — просмотр, OPERATOR — администратор зала, ADMIN — полный доступ. "
                    + "Пароль: от 8 символов, буквы и цифры.")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.create(request));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "Изменить роли пользователя")
    public UserResponse updateRoles(@PathVariable UUID id, @Valid @RequestBody RolesRequest request,
                                    @AuthenticationPrincipal CurrentUser current) {
        return admin.updateRoles(id, request.roles(), current);
    }

    @PostMapping("/{id}/enabled")
    @Operation(summary = "Включить или отключить учётную запись")
    public UserResponse setEnabled(@PathVariable UUID id, @Valid @RequestBody EnabledRequest request,
                                   @AuthenticationPrincipal CurrentUser current) {
        return admin.setEnabled(id, request.enabled(), current);
    }

    @PostMapping("/{id}/password")
    @Operation(summary = "Сбросить пароль пользователя (его сессии закрываются)")
    public UserResponse resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
        return admin.resetPassword(id, request.password());
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Снять блокировку входа после неудачных попыток")
    public UserResponse unlock(@PathVariable UUID id) {
        return admin.unlock(id);
    }
}
