package ru.mirea.cyberclub.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Описание API для Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI cyberClubOpenApi() {
        return new OpenAPI().info(new Info()
                .title("CyberClub REST API")
                .version("0.1.0")
                .description("Киберспортивный клуб: клиенты, игровые места, бронирования, аналитика."));
    }
}
