package ru.mirea.cyberclub.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Описание API для Swagger UI (/swagger-ui.html).
 * Схема bearerAuth включает в Swagger кнопку Authorize: в неё вставляется accessToken
 * из ответа POST /api/auth/login, и дальше все запросы из Swagger идут с этим токеном.
 */
@Configuration
public class OpenApiConfig {
    public static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI cyberClubOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CyberClub REST API")
                        .version("0.2.0")
                        .description("""
                                Киберспортивный клуб: клиенты, игровые места, бронирования, аналитика, пользователи.

                                **Как войти:** «Авторизация» → `POST /api/auth/login` → Try it out → email и пароль →
                                скопировать `accessToken` → кнопка **Authorize** вверху → вставить токен → Authorize.

                                Роли: **VIEWER** — просмотр, **OPERATOR** — клиенты и брони,
                                **ADMIN** — всё, включая регистрацию пользователей (`POST /api/admin/users`)."""))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("accessToken из ответа POST /api/auth/login (без слова Bearer)")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
