package ru.mirea.cyberclub.backend.config;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.repository.UserAccountRepository;
import ru.mirea.cyberclub.backend.security.PasswordPolicy;

/**
 * Создаёт первого администратора при запуске, если в базе ещё нет ни одного пользователя.
 * Email и пароль берутся из переменных окружения BOOTSTRAP_ADMIN_EMAIL и BOOTSTRAP_ADMIN_PASSWORD
 * (в Docker — из файла .env); в исходном коде пароля нет. Остальных сотрудников регистрирует
 * уже этот администратор через POST /api/admin/users.
 */
@Component
public class BootstrapAdmin implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SecurityProperties properties;

    public BootstrapAdmin(UserAccountRepository users, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy, SecurityProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String email = UserAccount.normalizeEmail(properties.bootstrapAdminEmail());
        String password = properties.bootstrapAdminPassword();
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            log.warn("В базе нет пользователей. Задайте BOOTSTRAP_ADMIN_EMAIL и BOOTSTRAP_ADMIN_PASSWORD "
                    + "и перезапустите сервер — будет создан первый администратор.");
            return;
        }
        try {
            passwordPolicy.validate(password, email, "BOOTSTRAP_ADMIN_PASSWORD");
        } catch (BadRequestException ex) {
            log.error("Первый администратор не создан: BOOTSTRAP_ADMIN_PASSWORD не подходит — {}", ex.getMessage());
            return;
        }
        UserAccount admin = users.save(new UserAccount(email, properties.bootstrapAdminName(),
                passwordEncoder.encode(password), Set.of(Role.ADMIN)));
        log.info("Создан первый администратор {} (userId={})", admin.getEmail(), admin.getId());
    }
}
