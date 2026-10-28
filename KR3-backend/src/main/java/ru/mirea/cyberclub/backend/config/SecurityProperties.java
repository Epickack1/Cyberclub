package ru.mirea.cyberclub.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры авторизации из блока {@code app.security:} в application.yml.
 *
 * @param jwtSecretBase64        ключ подписи JWT в Base64; пустой — сгенерировать при запуске
 * @param jwtIssuer              издатель токена (claim iss)
 * @param accessMinutes          срок жизни access-токена
 * @param refreshDays            срок жизни refresh-сессии
 * @param maxFailedLogins        сколько неудачных попыток входа подряд допускается
 * @param lockMinutes            на сколько минут блокируется вход после превышения
 * @param bootstrapAdminEmail    email первого администратора
 * @param bootstrapAdminName     имя первого администратора
 * @param bootstrapAdminPassword пароль первого администратора (только из переменной окружения)
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecretBase64,
        String jwtIssuer,
        int accessMinutes,
        int refreshDays,
        int maxFailedLogins,
        int lockMinutes,
        String bootstrapAdminEmail,
        String bootstrapAdminName,
        String bootstrapAdminPassword) {

    public SecurityProperties {
        if (jwtIssuer == null || jwtIssuer.isBlank()) jwtIssuer = "cyberclub";
        if (accessMinutes <= 0) accessMinutes = 15;
        if (refreshDays <= 0) refreshDays = 14;
        if (maxFailedLogins <= 0) maxFailedLogins = 5;
        if (lockMinutes <= 0) lockMinutes = 15;
        if (bootstrapAdminName == null || bootstrapAdminName.isBlank()) bootstrapAdminName = "Администратор клуба";
    }

    /** Пароль не попадает в toString() и, значит, в журнал. */
    @Override
    public String toString() {
        return "SecurityProperties{issuer=" + jwtIssuer + ", accessMinutes=" + accessMinutes
                + ", refreshDays=" + refreshDays + ", bootstrapAdminEmail=" + bootstrapAdminEmail + "}";
    }
}
