package ru.mirea.cyberclub.mcp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import ru.mirea.cyberclub.AppServices;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;

/**
 * Инструменты (tools), которые внешний ИИ получает через MCP: чтение и изменение броней,
 * клиентов, стационов и статистики.
 *
 * <p>Класс намеренно не знает ничего про MCP SDK и про JavaFX: каждый инструмент — это
 * запись с именем, описанием, JSON-схемой входа и функцией {@code Map -> String}.
 * Благодаря этому логику можно проверять обычным юнит-тестом без запуска сервера,
 * а сам MCP-слой в {@link CyberclubMcpServer} остаётся тонкой обвязкой.</p>
 *
 * <p>Ошибки бизнес-правил («станция занята», «клиент младше 12 лет») не подавляются:
 * они пробрасываются наружу, чтобы MCP пометил результат как ошибку и ИИ увидел причину.</p>
 */
public final class CyberclubTools {

    private CyberclubTools() {
    }

    /** Описание одного инструмента: как его назвать модели и как его выполнить. */
    public record Tool(
            String name,
            String description,
            Map<String, Object> inputSchema,
            Function<Map<String, Object>, String> body) {

        /** Выполняет инструмент — отдельное имя, чтобы читалось в тестах. */
        public String run(Map<String, Object> args) {
            return body.apply(args == null ? Map.of() : args);
        }
    }

    /** Весь набор инструментов поверх переданных сервисов приложения. */
    public static List<Tool> all(AppServices services) {
        List<Tool> tools = new ArrayList<>();
        tools.add(listBookings(services));
        tools.add(getBooking(services));
        tools.add(createBooking(services));
        tools.add(setBookingStatus(services));
        tools.add(searchBookings(services));
        tools.add(listClients(services));
        tools.add(createClient(services));
        tools.add(listStations(services));
        tools.add(getStatistics(services));
        return tools;
    }

    // ----------------------------------------------------------------- чтение

    private static Tool listBookings(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("status", str("Статус брони: CREATED, CONFIRMED, ACTIVE, COMPLETED, CANCELLED"));
        props.put("date", str("Дата в формате YYYY-MM-DD — покажет брони на этот день"));
        props.put("from", str("Начало периода, ISO-дата и время: 2026-10-14T00:00"));
        props.put("to", str("Конец периода, ISO-дата и время"));
        props.put("limit", num("Максимум строк в ответе, по умолчанию 50"));

        return new Tool("list_bookings",
                "Список бронирований стационаров клуба. Можно отфильтровать по статусу, "
                        + "дате или периоду; без параметров возвращает все брони.",
                schema(props),
                args -> {
                    String status = str(args, "status");
                    String date = str(args, "date");
                    String from = str(args, "from");
                    String to = str(args, "to");
                    int limit = limit(args, 50);

                    List<Booking> bookings;
                    if (status != null) {
                        bookings = s.bookings().filterByStatus(status(status));
                    } else if (date != null) {
                        bookings = s.bookings().searchByDate(dateOnly(date));
                    } else if (from != null || to != null) {
                        LocalDateTime f = from == null ? LocalDateTime.MIN : dateTime(from);
                        LocalDateTime t = to == null ? LocalDateTime.MAX : dateTime(to);
                        bookings = s.bookings().filterByPeriod(f, t);
                    } else {
                        bookings = s.bookings().getAll();
                    }
                    return bookingsView(bookings, limit, "Брони");
                });
    }

    private static Tool getBooking(AppServices s) {
        return new Tool("get_booking",
                "Подробная карточка одной брони по её идентификатору.",
                schema(Map.of("id", num("Идентификатор брони")), "id"),
                args -> {
                    long id = requireLong(args, "id");
                    return bookingView(s.bookings().getById(id));
                });
    }

    private static Tool searchBookings(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", str("Текст поиска: ник клиента, название стационара или комментарий"));
        return new Tool("search_bookings",
                "Поиск броней по нику клиента, названию стационара или комментарию.",
                schema(props, "query"),
                args -> {
                    String query = requireText(args, "query");
                    List<Booking> found = new ArrayList<>();
                    found.addAll(s.bookings().searchByClientNickname(query));
                    found.addAll(s.bookings().searchByStationName(query));
                    found.addAll(s.bookings().searchByComment(query));
                    return bookingsView(distinct(found), 50, "Найденные брони");
                });
    }

