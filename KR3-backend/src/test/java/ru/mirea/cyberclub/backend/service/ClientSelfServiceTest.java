package ru.mirea.cyberclub.backend.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Клиент на сайте: регистрация, бронь из кабинета (всегда неоплаченная), подтверждение оплаты
 * администратором, отмена своей брони, чужие брони и служебные разделы недоступны.
 */
@SpringBootTest(properties = {
        "app.security.bootstrap-admin-email=admin@cyberclub.local",
        "app.security.bootstrap-admin-password=Club2026root",
        "app.security.bootstrap-admin-must-change-password=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ClientSelfServiceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode last;

    @Test
    void clientRegistersBooksAndAdminConfirmsPayment() throws Exception {
        JsonNode admin = login("admin@cyberclub.local", "Club2026root");
        send(post("/api/stations"), admin, Map.of("name", "PC-01", "type", "STANDARD", "hourlyRate", 300))
                .andExpect(status().isCreated());
        long stationId = last.path("id").asLong();
        send(post("/api/stations"), admin, Map.of("name", "VIP-01", "type", "VIP", "hourlyRate", 900))
                .andExpect(status().isCreated());
        long vipId = last.path("id").asLong();

        // регистрация: проверки карточки и пароля, затем сразу вход
        send(post("/api/auth/register"), null, register("Gamer_77", "+79990000001", "petr@mail.test", "2001-03-03", "short"))
                .andExpect(status().isBadRequest());
        send(post("/api/auth/register"), null, register("Gamer_77", "+79990000001", "petr@mail.test", "2001-03-03", "Igra2026night"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.roles[0]").value("CLIENT"))
                .andExpect(jsonPath("$.user.mustChangePassword").value(false));
        JsonNode client = last;
        send(post("/api/auth/register"), null, register("Other_1", "+79990000002", "PETR@mail.test", "2001-03-03", "Igra2026night"))
                .andExpect(status().isConflict());
        send(post("/api/auth/register"), null, register("Teen_1", "+79990000003", "teen@mail.test", "2012-01-01", "Igra2026kid"))
                .andExpect(status().isCreated());
        JsonNode teen = last;

        mvc.perform(auth(get("/api/me/client"), client)).andExpect(jsonPath("$.nickname").value("Gamer_77"));
        mvc.perform(auth(get("/api/me/stations"), client)).andExpect(jsonPath("$.length()").value(2));

        LocalDateTime start = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS).withHour(14);
        send(post("/api/me/bookings"), client, booking(stationId, start, 2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paid").value(false))
                .andExpect(jsonPath("$.clientNickname").value("Gamer_77"))
                .andExpect(jsonPath("$.totalPrice").value(600.0));
        String bookingId = last.path("id").asText();
        // занятое время видно, но без сведений о клиенте; пересечение отклоняется
        mvc.perform(auth(get("/api/me/busy").param("stationId", String.valueOf(stationId))
                        .param("date", start.toLocalDate().toString()), teen))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientNickname").doesNotExist());
        send(post("/api/me/bookings"), teen, booking(stationId, start.plusHours(1), 1)).andExpect(status().isConflict());
        // правило 18+ действует и на сайте
        send(post("/api/me/bookings"), teen, booking(vipId, start, 1)).andExpect(status().isBadRequest());

        // чужую бронь не видно и не отменить; служебные разделы закрыты
        mvc.perform(auth(get("/api/me/bookings"), teen)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(auth(post("/api/me/bookings/" + bookingId + "/cancel"), teen)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/bookings"), client)).andExpect(status().isForbidden());
        send(post("/api/bookings/" + bookingId + "/confirm-payment"), client, Map.of()).andExpect(status().isForbidden());

        // администратор видит неоплаченную бронь с сайта и подтверждает оплату
        mvc.perform(auth(get("/api/bookings").param("paid", "false"), admin)).andExpect(jsonPath("$.totalElements").value(1));
        send(post("/api/bookings/" + bookingId + "/confirm-payment"), admin, Map.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.riskLevel").value("NONE"));
        send(post("/api/bookings/" + bookingId + "/confirm-payment"), admin, Map.of()).andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/me/bookings"), client)).andExpect(jsonPath("$.content[0].paid").value(true));

        mvc.perform(auth(post("/api/me/bookings/" + bookingId + "/cancel"), client))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(auth(get("/api/admin/audit").param("type", "CLIENT_REGISTERED"), admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(auth(get("/api/admin/audit").param("type", "BOOKING_PAID"), admin))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    private static Map<String, Object> register(String nickname, String phone, String email, String birthDate, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", nickname);
        body.put("fullName", "Клиент " + nickname);
        body.put("phone", phone);
        body.put("email", email);
        body.put("birthDate", birthDate);
        body.put("password", password);
        return body;
    }

    private static Map<String, Object> booking(long stationId, LocalDateTime start, int hours) {
        return Map.of("stationId", stationId, "startTime", start.toString(), "endTime", start.plusHours(hours).toString());
    }

    private JsonNode login(String email, String password) throws Exception {
        send(post("/api/auth/login"), null, Map.of("email", email, "password", password)).andExpect(status().isOk());
        return last;
    }

    private ResultActions send(MockHttpServletRequestBuilder request, JsonNode as, Object body) throws Exception {
        MockHttpServletRequestBuilder r = request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        ResultActions actions = mvc.perform(as == null ? r : auth(r, as));
        String response = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        last = response.isEmpty() ? null : json.readTree(response);
        return actions;
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, JsonNode tokens) {
        return request.header("Authorization", "Bearer " + tokens.path("accessToken").asText());
    }

    @SuppressWarnings("unused")
    private static List<String> none() {
        return List.of();
    }
}
