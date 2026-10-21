package ru.mirea.cyberclub.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Поднимает весь Spring Context на H2: ловит ошибки конфигурации, бинов и JPQL-запросов. */
@SpringBootTest
@ActiveProfiles("test")
class ContextSmokeTest {
    @Test
    void contextLoads() {
    }
}
