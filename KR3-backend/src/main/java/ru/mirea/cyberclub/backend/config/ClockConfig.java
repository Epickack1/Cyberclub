package ru.mirea.cyberclub.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Часы приложения. Время брони хранится как местное время клуба, поэтому "сейчас"
 * тоже берётся в часовом поясе клуба, а не в UTC контейнера.
 */
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(AppProperties properties) {
        return Clock.system(ZoneId.of(properties.timeZone()));
    }
}
