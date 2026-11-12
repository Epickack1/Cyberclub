package ru.mirea.cyberclub.backend.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.PageResponse;
import ru.mirea.cyberclub.backend.dto.UserDtos.CreateUserRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.EnabledRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.PasswordResetRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.RolesRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.TemporaryPasswordResponse;
import ru.mirea.cyberclub.backend.dto.UserDtos.UpdateUserRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.service.UserAdminService;
import ru.mirea.cyberclub.backend.web.PageableFactory;

/** Пользователи системы. Доступно только роли ADMIN (правило в SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Пользователи (администратор)", description = "Регистрация сотрудников и управление доступом")
public class AdminUserController {
    private final UserAdminService admin;
    private final PageableFactory pageableFactory;

    public AdminUserController(UserAdminService admin, PageableFactory pageableFactory) {
        this.admin = admin;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @Operation(summary = "Список пользователей с поиском и пагинацией",
            description = "q — подстрока email или имени; sort: email|name|created|lastLogin, через запятую asc|desc.")
    public PageResponse<UserResponse> list(@RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "25") int size,
                                           @RequestParam(defaultValue = "email,asc") String sort) {
        return PageResponse.from(admin.page(q, pageableFactory.users(page, size, sort)));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable UUID id) {
        return admin.get(id);
    }

    @PostMapping
    @Operation(summary = "Зарегистрировать пользователя",
            description = "Роли: VIEWER — просмотр, OPERATOR — администратор зала, ADMIN — полный доступ, "
                    + "CLIENT — клиент клуба (email = email карточки клиента). Пароль временный: "
                    + "от 8 символов, буквы и цифры; при первом входе его нужно сменить.")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(admin.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить логин, имя, роли и активность",
            description = "version — из последнего ответа сервера. При смене логина, ролей или отключении "
                    + "все сессии пользователя закрываются.")
    public UserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request,
                               @AuthenticationPrincipal CurrentUser current) {
        return admin.update(id, request, current);
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

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Сбросить пароль: сервер создаёт временный пароль и возвращает его один раз",
            description = "Сессии пользователя закрываются, блокировка снимается; при входе пароль нужно сменить.")
    public TemporaryPasswordResponse resetToTemporary(@PathVariable UUID id) {
        return admin.resetToTemporaryPassword(id);
    }

    @PostMapping("/{id}/password")
    @Operation(summary = "Сбросить пароль на заданный администратором (тоже временный)")
    public UserResponse resetPassword(@PathVariable UUID id, @Valid @RequestBody PasswordResetRequest request) {
        return admin.resetPassword(id, request.password());
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "Снять блокировку входа после неудачных попыток")
    public UserResponse unlock(@PathVariable UUID id) {
        return admin.unlock(id);
    }
}
