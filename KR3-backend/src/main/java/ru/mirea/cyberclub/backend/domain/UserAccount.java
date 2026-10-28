package ru.mirea.cyberclub.backend.domain;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Учётная запись сотрудника или клиента клуба. Пароль хранится только как хеш BCrypt;
 * счётчик неудачных входов и время блокировки защищают от перебора паролей.
 * У клиента (роль CLIENT) заполнен clientId — ссылка на его карточку в таблице clients.
 */
@Entity
@Table(name = "app_users")
public class UserAccount extends AuditedVersionedEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "client_id")
    private Long clientId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected UserAccount() {
    }

    public UserAccount(String email, String displayName, String passwordHash, Set<Role> roles) {
        this.id = UUID.randomUUID();
        this.email = normalizeEmail(email);
        this.displayName = displayName.trim();
        this.passwordHash = passwordHash;
        setRoles(roles);
    }

    /** Email хранится в нижнем регистре: так вход не зависит от того, как его набрали. */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Вход заблокирован после серии неудачных попыток. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** Учитывает неудачный вход; после maxAttempts подряд блокирует вход на lockFor. */
    public void registerFailedLogin(Instant now, int maxAttempts, java.time.Duration lockFor) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockFor);
            failedLoginAttempts = 0;
        }
    }

    public void registerSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastLoginAt = now;
    }

    public void unlock() {
        failedLoginAttempts = 0;
        lockedUntil = null;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName.trim(); }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
    public Long getClientId() { return clientId; }
    public void setClientId(Long clientId) { this.clientId = clientId; }

    public boolean isClient() {
        return roles.contains(Role.CLIENT);
    }

    public void setRoles(Set<Role> newRoles) {
        roles.clear();
        roles.addAll(newRoles);
    }
}
