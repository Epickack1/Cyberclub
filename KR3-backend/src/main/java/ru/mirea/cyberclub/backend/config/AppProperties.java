package ru.mirea.cyberclub.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры приложения из блока {@code app:} в application.yml.
 *
 * @param permanentDeleteEnabled разрешено ли окончательное удаление броней
 * @param timeZone               часовой пояс клуба: в нём считается "сейчас" для правил бронирования
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(boolean permanentDeleteEnabled, String timeZone) {
    public AppProperties {
        if (timeZone == null || timeZone.isBlank()) {
            timeZone = "Europe/Moscow";
        }
    }
}
