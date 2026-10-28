package ru.mirea.cyberclub.auth;

import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

/**
 * Пользователь системы в том виде, в каком его отдаёт сервер КР3 (без пароля и хеша).
 */
public record UserInfo(
        UUID id,
        String email,
        String displayName,
        Set<Role> roles,
        Long clientId,
        boolean enabled,
        boolean locked,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt) {

    public UserInfo {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Учётная запись клиента клуба (для сайта), а не сотрудника. */
    public boolean isClient() {
        return roles.contains(Role.CLIENT);
    }

    /** Главная роль для отображения: ADMIN важнее OPERATOR, OPERATOR важнее VIEWER. */
    public Role mainRole() {
        return roles.stream().max(Comparator.naturalOrder()).orElse(Role.VIEWER);
    }

    /** Состояние учётной записи для таблицы пользователей. */
    public String state() {
        if (!enabled) {
            return "Отключён";
        }
        return locked ? "Вход заблокирован" : "Активен";
    }
}
