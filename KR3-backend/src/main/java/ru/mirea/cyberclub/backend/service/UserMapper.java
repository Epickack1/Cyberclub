package ru.mirea.cyberclub.backend.service;

import java.time.Instant;

import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;

/** Преобразует учётную запись в ответ API без хеша пароля. */
final class UserMapper {
    private UserMapper() {
    }

    static UserResponse toResponse(UserAccount user, Instant now) {
        boolean locked = user.isLocked(now);
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRoles(),
                user.getClientId(), user.isEnabled(), locked, locked ? user.getLockedUntil() : null, user.getLastLoginAt(),
                user.getCreatedAt());
    }
}
