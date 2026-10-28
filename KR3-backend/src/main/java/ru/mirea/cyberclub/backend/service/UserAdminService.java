package ru.mirea.cyberclub.backend.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.dto.UserDtos.CreateUserRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.repository.ClientRepository;
import ru.mirea.cyberclub.backend.repository.RefreshSessionRepository;
import ru.mirea.cyberclub.backend.repository.UserAccountRepository;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.security.PasswordPolicy;

/**
 * Администрирование пользователей: регистрация, роли, отключение, сброс пароля, снятие блокировки.
 * Правило: в системе всегда остаётся хотя бы один включённый администратор, и администратор
 * не может отключить сам себя или снять с себя роль ADMIN.
 * Учётная запись клиента (CLIENT) привязывается к карточке клиента по email; роль CLIENT
 * не совмещается с ролями сотрудников и не назначается и не снимается после регистрации.
 */
@Service
public class UserAdminService {
    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);

    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public UserAdminService(UserAccountRepository users, ClientRepository clients, RefreshSessionRepository sessions,
                            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, Clock clock) {
        this.users = users;
        this.clients = clients;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByEmailAsc().stream()
                .map(user -> UserMapper.toResponse(user, clock.instant()))
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return UserMapper.toResponse(require(id), clock.instant());
    }

    /** Регистрирует пользователя с указанными ролями; для роли CLIENT — с привязкой к карточке клиента. */
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        Client client = request.roles().contains(Role.CLIENT) ? requireClientFor(email, request.roles()) : null;
        if (users.existsByEmail(email)) {
            throw new ConflictException("Пользователь с таким email уже зарегистрирован.");
        }
        if (client != null && users.existsByClientId(client.getId())) {
            throw new ConflictException("У этого клиента уже есть учётная запись.");
        }
        passwordPolicy.validate(request.password(), email, "password");
        UserAccount account = new UserAccount(email, request.displayName(),
                passwordEncoder.encode(request.password()), request.roles());
        if (client != null) {
            account.setClientId(client.getId());
        }
        UserAccount user = users.save(account);
        log.info("Зарегистрирован пользователь userId={} roles={} clientId={}", user.getId(), user.getRoles(),
                user.getClientId());
        return UserMapper.toResponse(user, clock.instant());
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<Role> roles, CurrentUser admin) {
        UserAccount user = require(id);
        if (user.isClient() != roles.contains(Role.CLIENT)) {
            throw new BusinessRuleException("Роль клиента задаётся только при регистрации: клиента нельзя сделать "
                    + "сотрудником, а сотрудника — клиентом. Зарегистрируйте отдельную учётную запись.");
        }
        boolean losesAdmin = user.hasRole(Role.ADMIN) && !roles.contains(Role.ADMIN);
        if (losesAdmin && user.getId().equals(admin.id())) {
            throw new BusinessRuleException("Нельзя снять роль администратора с самого себя.");
        }
        if (losesAdmin && user.isEnabled()) {
            requireAnotherAdmin();
        }
        user.setRoles(roles);
        // Роли проверяются по базе при каждом запросе; refresh-сессии закрываем, чтобы новый вход шёл с новыми ролями.
        sessions.revokeAllForUser(user.getId(), clock.instant());
        return UserMapper.toResponse(user, clock.instant());
    }

    @Transactional
    public UserResponse setEnabled(UUID id, boolean enabled, CurrentUser admin) {
        UserAccount user = require(id);
        if (!enabled && user.getId().equals(admin.id())) {
            throw new BusinessRuleException("Нельзя отключить собственную учётную запись.");
        }
        if (!enabled && user.isEnabled() && user.hasRole(Role.ADMIN)) {
            requireAnotherAdmin();
        }
        user.setEnabled(enabled);
        if (!enabled) {
            sessions.revokeAllForUser(user.getId(), clock.instant());
        }
        return UserMapper.toResponse(user, clock.instant());
    }

    /** Сброс пароля администратором; все сессии пользователя закрываются, блокировка снимается. */
    @Transactional
    public UserResponse resetPassword(UUID id, String password) {
        UserAccount user = require(id);
        passwordPolicy.validate(password, user.getEmail(), "password");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.unlock();
        sessions.revokeAllForUser(user.getId(), clock.instant());
        return UserMapper.toResponse(user, clock.instant());
    }

    @Transactional
    public UserResponse unlock(UUID id) {
        UserAccount user = require(id);
        user.unlock();
        return UserMapper.toResponse(user, clock.instant());
    }

    /** Карточка клиента с тем же email; роль CLIENT должна быть единственной. */
    private Client requireClientFor(String email, Set<Role> roles) {
        if (roles.size() > 1) {
            throw new BadRequestException("roles", "Роль клиента не совмещается с ролями сотрудников.");
        }
        return clients.findByEmailIgnoreCase(email).orElseThrow(() -> new BadRequestException("email",
                "Клиента с таким email нет в базе клуба. Сначала добавьте клиента, затем создайте ему учётную запись."));
    }

    private void requireAnotherAdmin() {
        if (users.countEnabledWithRole(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("Это последний администратор системы — сначала назначьте другого.");
        }
    }

    private UserAccount require(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("Пользователь не найден."));
    }
}