    private static Tool listClients(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", str("Строка для поиска по нику, ФИО, телефону или email"));
        return new Tool("list_clients",
                "Список клиентов клуба; с параметром query — поиск среди них.",
                schema(props),
                args -> {
                    String query = str(args, "query");
                    List<Client> clients = query == null ? s.clients().getAll() : s.clients().search(query);
                    return clientsView(clients, 50);
                });
    }

    private static Tool listStations(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("type", str("Тип зоны: STANDARD, VIP, BOOTCAMP, CONSOLE"));
        props.put("activeOnly", bool("Только активные (исправные) стационары, по умолчанию false"));
        return new Tool("list_stations",
                "Список игровых стационаров клуба: зона, тариф, характеристики, доступность.",
                schema(props),
                args -> {
                    String type = str(args, "type");
                    boolean activeOnly = Boolean.TRUE.equals(bool(args, "activeOnly"));
                    List<Station> stations;
                    if (type != null) {
                        stations = s.stations().getByType(stationType(type));
                    } else if (activeOnly) {
                        stations = s.stations().getActive();
                    } else {
                        stations = s.stations().getAll();
                    }
                    return stationsView(stations, activeOnly);
                });
    }

    private static Tool getStatistics(AppServices s) {
        return new Tool("get_statistics",
                "Сводная статистика клуба: клиенты, стационары, брони по статусам, выручка, "
                        + "лучшие клиенты и стационары, брони на сегодня.",
                schema(Map.of()),
                args -> {
                    Statistics st = s.statistics().collect();
                    StringBuilder out = new StringBuilder("Статистика клуба:\n");
                    for (var ind : s.statistics().toIndicators(st)) {
                        out.append("  - ").append(ind.name()).append(": ").append(ind.value()).append('\n');
                    }
                    return out.toString().stripTrailing();
                });
    }

    // ----------------------------------------------------------------- запись

    private static Tool createBooking(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("clientId", num("Идентификатор клиента (см. list_clients)"));
        props.put("clientPhone", str("Телефон клиента в формате +7XXXXXXXXXX — альтернатива clientId"));
        props.put("stationId", num("Идентификатор стационара (см. list_stations)"));
        props.put("stationName", str("Название стационара — альтернатива stationId"));
        props.put("startTime", str("Начало, ISO-дата и время: 2026-10-14T14:00"));
        props.put("endTime", str("Конец, ISO-дата и время: 2026-10-14T16:00"));
        props.put("comment", str("Комментарий к брони, необязательно"));

        return new Tool("create_booking",
                "Создать бронь стационара. Клиент и стационар задаются либо идентификатором, "
                        + "либо телефоном/названием. Проверяет доступность, длительность и возрастные ограничения.",
                schema(props, "startTime", "endTime"),
                args -> {
                    Long clientId = optionalLong(args, "clientId");
                    if (clientId == null) {
                        String phone = requireText(args, "clientPhone");
                        clientId = findClientByPhone(s, phone);
                    }
                    Long stationId = optionalLong(args, "stationId");
                    if (stationId == null) {
                        stationId = findStationByName(s, requireText(args, "stationName"));
                    }
                    Booking created = s.bookings().create(
                            clientId, stationId,
                            dateTime(requireText(args, "startTime")),
                            dateTime(requireText(args, "endTime")),
                            str(args, "comment"));
                    return "Бронь создана:\n" + bookingView(created);
                });
    }

    private static Tool setBookingStatus(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", num("Идентификатор брони"));
        props.put("status", str("Новый статус: CONFIRMED, ACTIVE, COMPLETED или CANCELLED"));
        return new Tool("set_booking_status",
                "Изменить статус брони по машине состояний: CREATED → CONFIRMED/CANCELLED, "
                        + "CONFIRMED → ACTIVE/CANCELLED, ACTIVE → COMPLETED. Недопустимый переход будет отклонён.",
                schema(props, "id", "status"),
                args -> {
                    Booking updated = s.bookings().changeStatus(
                            requireLong(args, "id"), status(requireText(args, "status")));
                    return "Статус обновлён:\n" + bookingView(updated);
                });
    }

