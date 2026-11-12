package ru.mirea.cyberclub.backend.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.dto.UserDtos.CreateUserRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.TemporaryPasswordResponse;
import ru.mirea.cyberclub.backend.dto.UserDtos.UpdateUserRequest;
import ru.mirea.cyberclub.backend.dto.UserDtos.UserResponse;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.filter.BookingSpecifications;
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
 * Пароль, выданный администратором (при регистрации или сбросе), временный: до его смены
 * пользователь может только войти и сменить пароль. Все изменения пишутся в журнал аудита.
 */
@Service
public class UserAdminService {
    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";

    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;
    private final Clock clock;

    public UserAdminService(UserAccountRepository users, ClientRepository clients, RefreshSessionRepository sessions,
                            PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy, AuditService audit,
                            Clock clock) {
        this.users = users;
        this.clients = clients;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return users.findAllByOrderByEmailAsc().stream()
                .map(user -> UserMapper.toResponse(user, clock.instant()))
                .toList();
    }

    /** Страница пользователей с поиском по email и имени. */
    @Transactional(readOnly = true)
    public Page<UserResponse> page(String q, Pageable pageable) {
        Page<UserAccount> found = q == null || q.isBlank()
                ? users.findAll(pageable)
                : users.search("%" + BookingSpecifications.escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%", pageable);
        return found.map(user -> UserMapper.toResponse(user, clock.instant()));
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
        account.setMustChangePassword(true);
        // saveAndFlush: версия в ответе должна совпадать с базой (запись ролей при вставке увеличивает version)
        UserAccount user = users.saveAndFlush(account);
        log.info("Зарегистрирован пользователь userId={} roles={} clientId={}", user.getId(), user.getRoles(),
                user.getClientId());
        audit.record("USER_CREATED", AuditService.USER, user.getId(), "Зарегистрирован " + user.getEmail()
                + " (" + rolesText(user.getRoles()) + "), пароль временный");
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
        audit.record("USER_ROLES", AuditService.USER, id, user.getEmail() + ": роли " + rolesText(roles));
        return UserMapper.toResponse(user, clock.instant());
    }

    /**
     * Изменение логина (email), имени, ролей и активности одним запросом — с теми же правилами,
     * что у отдельных операций: нельзя снять с себя ADMIN или отключить себя, нельзя оставить систему
     * без администратора, роль клиента не меняется, логин клиента совпадает с email его карточки.
     */
    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, CurrentUser admin) {
        UserAccount user = require(id);
        Versions.check(request.version(), user.getVersion());
        Set<Role> roles = request.roles();
        if (user.isClient() != roles.contains(Role.CLIENT)) {
            throw new BusinessRuleException("Роль клиента задаётся только при регистрации: клиента нельзя сделать "
                    + "сотрудником, а сотрудника — клиентом. Зарегистрируйте отдельную учётную запись.");
        }
        String email = UserAccount.normalizeEmail(request.email());
        boolean emailChanged = !email.equals(user.getEmail());
        if (emailChanged && users.existsByEmail(email)) {
            throw new ConflictException("Пользователь с таким email уже зарегистрирован.");
        }
        if (emailChanged && user.isClient()) {
            throw new BusinessRuleException("Логин клиента — email его карточки: измените email в карточке клиента "
                    + "и зарегистрируйте учётную запись заново.");
        }
        boolean self = user.getId().equals(admin.id());
        boolean losesAdmin = user.hasRole(Role.ADMIN) && !roles.contains(Role.ADMIN);
        boolean disabling = user.isEnabled() && !request.enabled();
        if (self && (losesAdmin || disabling)) {
            throw new BusinessRuleException("Нельзя снять роль администратора с самого себя или отключить собственную учётную запись.");
        }
        if (user.isEnabled() && user.hasRole(Role.ADMIN) && (losesAdmin || disabling)) {
            requireAnotherAdmin();
        }
        boolean accessChanged = emailChanged || !roles.equals(user.getRoles()) || disabling;
        user.setEmail(email);
        user.setDisplayName(request.displayName());
        user.setRoles(roles);
        user.setEnabled(request.enabled());
        if (accessChanged) {
            sessions.revokeAllForUser(user.getId(), clock.instant());
        }
        UserAccount saved = users.saveAndFlush(user);
        audit.record("USER_UPDATED", AuditService.USER, id, saved.getEmail() + ": имя \"" + saved.getDisplayName()
                + "\", роли " + rolesText(roles) + (saved.isEnabled() ? ", активен" : ", отключён"));
        return UserMapper.toResponse(saved, clock.instant());
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
        audit.record(enabled ? "USER_ENABLED" : "USER_DISABLED", AuditService.USER, id,
                user.getEmail() + (enabled ? ": учётная запись включена" : ": учётная запись отключена"));
        return UserMapper.toResponse(user, clock.instant());
    }

    /**
     * Сброс пароля на заданный администратором; пароль временный — при входе его нужно сменить.
     * Все сессии пользователя закрываются, блокировка снимается.
     */
    @Transactional
    public UserResponse resetPassword(UUID id, String password) {
        UserAccount user = require(id);
        passwordPolicy.validate(password, user.getEmail(), "password");
        setTemporaryPassword(user, password);
        return UserMapper.toResponse(user, clock.instant());
    }

    /** Сброс пароля со случайным временным паролем, который сервер возвращает один раз. */
    @Transactional
    public TemporaryPasswordResponse resetToTemporaryPassword(UUID id) {
        UserAccount user = require(id);
        String password = temporaryPassword(user.getEmail());
        setTemporaryPassword(user, password);
        return new TemporaryPasswordResponse(UserMapper.toResponse(user, clock.instant()), password);
    }

    private void setTemporaryPassword(UserAccount user, String password) {
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setMustChangePassword(true);
        user.unlock();
        sessions.revokeAllForUser(user.getId(), clock.instant());
        audit.record("USER_PASSWORD_RESET", AuditService.USER, user.getId(),
                user.getEmail() + ": пароль сброшен на временный, сессии закрыты");
    }

    /** 12 символов: буквы и цифры без похожих 0/O и 1/l; всегда проходит политику паролей. */
    String temporaryPassword(String email) {
        while (true) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 9; i++) {
                sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
            }
            for (int i = 0; i < 3; i++) {
                sb.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
            }
            String password = sb.toString();
            try {
                passwordPolicy.validate(password, email, "password");
                return password;
            } catch (BadRequestException ex) {
                // почти невозможно (имя из email внутри случайной строки) — генерируем заново
            }
        }
    }

    @Transactional
    public UserResponse unlock(UUID id) {
        UserAccount user = require(id);
        user.unlock();
        audit.record("USER_UNLOCKED", AuditService.USER, id, user.getEmail() + ": блокировка входа снята");
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

    private static String rolesText(Set<Role> roles) {
        return roles.stream().map(Role::name).sorted().reduce((a, b) -> a + ", " + b).orElse("-");
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
