package ru.mirea.cyberclub.mcp.rest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.mcp.ClubApi;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * {@link ClubApi} поверх REST API КР3 ({@code KR3-backend}): MCP-сервер больше не открывает
 * соединение с PostgreSQL, а ходит в HTTP — ровно так же, как это сделал бы любой внешний клиент.
 *
 * <p>КР2 и КР3 работают с одной базой, поэтому идентификаторы совпадают: {@code id} брони —
 * обычное число, как и в доменной модели приложения. Поле {@code number} — это номер для
 * людей (#12), он может не совпадать с {@code id} после удалений и архивирования, поэтому
 * наружу отдаётся именно {@code id}.</p>
 */
public final class RestClubApi implements ClubApi {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    /** КР3 разрешает size от 1 до 100 — больше не отдаст, поэтому читаем постранично. */
    private static final int PAGE_SIZE = 100;
    /** Предохранитель от бесконечного листания, если бэкенд перестанет отдавать last. */
    private static final int MAX_PAGES = 50;

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;
    private final String describe;

    public RestClubApi(String baseUrl) {
        this(baseUrl,
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build(),
                // Деньги нельзя пускать через double: 420.00 превратилось бы в 420.0,
                // а на больших суммах возможна ещё и потеря разрядов.
                new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS));
    }

    RestClubApi(String baseUrl, HttpClient http, ObjectMapper json) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.http = http;
        this.json = json;
        this.describe = "REST API (КР3): " + this.baseUrl;
    }

    /** Куда показывает переменная окружения — пустая строка означает режим БД. */
    public static String configuredBaseUrl() {
        String url = System.getenv("CYBERCLUB_API_URL");
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String stripTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    @Override
    public String describe() {
        return describe;
    }

    // ------------------------------------------------------------ брони

    @Override
    public List<Booking> allBookings() {
        // Инструменты (например, поиск свободных мест) должны видеть все брони,
        // а КР3 отдаёт максимум 100 за страницу — листаем до последней.
        List<Booking> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            JsonNode node = get("/api/bookings?size=" + PAGE_SIZE + "&page=" + page);
            List<Booking> chunk = bookings(node);
            all.addAll(chunk);
            if (chunk.size() < PAGE_SIZE || node.path("last").asBoolean(false)) {
                break;
            }
        }
        return all;
    }

    @Override
    public List<Booking> bookingsByStatus(BookingStatus status) {
        return bookings(get("/api/bookings?size=" + PAGE_SIZE + "&status=" + status.name()));
    }

    @Override
    public List<Booking> bookingsByDate(LocalDate date) {
        return bookings(get("/api/bookings?size=" + PAGE_SIZE + "&from=" + date + "&to=" + date));
    }

    @Override
    public List<Booking> bookingsByPeriod(LocalDateTime from, LocalDateTime to) {
        String query = "/api/bookings?size=" + PAGE_SIZE
                + "&from=" + toLocalDate(from) + "&to=" + toLocalDate(to);
        List<Booking> inRange = new ArrayList<>();
        for (Booking b : bookings(get(query))) {
            // REST фильтрует по датам, а не по моментам — периоды границей отсекаем сами.
            boolean afterStart = !b.getStartTime().isBefore(from);
            boolean beforeEnd = !b.getEndTime().isAfter(to);
            if (afterStart && beforeEnd) {
                inRange.add(b);
            }
        }
        return inRange;
    }

    @Override
    public List<Booking> bookingsByClient(long clientId) {
        return bookings(get("/api/bookings?size=" + PAGE_SIZE + "&clientId=" + clientId));
    }

    @Override
    public List<Booking> searchBookings(String query) {
        return bookings(get("/api/bookings?size=" + PAGE_SIZE + "&q=" + encode(query)));
    }

    @Override
    public Booking booking(long id) {
        return booking(get("/api/bookings/" + id));
    }

    @Override
    public Booking createBooking(long clientId, long stationId, LocalDateTime start, LocalDateTime end,
                                 String comment) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", clientId);
        body.put("stationId", stationId);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        if (comment != null && !comment.isBlank()) {
            body.put("comment", comment);
        }
        return booking(post("/api/bookings", body));
    }

    @Override
    public Booking changeBookingStatus(long id, BookingStatus status) {
        // КР3 требует актуальный version: поднимаем карточку и отправляем его вместе со статусом,
        // иначе бэкенд ответит 400 «Укажите version».
        long version = get("/api/bookings/" + id).path("version").asLong(0);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.name());
        body.put("version", version);
        return booking(post("/api/bookings/" + id + "/status", body));
    }

    @Override
    public Booking rescheduleBooking(long id, long stationId, LocalDateTime start, LocalDateTime end,
                                     String comment) {
        // PUT в КР3 заменяет карточку целиком: клиента и оплату берём из текущей брони,
        // иначе бэкенд отклонит запрос («клиента менять нельзя») или обнулит признак оплаты.
        JsonNode current = get("/api/bookings/" + id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", current.path("clientId").asLong());
        body.put("stationId", stationId);
        body.put("startTime", start.toString());
        body.put("endTime", end.toString());
        body.put("comment", comment);
        body.put("paid", current.path("paid").asBoolean(false));
        body.put("version", current.path("version").asLong(0));
        return booking(put("/api/bookings/" + id, body));
    }

    // ------------------------------------------------------------ клиенты

    @Override
    public List<Client> allClients() {
        return clients(get("/api/clients"));
    }

    @Override
    public List<Client> searchClients(String query) {
        return clients(get("/api/clients?q=" + encode(query)));
    }

    @Override
    public Client client(long id) {
        return client(get("/api/clients/" + id));
    }

    @Override
    public Client createClient(Client client) {
        return client(post("/api/clients", clientBody(client)));
    }

    @Override
    public Client updateClient(Client client) {
        long id = client.getId();
        // Изменение в КР3 требует актуальный version — берём его из текущей карточки,
        // иначе бэкенд ответит конфликтом «данные устарели».
        long version = get("/api/clients/" + id).path("version").asLong(0);
        Map<String, Object> body = clientBody(client);
        body.put("version", version);
        return client(put("/api/clients/" + id, body));
    }

    @Override
    public void deleteClient(long id) {
        send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/clients/" + id))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .DELETE()
                .build());
    }

    private static Map<String, Object> clientBody(Client client) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("nickname", client.getNickname());
        body.put("fullName", client.getFullName());
        body.put("phone", client.getPhone());
        body.put("email", client.getEmail());
        body.put("birthDate", client.getBirthDate() == null ? null : client.getBirthDate().toString());
        return body;
    }

    // ------------------------------------------------------------ стационары

    @Override
    public List<Station> allStations() {
        return stations(get("/api/stations"));
    }

    @Override
    public List<Station> stationsByType(StationType type) {
        List<Station> matched = new ArrayList<>();
        for (Station st : allStations()) {
            if (st.getType() == type) {
                matched.add(st);
            }
        }
        return matched;
    }

    @Override
    public List<Station> activeStations() {
        List<Station> active = new ArrayList<>();
        for (Station st : allStations()) {
            if (st.isActive()) {
                active.add(st);
            }
        }
        return active;
    }

    // ------------------------------------------------------------ статистика

    @Override
    public String statisticsText() {
        JsonNode node = get("/api/analytics/summary");
        StringBuilder out = new StringBuilder("Статистика клуба (REST API, архивные брони не входят):\n");
        line(out, "Всего бронирований", node.path("totalBookings").asLong());
        line(out, "Действующих броней", node.path("activeBookings").asLong());
        line(out, "В архиве", node.path("archivedBookings").asLong());
        line(out, "Оплаченных", node.path("paidBookings").asLong());
        line(out, "Средняя стоимость брони", money(node.path("averagePrice")));
        line(out, "Выручка по завершённым", money(node.path("completedRevenue")));
        appendGroups(out, "По статусам", node.path("byStatus"));
        appendGroups(out, "По игровым местам", node.path("byStation"));
        appendGroups(out, "По зонам", node.path("byStationType"));
        return out.toString().stripTrailing();
    }

    private static void line(StringBuilder out, String name, Object value) {
        out.append("  - ").append(name).append(": ").append(value).append('\n');
    }

    /**
     * Деньги показываем с двумя знаками. Jackson отбрасывает хвостовые нули, из-за чего
     * 420.00 превратилось бы в 420 — для суммы это выглядит как ошибка округления.
     */
    private static String money(JsonNode node) {
        if (!node.isNumber()) {
            return "—";
        }
        return node.decimalValue().setScale(2, RoundingMode.HALF_UP).toPlainString() + " ₽";
    }

    private static void appendGroups(StringBuilder out, String title, JsonNode groups) {
        if (!groups.isObject() || groups.isEmpty()) {
            return;
        }
        out.append("  ").append(title).append(":\n");
        var names = groups.fieldNames();
        while (names.hasNext()) {
            String key = names.next();
            out.append("    - ").append(key).append(": ").append(groups.path(key).asLong()).append('\n');
        }
    }

    // ------------------------------------------------------------ HTTP

    private JsonNode get(String path) {
        return send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build());
    }

    private JsonNode post(String path, Map<String, Object> body) {
        return withBody("POST", path, body);
    }

    private JsonNode put(String path, Map<String, Object> body) {
        return withBody("PUT", path, body);
    }

    private JsonNode withBody(String method, String path, Map<String, Object> body) {
        String payload;
        try {
            payload = json.writeValueAsString(body);
        } catch (Exception e) {
            throw new AppException("Не удалось подготовить запрос к REST API: " + e.getMessage());
        }
        return send(HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(payload))
                .build());
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppException("Запрос к REST API прерван.");
        } catch (Exception e) {
            throw new AppException("Не удалось связаться с REST API (" + baseUrl + "): "
                    + e.getMessage() + ". Убедитесь, что KR3-backend запущен.");
        }
        JsonNode body = parseQuietly(response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw error(response.statusCode(), body, response.body());
        }
        return body;
    }

    /**
     * Бэкенд отдаёт ошибки в формате {@code ApiError} с полем message — оно и нужно наружу,
     * чтобы ИИ увидел «станция занята», а не код 409. Стек-трейсы и внутренности не показываем.
     */
    private AppException error(int status, JsonNode body, String raw) {
        String message = body != null ? body.path("message").asText(null) : null;
        if (message == null || message.isBlank()) {
            message = raw == null || raw.isBlank() ? "без описания" : clip(raw);
        }
        if (status == 404) {
            return new EntityNotFoundException(message);
        }
        return new AppException("REST API ответил ошибкой " + status + ": " + message);
    }

    private JsonNode parseQuietly(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String clip(String raw) {
        return raw.length() <= 300 ? raw : raw.substring(0, 300) + "…";
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String toLocalDate(LocalDateTime moment) {
        return moment.toLocalDate().toString();
    }

    // ------------------------------------------------------------ разбор ответов

    private List<Booking> bookings(JsonNode page) {
        JsonNode content = page != null && page.isObject() ? page.path("content") : page;
        List<Booking> result = new ArrayList<>();
        if (content == null || !content.isArray()) {
            return result;
        }
        for (JsonNode node : content) {
            result.add(booking(node));
        }
        return result;
    }

    private List<Client> clients(JsonNode array) {
        List<Client> result = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                result.add(client(node));
            }
        }
        return result;
    }

    private List<Station> stations(JsonNode array) {
        List<Station> result = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode node : array) {
                result.add(station(node));
            }
        }
        return result;
    }

    private static Booking booking(JsonNode node) {
        Booking b = new Booking();
        // Идентификатор общий для КР2 и КР3: это первичный ключ таблицы bookings.
        b.setId(node.path("id").asLong(0));
        b.setClientId(longOrNull(node, "clientId"));
        b.setStationId(longOrNull(node, "stationId"));
        b.setStartTime(localDateTime(node, "startTime"));
        b.setEndTime(localDateTime(node, "endTime"));
        b.setStatus(enumOrNull(node, "status", BookingStatus.class));
        b.setTotalPrice(moneyOrNull(node, "totalPrice"));
        b.setComment(node.path("comment").isTextual() ? node.path("comment").asText() : null);
        b.setClientNickname(textOrNull(node, "clientNickname"));
        b.setClientFullName(textOrNull(node, "clientFullName"));
        b.setStationName(textOrNull(node, "stationName"));
        b.setStationType(enumOrNull(node, "stationType", StationType.class));
        return b;
    }

    private static Client client(JsonNode node) {
        Client c = new Client();
        c.setId(node.path("id").asLong(0));
        c.setNickname(node.path("nickname").asText(null));
        c.setFullName(node.path("fullName").asText(null));
        c.setPhone(node.path("phone").asText(null));
        c.setEmail(node.path("email").asText(null));
        c.setBirthDate(node.path("birthDate").isTextual()
                ? LocalDate.parse(node.path("birthDate").asText()) : null);
        return c;
    }

    private static Station station(JsonNode node) {
        Station st = new Station();
        st.setId(node.path("id").asLong(0));
        st.setName(node.path("name").asText(null));
        st.setType(enumOrNull(node, "type", StationType.class));
        st.setHourlyRate(moneyOrNull(node, "hourlyRate"));
        st.setSpecs(textOrNull(node, "specs"));
        st.setActive(node.path("active").asBoolean(true));
        return st;
    }

    private static Long longOrNull(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asLong() : null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }

    private static LocalDateTime localDateTime(JsonNode node, String field) {
        return node.path(field).isTextual() ? LocalDateTime.parse(node.path(field).asText()) : null;
    }

    /**
     * Деньги возвращаем со scale 2: Jackson отбрасывает хвостовые нули, и BigDecimal
     * начинает печатать в научной записи — 250.00 превратилось бы в «2.5E+2 ₽/ч».
     * С представлениями это бы не совпало: там сумма выводится через toString().
     */
    private static BigDecimal moneyOrNull(JsonNode node, String field) {
        return node.path(field).isNumber()
                ? node.path(field).decimalValue().setScale(2, RoundingMode.HALF_UP)
                : null;
    }

    private static <E extends Enum<E>> E enumOrNull(JsonNode node, String field, Class<E> type) {
        String value = textOrNull(node, field);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