    private static Tool createClient(AppServices s) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("nickname", str("Ник: 3-20 символов, латиница, цифры и подчёркивание"));
        props.put("fullName", str("ФИО клиента"));
        props.put("phone", str("Телефон в формате +7XXXXXXXXXX"));
        props.put("email", str("Email клиента"));
        props.put("birthDate", str("Дата рождения в формате YYYY-MM-DD"));
        return new Tool("create_client",
                "Добавить карточку клиента. Проверяет формат полей, уникальность и то, "
                        + "что клиент не младше 12 лет.",
                schema(props, "nickname", "fullName", "phone", "email", "birthDate"),
                args -> {
                    Client client = new Client();
                    client.setNickname(requireText(args, "nickname"));
                    client.setFullName(requireText(args, "fullName"));
                    client.setPhone(requireText(args, "phone"));
                    client.setEmail(requireText(args, "email"));
                    client.setBirthDate(dateOnly(requireText(args, "birthDate")));
                    Client saved = s.clients().create(client);
                    return "Клиент создан:\n" + clientView(saved);
                });
    }

    // ------------------------------------------------------------- представление

    private static String bookingsView(List<Booking> bookings, int limit, String title) {
        if (bookings.isEmpty()) {
            return title + ": ничего не найдено.";
        }
        int shown = Math.min(bookings.size(), limit);
        StringBuilder out = new StringBuilder(title + ": " + bookings.size() + ", показаны первые "
                + shown + ".\n");
        for (int i = 0; i < shown; i++) {
            out.append("  ").append(bookingOneLine(bookings.get(i))).append('\n');
        }
        if (bookings.size() > shown) {
            out.append("  ... и ещё ").append(bookings.size() - shown).append(" — уточните фильтр.");
        }
        return out.toString().stripTrailing();
    }

    private static String bookingOneLine(Booking b) {
        String line = "#" + b.getId()
                + " | " + b.getStartTime() + " → " + b.getEndTime()
                + " | " + nullToDash(b.getClientNickname())
                + " | " + nullToDash(b.getStationName())
                + " | " + b.getStatus().getTitle()
                + " | " + b.getTotalPrice() + " ₽";
        String comment = b.getComment();
        if (comment != null && !comment.isBlank()) {
            line += " | " + clip(comment.replaceAll("\\s+", " ").trim(), 40);
        }
        return line;
    }

    /** Обрезает длинный текст, чтобы список броней оставался компактным. */
    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String bookingView(Booking b) {
        StringBuilder out = new StringBuilder();
        out.append("  Идентификатор: ").append(b.getId()).append('\n');
        out.append("  Клиент: ").append(nullToDash(b.getClientFullName()))
                .append(" (").append(nullToDash(b.getClientNickname())).append(")\n");
        out.append("  Стационар: ").append(nullToDash(b.getStationName()))
                .append(" [").append(b.getStationType() == null ? "?" : b.getStationType().getTitle()).append("]\n");
        out.append("  Начало: ").append(b.getStartTime()).append('\n');
        out.append("  Конец: ").append(b.getEndTime()).append('\n');
        out.append("  Статус: ").append(b.getStatus().getTitle()).append('\n');
        out.append("  Стоимость: ").append(b.getTotalPrice()).append(" ₽\n");
        if (b.getComment() != null && !b.getComment().isBlank()) {
            out.append("  Комментарий: ").append(b.getComment());
        }
        return out.toString().stripTrailing();
    }

    private static String clientsView(List<Client> clients, int limit) {
        if (clients.isEmpty()) {
            return "Клиенты: ничего не найдено.";
        }
        int shown = Math.min(clients.size(), limit);
        StringBuilder out = new StringBuilder("Клиенты: " + clients.size() + ", показаны первые " + shown + ".\n");
        for (int i = 0; i < shown; i++) {
            out.append("  ").append(clientOneLine(clients.get(i))).append('\n');
        }
        if (clients.size() > shown) {
            out.append("  ... и ещё ").append(clients.size() - shown);
        }
        return out.toString().stripTrailing();
    }

    private static String clientOneLine(Client c) {
        return "#" + c.getId() + " | @" + c.getNickname() + " | " + c.getFullName()
                + " | " + c.getPhone() + " | " + c.getEmail()
                + " | " + (c.getBirthDate() == null ? "-" : c.getBirthDate());
    }

    private static String clientView(Client c) {
        StringBuilder out = new StringBuilder();
        out.append("  Идентификатор: ").append(c.getId()).append('\n');
        out.append("  Ник: @").append(c.getNickname()).append('\n');
        out.append("  ФИО: ").append(c.getFullName()).append('\n');
        out.append("  Телефон: ").append(c.getPhone()).append('\n');
        out.append("  Email: ").append(c.getEmail()).append('\n');
        out.append("  Дата рождения: ").append(c.getBirthDate());
        return out.toString();
    }

    private static String stationsView(List<Station> stations, boolean activeOnly) {
        if (stations.isEmpty()) {
            return "Стационары: ничего не найдено.";
        }
        StringBuilder out = new StringBuilder("Стационары: " + stations.size()
                + (activeOnly ? " (только активные)" : "") + ".\n");
        for (Station st : stations) {
            out.append("  #").append(st.getId())
                    .append(" | ").append(st.getName())
                    .append(" | ").append(st.getType().getTitle())
                    .append(" | ").append(st.getHourlyRate()).append(" ₽/ч")
                    .append(" | ").append(st.isActive() ? "активен" : "отключён")
                    .append(" | ").append(nullToDash(st.getSpecs()))
                    .append('\n');
        }
        return out.toString().stripTrailing();
    }

    // ------------------------------------------------------------- разбор аргументов

    private static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String requireText(Map<String, Object> args, String key) {
        String value = str(args, key);
        if (value == null) {
            throw new AppException("Не передан обязательный параметр \"" + key + "\".");
        }
        return value;
    }

    private static Long optionalLong(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new AppException("Параметр \"" + key + "\" должен быть числом, получено: " + text);
        }
    }

    private static long requireLong(Map<String, Object> args, String key) {
        Long value = optionalLong(args, key);
        if (value == null) {
            throw new AppException("Не передан обязательный параметр \"" + key + "\".");
        }
        return value;
    }

    private static int limit(Map<String, Object> args, int defaultValue) {
        Long value = optionalLong(args, "limit");
        if (value == null || value <= 0) {
            return defaultValue;
        }
        return (int) Math.min(value, 500);
    }

    private static Object bool(Map<String, Object> args, String key) {
        return args.get(key);
    }

    private static LocalDateTime dateTime(String text) {
        String normalized = text.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(normalized);
        } catch (DateTimeParseException e) {
            throw new AppException("Неверная дата и время \"" + text
                    + "\". Нужен формат ISO-8601, например 2026-10-14T14:00.");
        }
    }

    private static LocalDate dateOnly(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new AppException("Неверная дата \"" + text + "\". Нужен формат YYYY-MM-DD.");
        }
    }

    /**
     * Статус принимается и по имени перечисления (CONFIRMED), и по русскому названию
     * из интерфейса («Подтверждено») — внешний ИИ может прислать любое из них.
     */
    private static BookingStatus status(String text) {
        String wanted = text.trim();
        for (BookingStatus s : BookingStatus.values()) {
            if (s.name().equalsIgnoreCase(wanted) || s.getTitle().equalsIgnoreCase(wanted)) {
                return s;
            }
        }
        throw new AppException("Неизвестный статус \"" + text
                + "\". Допустимые: CREATED, CONFIRMED, ACTIVE, COMPLETED, CANCELLED "
                + "либо по-русски: Создано, Подтверждено, Активно, Завершено, Отменено.");
    }

    /** Как и статус, тип зоны принимается и в коде, и в русском названии. */
    private static StationType stationType(String text) {
        String wanted = text.trim();
        for (StationType t : StationType.values()) {
            if (t.name().equalsIgnoreCase(wanted) || t.getTitle().equalsIgnoreCase(wanted)) {
                return t;
            }
        }
        throw new AppException("Неизвестный тип зоны \"" + text
                + "\". Допустимые: STANDARD, VIP, BOOTCAMP, CONSOLE "
                + "либо по-русски: Стандарт, VIP, Буткемп, Консоль.");
    }

    private static Long findClientByPhone(AppServices s, String phone) {
        for (Client c : s.clients().getAll()) {
            if (phone.equals(c.getPhone())) {
                return c.getId();
            }
        }
        throw new EntityNotFoundException("Клиент с телефоном " + phone + " не найден.");
    }

    private static Long findStationByName(AppServices s, String name) {
        String wanted = name.trim().toLowerCase();
        for (Station st : s.stations().getAll()) {
            if (st.getName() != null && st.getName().trim().toLowerCase().equals(wanted)) {
                return st.getId();
            }
        }
        throw new EntityNotFoundException("Стационар с названием \"" + name + "\" не найден.");
    }

    private static List<Booking> distinct(List<Booking> source) {
        Map<Long, Booking> byId = new LinkedHashMap<>();
        for (Booking b : source) {
            if (b.getId() != null) {
                byId.putIfAbsent(b.getId(), b);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static String nullToDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }

    // ------------------------------------------------------------- JSON-схема

    private static Map<String, Object> schema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> str(String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "string");
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> num(String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "integer");
        p.put("description", description);
        return p;
    }

    private static Map<String, Object> bool(String description) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("type", "boolean");
        p.put("description", description);
        return p;
    }
}
