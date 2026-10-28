package ru.mirea.cyberclub.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Сквозная проверка авторизации на H2: первый администратор, регистрация пользователей,
 * права ролей, обновление и отзыв токенов, блокировка после перебора паролей, Swagger.
 */
@SpringBootTest(properties = {
        "app.security.bootstrap-admin-email=Admin@CyberClub.local",
        "app.security.bootstrap-admin-password=Club2026root",
        "app.security.max-failed-logins=3",
        "app.security.lock-minutes=15"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowTest {
    private static final String ADMIN_EMAIL = "admin@cyberclub.local";
    private static final String ADMIN_PASSWORD = "Club2026root";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Test
    void anonymousRequestGets401InApiErrorFormat() throws Exception {
        mvc.perform(get("/api/clients"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/clients"));
    }

    @Test
    void swaggerIsOpenAndHasAuthorizeScheme() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/admin/users'].post").exists());
    }

    @Test
    void bootstrapAdminLogsInCaseInsensitivelyAndSeesItself() throws Exception {
        JsonNode tokens = login("ADMIN@cyberclub.local", ADMIN_PASSWORD);
        assertThat(tokens.path("tokenType").asText()).isEqualTo("Bearer");
        assertThat(tokens.path("refreshToken").asText()).isNotBlank();

        mvc.perform(auth(get("/api/auth/me"), tokens))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void adminRegistersUsersAndRolesAreEnforced() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        createUser(admin, "viewer1@cyberclub.local", "Club2026view", "VIEWER").andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.enabled").value(true));
        createUser(admin, "Viewer1@CyberClub.local", "Club2026view", "VIEWER").andExpect(status().isConflict());
        createUser(admin, "weak@cyberclub.local", "short1", "VIEWER")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());
        createUser(admin, "operator1@cyberclub.local", "Club2026oper", "OPERATOR").andExpect(status().isCreated());

        JsonNode viewer = login("viewer1@cyberclub.local", "Club2026view");
        mvc.perform(auth(get("/api/clients"), viewer)).andExpect(status().isOk());
        mvc.perform(auth(post("/api/clients"), viewer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(auth(get("/api/admin/users"), viewer)).andExpect(status().isForbidden());
        createUser(viewer, "hacker@cyberclub.local", "Club2026hack", "ADMIN").andExpect(status().isForbidden());

        JsonNode operator = login("operator1@cyberclub.local", "Club2026oper");
        // OPERATOR проходит проверку прав на изменение клиентов (дальше уже валидация полей — 400, а не 403)
        mvc.perform(auth(post("/api/clients"), operator).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        // а справочник мест меняет только ADMIN
        mvc.perform(auth(post("/api/stations"), operator).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        mvc.perform(auth(get("/api/admin/users"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'viewer1@cyberclub.local')]").exists());
    }

    @Test
    void refreshTokenRotatesAndReuseRevokesAllSessions() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        createUser(admin, "rotate@cyberclub.local", "Club2026rot", "VIEWER").andExpect(status().isCreated());
        JsonNode first = login("rotate@cyberclub.local", "Club2026rot");

        JsonNode second = refresh(first.path("refreshToken").asText()).andExpect(status().isOk()).andReturnJson();
        assertThat(second.path("refreshToken").asText()).isNotEqualTo(first.path("refreshToken").asText());

        // старый refresh-токен второй раз — похоже на кражу: отказ и отзыв всех сессий
        refresh(first.path("refreshToken").asText()).andExpect(status().isUnauthorized());
        refresh(second.path("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesRefreshSession() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        createUser(admin, "logout@cyberclub.local", "Club2026out", "VIEWER").andExpect(status().isCreated());
        JsonNode tokens = login("logout@cyberclub.local", "Club2026out");

        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.path("refreshToken").asText() + "\"}"))
                .andExpect(status().isNoContent());
        refresh(tokens.path("refreshToken").asText()).andExpect(status().isUnauthorized());
    }

    @Test
    void bruteForceLocksAccountAndAdminUnlocks() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String id = createUser(admin, "brute@cyberclub.local", "Club2026lock", "VIEWER")
                .andExpect(status().isCreated()).andReturnJson().path("id").asText();

        loginRequest("brute@cyberclub.local", "wrong-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверный email или пароль."));
        loginRequest("brute@cyberclub.local", "wrong-2").andExpect(status().isUnauthorized());
        loginRequest("brute@cyberclub.local", "wrong-3").andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        // даже правильный пароль не пускает, пока действует блокировка
        loginRequest("brute@cyberclub.local", "Club2026lock").andExpect(status().isLocked());

        mvc.perform(auth(post("/api/admin/users/" + id + "/unlock"), admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
        loginRequest("brute@cyberclub.local", "Club2026lock").andExpect(status().isOk());
    }

    @Test
    void unknownEmailGetsSameMessageAsWrongPassword() throws Exception {
        loginRequest("nobody@cyberclub.local", "Whatever2026").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверный email или пароль."));
    }

    @Test
    void disabledUserLosesAccessImmediately() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String id = createUser(admin, "fired@cyberclub.local", "Club2026fire", "OPERATOR")
                .andExpect(status().isCreated()).andReturnJson().path("id").asText();
        JsonNode fired = login("fired@cyberclub.local", "Club2026fire");
        mvc.perform(auth(get("/api/clients"), fired)).andExpect(status().isOk());

        mvc.perform(auth(post("/api/admin/users/" + id + "/enabled"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(auth(get("/api/clients"), fired)).andExpect(status().isUnauthorized());
        loginRequest("fired@cyberclub.local", "Club2026fire").andExpect(status().isUnauthorized());
    }

    @Test
    void adminCannotDisableOrDemoteHimself() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String selfId = admin.path("user").path("id").asText();
        mvc.perform(auth(post("/api/admin/users/" + selfId + "/enabled"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/admin/users/" + selfId + "/roles"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"VIEWER\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clientAccountSeesOnlyOwnCabinet() throws Exception {
        JsonNode admin = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String clientJson = json.writeValueAsString(java.util.Map.of("nickname", "site_guest", "fullName", "Гость Сайта",
                "phone", "+79990001122", "email", "Guest@Mail.test", "birthDate", "2001-05-17"));
        long clientId = new JsonResult(mvc.perform(auth(post("/api/clients"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content(clientJson)))
                .andExpect(status().isCreated()).andReturnJson().path("id").asLong();

        createUser(admin, "nobody@mail.test", "Visit2026site", "CLIENT")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());
        String mixed = json.writeValueAsString(java.util.Map.of("email", "guest@mail.test", "displayName", "Гость",
                "password", "Visit2026site", "roles", java.util.List.of("CLIENT", "OPERATOR")));
        mvc.perform(auth(post("/api/admin/users"), admin).contentType(MediaType.APPLICATION_JSON).content(mixed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.roles").exists());
        String accountId = createUser(admin, "guest@mail.test", "Visit2026site", "CLIENT")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andReturnJson().path("id").asText();
        createUser(admin, "GUEST@mail.test", "Visit2026site", "CLIENT").andExpect(status().isConflict());

        JsonNode guest = login("guest@mail.test", "Visit2026site");
        mvc.perform(auth(get("/api/me/client"), guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname").value("site_guest"));
        mvc.perform(auth(get("/api/me/bookings"), guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
        mvc.perform(auth(get("/api/clients"), guest)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/bookings"), guest)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/admin/users"), guest)).andExpect(status().isForbidden());
        mvc.perform(auth(get("/api/me/client"), admin)).andExpect(status().isForbidden());
        mvc.perform(auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/admin/users/" + accountId + "/roles"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"OPERATOR\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"));
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode login(String email, String password) throws Exception {
        return loginRequest(email, password).andExpect(status().isOk()).andReturnJson();
    }

    private JsonResult loginRequest(String email, String password) throws Exception {
        return new JsonResult(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new java.util.LinkedHashMap<>(java.util.Map.of(
                        "email", email, "password", password))))));
    }

    private JsonResult refresh(String refreshToken) throws Exception {
        return new JsonResult(mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}")));
    }

    private JsonResult createUser(JsonNode as, String email, String password, String role) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of(
                "email", email, "displayName", "Тестовый " + role, "password", password, "roles", java.util.List.of(role)));
        return new JsonResult(mvc.perform(auth(post("/api/admin/users"), as)
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, JsonNode tokens) {
        return request.header("Authorization", "Bearer " + tokens.path("accessToken").asText());
    }

    /** Обёртка над ResultActions: проверки цепочкой и разбор JSON ответа. */
    private final class JsonResult {
        private final ResultActions actions;

        JsonResult(ResultActions actions) {
            this.actions = actions;
        }

        JsonResult andExpect(org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        JsonNode andReturnJson() throws Exception {
            return json.readTree(actions.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
