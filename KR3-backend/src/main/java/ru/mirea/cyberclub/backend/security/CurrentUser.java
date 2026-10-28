package ru.mirea.cyberclub.backend.security;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.domain.UserAccount;

/**
 * Вошедший пользователь в SecurityContext (без хеша пароля).
 * В контроллер попадает через {@code @AuthenticationPrincipal CurrentUser user}.
 */
public record CurrentUser(UUID id, String email, String displayName, Set<Role> roles) {

    public static CurrentUser from(UserAccount user) {
        return new CurrentUser(user.getId(), user.getEmail(), user.getDisplayName(), user.getRoles());
    }

    /** Роли в формате Spring Security: ROLE_ADMIN, ROLE_OPERATOR, ROLE_VIEWER. */
    public Collection<? extends GrantedAuthority> authorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
}
