package ru.mirea.cyberclub.mcp.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * REST-адаптер проверяется на поддельном HTTP-сервере с ответами в формате КР3.
 * Настоящий бэкенд для юнита не нужен: важна именно перекладка ответов в доменные
 * модели и читаемость ошибок, которые уйдут наружу как есть.
 */
@DisplayName("REST-адаптер MCP (RestClubApi)")
class RestClubApiTest {

    private static final String BOOKING_JSON = """
            {"id":15,"number":15,"version":2,
             "clientId":4,"clientNickname":"ProGamer","clientFullName":"Кузнецов Дмитрий Олегович",
             "stationId":6,"stationName":"VIP-02","stationType":"VIP",
             "startTime":"2027-01-01T00:00:00","endTime":"2027-01-01T02:00:00",
             "durationMinutes":120,"status":"CREATED","statusTitle":"Создано",
             "totalPrice":600.00,"paid":false,"comment":"Полночь","archived":false}""";

    private HttpServer server;
    private RestClubApi api;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastMethod = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        api = new RestClubApi("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** Подменяет ответ на любой путь: контекст один, поэтому старый сначала снимается. */
    private void respond(String method, int status, String body) {
        removeContext();
        server.createContext("/", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            lastMethod.set(exchange.getRequestMethod());
            byte[] request = exchange.getRequestBody().readAllBytes();
            if (request.length > 0) {
                lastBody.set(new String(request, StandardCharsets.UTF_8));
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private void removeContext() {
        try {
            server.removeContext("/");
        } catch (IllegalArgumentException notRegistered) {
            // контекст ещё не создавался — это нормально для первого ответа в тесте
        }
    }

    // ------------------------------------------------------------ чтение

    @Test
    @DisplayName("Список броней: id и номер читаются из ответа, деньги не теряют точность")
    void listBookingsMapsPageContent() {
        respond("GET", 200, "{\"content\":[" + BOOKING_JSON + "],\"totalElements\":1}");

        List<Booking> bookings = api.allBookings();

        assertThat(bookings).hasSize(1);
        Booking b = bookings.get(0);
        assertThat(b.getId()).isEqualTo(15L);
        assertThat(b.getStartTime()).isEqualTo(LocalDateTime.of(2027, 1, 1, 0, 0));
        assertThat(b.getEndTime()).isEqualTo(LocalDateTime.of(2027, 1, 1, 2, 0));
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CREATED);
        assertThat(b.getTotalPrice()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(b.getTotalPrice().toString()).isEqualTo("600.00");
        assertThat(b.getStationType()).isEqualTo(StationType.VIP);
        assertThat(b.getClientNickname()).isEqualTo("ProGamer");
        assertThat(lastPath.get()).contains("size=");
    }

    @Test
    @DisplayName("Карточка брони поднимается по общему идентификатору")
    void getBookingById() {
        respond("GET", 200, BOOKING_JSON);

        Booking b = api.booking(15);

        assertThat(b.getId()).isEqualTo(15L);
        assertThat(lastPath.get()).isEqualTo("/api/bookings/15");
    }

    @Test
    @DisplayName("Клиенты и стационары перекладываются в доменные модели")
    void clientsAndStationsMap() {
        respond("GET", 200, """
                [{"id":7,"nickname":"Amegon","fullName":"Амегон","phone":"+79161234577",
                  "email":"amegon@mail.ru","birthDate":"2005-01-01","age":21}]""");
        List<Client> clients = api.allClients();

        respond("GET", 200, """
                [{"id":1,"name":"PC-01","type":"STANDARD","typeTitle":"Стандарт",
                  "hourlyRate":150.00,"specs":"RTX 4060","active":true}]""");
        List<Station> stations = api.allStations();

        assertThat(clients).hasSize(1);
        assertThat(clients.get(0).getNickname()).isEqualTo("Amegon");
        assertThat(clients.get(0).getFullName()).isEqualTo("Амегон");
        assertThat(clients.get(0).getBirthDate()).hasToString("2005-01-01");

        assertThat(stations).hasSize(1);
        assertThat(stations.get(0).getType()).isEqualTo(StationType.STANDARD);
        assertThat(stations.get(0).getHourlyRate()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(stations.get(0).isActive()).isTrue();
        // иначе через toString() получилось бы «1.5E+2» — представления печатают сумму так
        assertThat(stations.get(0).getHourlyRate().toString()).isEqualTo("150.00");
    }

    @Test
    @DisplayName("Фильтр по типу зоны применяется на стороне адаптера")
    void stationsByTypeFiltersClientSide() {
        respond("GET", 200, """
                [{"id":1,"name":"PC-01","type":"STANDARD","hourlyRate":150.00,"active":true},
                 {"id":6,"name":"VIP-02","type":"VIP","hourlyRate":300.00,"active":true}]""");

        List<Station> vip = api.stationsByType(StationType.VIP);

        assertThat(vip).extracting(Station::getName).containsExactly("VIP-02");
    }

    // ------------------------------------------------------------ запись

    @Test
    @DisplayName("Создание клиента уходит в POST с ожидаемым телом")
    void createClientPostsJson() {
        respond("POST", 201, """
                {"id":9,"nickname":"Amegon","fullName":"Амегон","phone":"+79161234577",
                 "email":"amegon@mail.ru","birthDate":"2005-01-01","age":21}""");

        Client form = new Client();
        form.setNickname("Amegon");
        form.setFullName("Амегон");
        form.setPhone("+79161234577");
        form.setEmail("amegon@mail.ru");
        form.setBirthDate(java.time.LocalDate.of(2005, 1, 1));
        Client saved = api.createClient(form);

        assertThat(saved.getId()).isEqualTo(9L);
        assertThat(lastPath.get()).isEqualTo("/api/clients");
        assertThat(lastBody.get())
                .contains("\"nickname\":\"Amegon\"")
                .contains("\"fullName\":\"Амегон\"")
                .contains("\"birthDate\":\"2005-01-01\"");
    }

    @Test
    @DisplayName("Карточка клиента читается по идентификатору")
    void getClientById() {
        respond("GET", 200, """
                {"id":9,"nickname":"Amegon","fullName":"Амегон","phone":"+79161234577",
                 "email":"amegon@mail.ru","birthDate":"2005-01-01","age":21}""");

        Client client = api.client(9);

        assertThat(client.getId()).isEqualTo(9L);
        assertThat(client.getNickname()).isEqualTo("Amegon");
        assertThat(client.getPhone()).isEqualTo("+79161234577");
        assertThat(lastPath.get()).isEqualTo("/api/clients/9");
    }

    @Test
    @DisplayName("Изменение клиента: version берётся из GET, затем PUT с новыми полями")
    void updateClientSendsVersion() {
        respond("PUT", 200, """
                {"id":9,"nickname":"Amegon","fullName":"Амегон","phone":"+79161234588",
                 "email":"amegon@mail.ru","birthDate":"2005-01-01","age":21,"version":3}""");

        Client client = new Client();
        client.setId(9L);
        client.setNickname("Amegon");
        client.setFullName("Амегон");
        client.setPhone("+79161234588");
        client.setEmail("amegon@mail.ru");
        client.setBirthDate(java.time.LocalDate.of(2005, 1, 1));

        Client updated = api.updateClient(client);

        assertThat(updated.getPhone()).isEqualTo("+79161234588");
        assertThat(lastPath.get()).isEqualTo("/api/clients/9");
        assertThat(lastMethod.get()).isEqualTo("PUT");
        // version пришёл в ответе на GET и обязан уехать обратно, иначе бэкенд
        // ответит конфликтом «данные устарели».
        assertThat(lastBody.get())
                .contains("\"version\":3")
                .contains("\"+79161234588\"")
                .contains("\"nickname\":\"Amegon\"");
    }

    @Test
    @DisplayName("Удаление клиента уходит в DELETE и не падает на пустом теле 204")
    void deleteClientSendsDelete() {
        respond("DELETE", 204, "");

        api.deleteClient(9);

        assertThat(lastPath.get()).isEqualTo("/api/clients/9");
        assertThat(lastMethod.get()).isEqualTo("DELETE");
    }

    @Test
    @DisplayName("Создание брони передаёт времена в ISO-формате")
    void createBookingPostsIsoTimes() {
        respond("POST", 201, BOOKING_JSON);

        Booking created = api.createBooking(4, 6,
                LocalDateTime.of(2027, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 2, 0), "Полночь");

        assertThat(created.getId()).isEqualTo(15L);
        assertThat(lastPath.get()).isEqualTo("/api/bookings");
        // ISO-8601 без секунд, когда они равны нулю — так пишет LocalDateTime.toString().
        // Парсер бэкенда это принимает.
        assertThat(lastBody.get())
                .contains("\"startTime\":\"2027-01-01T00:00\"")
                .contains("\"endTime\":\"2027-01-01T02:00\"")
                .contains("\"comment\":\"Полночь\"");
    }

    @Test
    @DisplayName("Смена статуса: version читается из карточки и уходит в теле запроса")
    void changeStatusSendsVersion() {
        removeContext();
        server.createContext("/api/bookings", exchange -> {
            String path = exchange.getRequestURI().getPath();
            lastPath.set(exchange.getRequestURI().toString());
            String body;
            int status;
            if ("POST".equals(exchange.getRequestMethod()) && path.endsWith("/status")) {
                lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                body = BOOKING_JSON.replace("CREATED", "CONFIRMED").replace("Создано", "Подтверждено");
                status = 200;
            } else {
                body = BOOKING_JSON;
                status = 200;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        Booking updated = api.changeBookingStatus(15, BookingStatus.CONFIRMED);

        assertThat(updated.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(lastPath.get()).isEqualTo("/api/bookings/15/status");
        assertThat(lastBody.get())
                .contains("\"status\":\"CONFIRMED\"")
                .contains("\"version\":2");
    }

    @Test
    @DisplayName("Перенос брони: clientId и version берутся из карточки, меняются место и время")
    void rescheduleKeepsClientAndSendsVersion() {
        removeContext();
        server.createContext("/api/bookings", exchange -> {
            lastPath.set(exchange.getRequestURI().toString());
            lastMethod.set(exchange.getRequestMethod());
            String body;
            if ("PUT".equals(exchange.getRequestMethod())) {
                lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                body = BOOKING_JSON.replace("VIP-02", "PC-03");
            } else {
                body = BOOKING_JSON;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });

        Booking moved = api.rescheduleBooking(15, 3,
                LocalDateTime.of(2027, 1, 2, 10, 0), LocalDateTime.of(2027, 1, 2, 12, 0), "Турнир");

        assertThat(moved.getStationName()).isEqualTo("PC-03");
        assertThat(lastPath.get()).isEqualTo("/api/bookings/15");
        assertThat(lastMethod.get()).isEqualTo("PUT");
        assertThat(lastBody.get())
                .contains("\"clientId\":4")
                .contains("\"stationId\":3")
                .contains("\"startTime\":\"2027-01-02T10:00\"")
                .contains("\"version\":2")
                .contains("\"paid\":false");
    }

    // ------------------------------------------------------------ ошибки

    @Test
    @DisplayName("Бизнес-ошибка бэкенда уходит наружу текстом, а не кодом 409")
    void businessErrorKeepsMessage() {
        respond("POST", 409, """
                {"timestamp":"2026-10-21T11:42:09Z","status":409,"code":"BUSINESS_RULE",
                 "message":"Станция уже занята в это время.","path":"/api/bookings",
                 "correlationId":"abc"}""");

        assertThatThrownBy(() -> api.createBooking(4, 6,
                LocalDateTime.of(2027, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 2, 0), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("409")
                .hasMessageContaining("Станция уже занята в это время.")
                .hasMessageNotContaining("correlationId");
    }

    @Test
    @DisplayName("404 распознаётся как отсутствие сущности, а не как поломка")
    void notFoundIsEntityNotFound() {
        respond("GET", 404, """
                {"status":404,"code":"NOT_FOUND","message":"Бронь с номером 999 не найдена."}""");

        assertThatThrownBy(() -> api.booking(999))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("Недоступный сервер объясняет, что нужно поднять KR3-backend")
    void unreachableServerExplainsItself() {
        server.stop(0);

        assertThatThrownBy(() -> api.allClients())
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Не удалось связаться с REST API")
                .hasMessageContaining("KR3-backend");
    }

    // ------------------------------------------------------------ статистика

    @Test
    @DisplayName("Сводка собирается из /api/analytics/summary")
    void statisticsFromSummary() {
        respond("GET", 200, """
                {"totalBookings":15,"activeBookings":8,"archivedBookings":7,"paidBookings":3,
                 "averagePrice":420.00,"completedRevenue":6600.00,
                 "byStatus":{"CONFIRMED":4,"COMPLETED":3},
                 "byStation":{"PC-01":3},"byStationType":{"STANDARD":5}}""");

        String text = api.statisticsText();

        assertThat(text)
                .contains("Всего бронирований: 15")
                .contains("Средняя стоимость брони: 420.00 ₽")
                .contains("Выручка по завершённым: 6600.00 ₽")
                .contains("    - PC-01: 3");
    }

    @Test
    @DisplayName("Описание источника показывает адрес бэкенда")
    void describeShowsBaseUrl() {
        assertThat(api.describe()).contains("REST API").contains(String.valueOf(server.getAddress().getPort()));
    }
}
