package ru.mirea.cyberclub.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Сквозная проверка функций основной сущности на H2: теги и риск неоплаты, фильтры по ним,
 * заметки с правами автора, массовые операции с отдельными ошибками, импорт и экспорт CSV, аналитика.
 */
@SpringBootTest(properties = {
        "app.security.bootstrap-admin-email=admin@cyberclub.local",
        "app.security.bootstrap-admin-password=Club2026root",
        "app.security.bootstrap-admin-must-change-password=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BookingFeaturesTest {
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private JsonNode admin;
    private long clientId;
    private long stationId;
    private LocalDateTime base;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@cyberclub.local", "Club2026root");
        send(post("/api/clients"), admin, Map.of("nickname", "Frost", "fullName", "Морозов Артём",
                "phone", "+79990001111", "email", "frost@mail.test", "birthDate", "2000-01-15"))
                .andExpect(status().isCreated());
        clientId = lastJson.path("id").asLong();
        send(post("/api/stations"), admin, Map.of("name", "PC-01", "type", "STANDARD",
                "hourlyRate", 400, "specs", "RTX 4070"))
                .andExpect(status().isCreated());
        stationId = lastJson.path("id").asLong();
        base = LocalDateTime.now().plusDays(3).truncatedTo(ChronoUnit.HOURS).withHour(12);
    }

    @Test
    void tagsAndPaymentRiskAreStoredAndFilterable() throws Exception {
        JsonNode cheap = createBooking(0, 1, false, List.of("Турнир", "  постоянный "));
        JsonNode pricey = createBooking(2, 3, false, List.of());   // 3 ч × 400 = 1200 руб. → MEDIUM
        JsonNode paid = createBooking(6, 2, true, List.of("турнир"));

        assertThat(cheap.path("riskLevel").asText()).isEqualTo("LOW");
        assertThat(cheap.path("tags").toString()).isEqualTo("[\"постоянный\",\"турнир\"]");
        assertThat(pricey.path("riskLevel").asText()).isEqualTo("MEDIUM");
        assertThat(paid.path("riskLevel").asText()).isEqualTo("NONE");

        mvc.perform(auth(get("/api/bookings").param("tag", "ТУРНИР"), admin))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(auth(get("/api/bookings").param("riskLevel", "MEDIUM"), admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].number").value(pricey.path("number").asLong()));

        // бронь оплатили — риск пересчитан сервером
        Map<String, Object> update = bookingBody(2, 3, true, null);
        update.put("version", pricey.path("version").asLong());
        send(put("/api/bookings/" + pricey.path("id").asText()), admin, update)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskLevel").value("NONE"))
                .andExpect(jsonPath("$.tags.length()").value(0));

        send(post("/api/bookings"), admin, bookingBody(10, 1, false, List.of("плохой тег!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.tags").exists());

        mvc.perform(auth(get("/api/analytics/summary"), admin))
                .andExpect(jsonPath("$.byRiskLevel.LOW").value(1))
                .andExpect(jsonPath("$.byRiskLevel.NONE").value(2));
    }

    @Test
    void notesKeepAuthorAndOnlyAuthorOrAdminEdits() throws Exception {
        String bookingId = createBooking(0, 1, false, List.of()).path("id").asText();
        JsonNode operator = createStaff("op@cyberclub.local", "Smena2026op", "OPERATOR");
        JsonNode viewer = createStaff("view@cyberclub.local", "Smotr2026vw", "VIEWER");

        send(post("/api/bookings/" + bookingId + "/notes"), operator,
                Map.of("body", "Клиент просил место у окна", "pinned", false))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").value("Сотрудник OPERATOR"))
                .andExpect(jsonPath("$.editable").value(true));
        JsonNode note = lastJson;
        send(post("/api/bookings/" + bookingId + "/notes"), admin, Map.of("body", "Закреплено админом", "pinned", true))
                .andExpect(status().isCreated());

        mvc.perform(auth(get("/api/bookings/" + bookingId + "/notes"), viewer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pinned").value(true))
                .andExpect(jsonPath("$[1].editable").value(false));
        send(post("/api/bookings/" + bookingId + "/notes"), viewer, Map.of("body", "нельзя", "pinned", false))
                .andExpect(status().isForbidden());

        String noteUrl = "/api/bookings/" + bookingId + "/notes/" + note.path("id").asText();
        send(put(noteUrl), operator, Map.of("body", "Место у окна, перезвонить", "pinned", true,
                "version", note.path("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
        mvc.perform(auth(delete(noteUrl), admin)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/bookings/" + bookingId + "/notes"), admin)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void bulkOperationReportsEachFailureSeparately() throws Exception {
        String first = createBooking(0, 1, false, List.of()).path("id").asText();
        String second = createBooking(2, 1, false, List.of()).path("id").asText();
        String third = createBooking(4, 1, false, List.of()).path("id").asText();
        send(post("/api/bookings/" + third + "/status"), admin, Map.of("status", "CONFIRMED", "version", 0))
                .andExpect(status().isOk());
        send(post("/api/bookings/" + third + "/status"), admin, Map.of("status", "ACTIVE", "version", 1))
                .andExpect(status().isOk());

        send(post("/api/bookings/bulk"), admin, Map.of("ids", List.of(first, second, third), "action", "ARCHIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested").value(3))
                .andExpect(jsonPath("$.succeeded").value(2))
                .andExpect(jsonPath("$.failures.length()").value(1))
                .andExpect(jsonPath("$.failures[0].id").value(third));
        send(post("/api/bookings/bulk"), admin, Map.of("ids", List.of(first, second), "action", "RESTORE"))
                .andExpect(jsonPath("$.succeeded").value(2));
        send(post("/api/bookings/bulk"), admin, Map.of("ids", List.of(first, second), "action", "ADD_TAG", "tag", "VIP"))
                .andExpect(jsonPath("$.succeeded").value(2));
        mvc.perform(auth(get("/api/bookings").param("tag", "vip"), admin)).andExpect(jsonPath("$.totalElements").value(2));
        send(post("/api/bookings/bulk"), admin, Map.of("ids", List.of(first, second), "action", "SET_STATUS", "status", "CONFIRMED"))
                .andExpect(jsonPath("$.succeeded").value(2));
        send(post("/api/bookings/bulk"), admin, Map.of("ids", List.of(first), "action", "SET_STATUS"))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(get("/api/admin/audit").param("type", "BOOKING_BULK"), admin))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void csvPreviewDoesNotSaveAndApplyCreatesAndUpdates() throws Exception {
        JsonNode existing = createBooking(0, 1, false, List.of());
        long number = existing.path("number").asLong();
        String csv = "﻿number;client;station;start;end;paid;comment;tags\r\n"
                + ";frost;pc-01;" + time(4) + ";" + time(6) + ";да;из файла;импорт|csv\r\n"
                + number + ";Frost;PC-01;" + time(0) + ";" + time(2) + ";true;;\r\n"
                + ";nobody;PC-01;" + time(8) + ";" + time(9) + ";нет;;\r\n"
                + ";Frost;PC-01;" + time(5) + ";" + time(7) + ";нет;пересекается;\r\n";
        MockMultipartFile file = new MockMultipartFile("file", "bookings.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        mvc.perform(auth(multipart("/api/bookings/import/preview").file(file).param("mode", "UPSERT_BY_NUMBER"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.validRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(2))
                .andExpect(jsonPath("$.toCreate").value(1))
                .andExpect(jsonPath("$.toUpdate").value(1))
                .andExpect(jsonPath("$.issues[?(@.row == 4 && @.field == 'client')]").exists());
        mvc.perform(auth(get("/api/bookings"), admin)).andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(auth(multipart("/api/bookings/import/apply").file(file).param("mode", "UPSERT_BY_NUMBER"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.updated").value(1))
                .andExpect(jsonPath("$.skipped").value(2));
        mvc.perform(auth(get("/api/bookings").param("tag", "импорт"), admin))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paid").value(true));
        mvc.perform(auth(get("/api/bookings/number/" + number), admin))
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.durationMinutes").value(120));

        // в режиме «только новые» существующие номера пропускаются
        mvc.perform(auth(multipart("/api/bookings/import/preview").file(file).param("mode", "CREATE_ONLY"), admin))
                .andExpect(jsonPath("$.issues[?(@.row == 3 && @.field == 'number')]").exists());

        String exported = mvc.perform(auth(get("/api/bookings/export").param("tag", "импорт"), admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(exported).startsWith("﻿number;client;client_name;station");
        assertThat(exported.lines().count()).isEqualTo(2);
        assertThat(exported).contains("Frost;Морозов Артём;PC-01").contains("csv|импорт");
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode lastJson;

    private JsonNode createBooking(int startHour, int hours, boolean paid, List<String> tags) throws Exception {
        send(post("/api/bookings"), admin, bookingBody(startHour, hours, paid, tags)).andExpect(status().isCreated());
        return lastJson;
    }

    private Map<String, Object> bookingBody(int startHour, int hours, boolean paid, List<String> tags) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        body.put("stationId", stationId);
        body.put("startTime", base.plusHours(startHour).toString());
        body.put("endTime", base.plusHours(startHour + hours).toString());
        body.put("paid", paid);
        if (tags != null) {
            body.put("tags", tags);
        }
        return body;
    }

    private String time(int hour) {
        return CSV_TIME.format(base.plusHours(hour));
    }

    private JsonNode createStaff(String email, String password, String role) throws Exception {
        send(post("/api/admin/users"), admin, Map.of("email", email, "displayName", "Сотрудник " + role,
                "password", password, "roles", List.of(role))).andExpect(status().isCreated());
        JsonNode tokens = login(email, password);
        send(post("/api/auth/password"), tokens, Map.of("currentPassword", password, "newPassword", password + "X"))
                .andExpect(status().isNoContent());
        return login(email, password + "X");
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", password));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response);
    }

    /** Запрос с JSON-телом; разобранный ответ кладётся в {@link #lastJson}. */
    private ResultActions send(MockHttpServletRequestBuilder request, JsonNode as, Object body) throws Exception {
        ResultActions actions = mvc.perform(auth(request, as).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
        String response = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        lastJson = response.isEmpty() ? null : json.readTree(response);
        return actions;
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, JsonNode tokens) {
        return request.header("Authorization", "Bearer " + tokens.path("accessToken").asText());
    }
}
