package ru.mirea.cyberclub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Точка входа backend киберспортивного клуба.
 * Встроенный пользователь Spring Security со случайным паролем отключён:
 * пользователи хранятся в таблице app_users и входят через JWT (/api/auth/login).
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CyberClubBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(CyberClubBackendApplication.class, args);
    }
}
