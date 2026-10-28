package ru.mirea.cyberclub.mcp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import ru.mirea.cyberclub.AppServices;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
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
 * <p>Данные лежат за портом {@link ClubApi}: один и тот же набор инструментов работает и
 * над прямым подключением к БД, и над REST API КР3 — меняется только адаптер.</p>
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

    /** Весь набор инструментов поверх сервисов КР2 — режим по умолчанию. */
    public static List<Tool> all(AppServices services) {
        return all(new JdbcClubApi(services));
    }

    /** Весь набор инструментов поверх произвольного источника данных. */
    public static List<Tool> all(ClubApi api) {
        List<Tool> tools = new ArrayList<>();
        tools.add(listBookings(api));
        tools.add(getBooking(api));
        tools.add(createBooking(api));
        tools.add(setBookingStatus(api));
        tools.add(findFreeSlots(api));
        tools.add(rescheduleBooking(api));
        tools.add(searchBookings(api));
        tools.add(listClients(api));
        tools.add(getClient(api));
        tools.add(createClient(api));
        tools.add(updateClient(api));
        tools.add(deleteClient(api));
        tools.add(listStations(api));
        tools.add(getStatistics(api));
        return tools;
    }

    // ----------------------------------------------------------------- чтение

    private static Tool listBookings(ClubApi api) {
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
                        bookings = api.bookingsByStatus(status(status));
                    } else if (date != null) {
                        bookings = api.bookingsByDate(dateOnly(date));
                    } else if (from != null || to != null) {
                        LocalDateTime f = from == null ? LocalDateTime.MIN : dateTime(from);
                        LocalDateTime t = to == null ? LocalDateTime.MAX : dateTime(to);
                        bookings = api.bookingsByPeriod(f, t);
                    } else {
                        bookings = api.allBookings();
                    }
                    return bookingsView(bookings, limit, "Брони");
                });
    }

    private static Tool getBooking(ClubApi api) {
        return new Tool("get_booking",
                "Подробная карточка одной брони по её идентификатору.",
                schema(Map.of("id", num("Идентификатор брони")), "id"),
                args -> {
                    long id = requireLong(args, "id");
                    return bookingView(api.booking(id));
                });
    }

    private static Tool searchBookings(ClubApi api) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", str("Текст поиска: ник клиента, название стационара или комментарий"));
        return new Tool("search_bookings",
                "Поиск броней по нику клиента, названию стационара или комментарию.",
                schema(props, "query"),
                args -> {
                    String query = requireText(args, "query");
                    return bookingsView(api.searchBookings(query), 50, "Найденные брони");
                });
    }

    private static Tool listClients(ClubApi api) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("query", str("Строка для поиска по нику, ФИО, телефону или email"));
        return new Tool("list_clients",
                "Список клиентов клуба; с параметром query — поиск среди них.",
                schema(props),
                args -> {
                    String query = str(args, "query");
                    List<Client> clients = query == null ? api.allClients() : api.searchClients(query);
                    return clientsView(clients, 50);
                });
    }

    private static Tool listStations(ClubApi api) {
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
                        stations = api.stationsByType(stationType(type));
                    } else if (activeOnly) {
                        stations = api.activeStations();
                    } else {
                        stations = api.allStations();
                    }
                    return stationsView(stations, activeOnly);
                });
    }

    private static Tool getStatistics(ClubApi api) {
        return new Tool("get_statistics",
                "Сводная статистика клуба: клиенты, стационары, брони по статусам, выручка, "
                        + "лучшие клиенты и стационары, брони на сегодня.",
                schema(Map.of()),
                args -> api.statisticsText());
    }

    // ----------------------------------------------------------------- запись

    private static Tool createBooking(ClubApi api) {
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
                        clientId = findClientByPhone(api, phone);
                    }
                    Long stationId = optionalLong(args, "stationId");
                    if (stationId == null) {
                        stationId = findStationByName(api, requireText(args, "stationName"));
                    }
                    Booking created = api.createBooking(
                            clientId, stationId,
                            dateTime(requireText(args, "startTime")),
                            dateTime(requireText(args, "endTime")),
                            str(args, "comment"));
                    return "Бронь создана:\n" + bookingView(created);
                });
    }

    private static Tool setBookingStatus(ClubApi api) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", num("Идентификатор брони"));
        props.put("status", str("Новый статус: CONFIRMED, ACTIVE, COMPLETED или CANCELLED"));
        return new Tool("set_booking_status",
                "Изменить статус брони по машине состояний: CREATED → CONFIRMED/CANCELLED, "
                        + "CONFIRMED → ACTIVE/CANCELLED, ACTIVE → COMPLETED. Недопустимый переход будет отклонён.",
                schema(props, "id", "status"),
                args -> {
                    Booking updated = api.changeBookingStatus(
                            requireLong(args, "id"), status(requireText(args, "status")));
                    return "Статус обновлён:\n" + bookingView(updated);
                });
    }

    private static Tool findFreeSlots(ClubApi api) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("startTime", str("Начало интервала, ISO-дата и время: 2026-10-14T18:00"));
        props.put("endTime", str("Конец интервала, ISO-дата и время"));
        props.put("zone", str("Только эта зона: STANDARD, VIP, BOOTCAMP или CONSOLE"));
        return new Tool("find_free_slots",
                "Показать, какие игровые места свободны на интервал, а какие заняты и какой "
                        + "бронью. Нужен, чтобы разрешить конфликт занятости: подобрать другое "
                        + "место или время, а не создавать бронь наугад.",
                schema(props, "startTime", "endTime"),
                args -> {
                    LocalDateTime start = dateTime(requireText(args, "startTime"));
                    LocalDateTime end = dateTime(requireText(args, "endTime"));
                    if (!end.isAfter(start)) {
                        throw new AppException("Конец интервала должен быть позже начала.");
                    }
                    String zoneText = str(args, "zone");
                    StationType zone = zoneText == null ? null : stationType(zoneText);

                    List<Station> candidates = zone == null
                            ? api.activeStations()
                            : api.stationsByType(zone);
                    Set<Long> candidateIds = new HashSet<>();
                    for (Station s : candidates) {
                        candidateIds.add(s.getId());
                    }
                    List<Booking> occupied = api.allBookings().stream()
                            .filter(b -> candidateIds.contains(b.getStationId()))
                            .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                            .filter(b -> b.getStartTime().isBefore(end) && b.getEndTime().isAfter(start))
                            .toList();
                    Set<Long> busyStations = new HashSet<>();
                    for (Booking b : occupied) {
                        busyStations.add(b.getStationId());
                    }
                    List<Station> free = candidates.stream()
                            .filter(Station::isActive)
                            .filter(s -> !busyStations.contains(s.getId()))
                            .toList();
                    return freeSlotsView(start, end, zone, free, occupied);
                });
    }

    private static Tool rescheduleBooking(ClubApi api) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", num("Идентификатор брони (см. list_bookings)"));
        props.put("startTime", str("Новое начало, ISO-дата и время"));
        props.put("endTime", str("Новое окончание, ISO-дата и время"));
        props.put("stationId", num("Новое игровое место (см. list_stations), необязательно"));
        props.put("stationName", str("Название нового места — альтернатива stationId"));
        return new Tool("reschedule_booking",
                "Перенести бронь на другое время и/или место, не создавая новую. Клиент не "
                        + "меняется. Так разрешается конфликт занятости: сначала find_free_slots, "
                        + "затем этот инструмент.",
                schema(props, "id", "startTime", "endTime"),
                args -> {
                    long id = requireLong(args, "id");
                    Booking current = api.booking(id);
                    Long stationId = optionalLong(args, "stationId");
                    if (stationId == null) {
                        String stationName = str(args, "stationName");
                        stationId = stationName == null
                                ? current.getStationId()
                                : findStationByName(api, stationName);
                    }
                    Booking moved = api.rescheduleBooking(id, stationId,
                            dateTime(requireText(args, "startTime")),
                            dateTime(requireText(args, "endTime")),
                            current.getComment());
                    return "Бронь перенесена:\n" + bookingView(moved);
                });
    }

    private static Tool createClient(ClubApi api) {
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
                    Client saved = api.createClient(client);
                    return "Клиент создан:\n" + clientView(saved);
                });
    }

    private static Tool getClient(ClubApi api) {
        return new Tool("get_client",
                "Показать карточку клиента по идентификатору: ник, ФИО, телефон, email "
                        + "и дату рождения.",
                schema(Map.of("id", num("Идентификатор клиента (см. list_clients)")), "id"),
                args -> {
                    Client client = api.client(requireLong(args, "id"));
                    return "Карточка клиента:\n" + clientView(client);
                });
    }

    private static Tool updateClient(ClubApi api) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("id", num("Идентификатор клиента (см. list_clients)"));
        props.put("nickname", str("Новый ник: 3-20 символов, латиница, цифры и подчёркивание"));
        props.put("fullName", str("Новое ФИО"));
        props.put("phone", str("Новый телефон в формате +7XXXXXXXXXX"));
        props.put("email", str("Новый email"));
        props.put("birthDate", str("Новая дата рождения в формате YYYY-MM-DD"));
        return new Tool("update_client",
                "Изменить карточку клиента. Меняются только переданные поля, остальные "
                        + "сохраняются как были. Проверяет формат полей, уникальность "
                        + "ника, телефона и email, а также возраст не младше 12 лет.",
                schema(props, "id"),
                args -> {
                    long id = requireLong(args, "id");
                    Client client = api.client(id);

                    boolean changed = false;
                    String nickname = str(args, "nickname");
                    if (nickname != null) {
                        client.setNickname(nickname);
                        changed = true;
                    }
                    String fullName = str(args, "fullName");
                    if (fullName != null) {
                        client.setFullName(fullName);
                        changed = true;
                    }
                    String phone = str(args, "phone");
                    if (phone != null) {
                        client.setPhone(phone);
                        changed = true;
                    }
                    String email = str(args, "email");
                    if (email != null) {
                        client.setEmail(email);
                        changed = true;
                    }
                    String birthDate = str(args, "birthDate");
                    if (birthDate != null) {
                        client.setBirthDate(dateOnly(birthDate));
                        changed = true;
                    }
                    if (!changed) {
                        throw new AppException("Не передано ни одного поля для изменения: "
                                + "nickname, fullName, phone, email или birthDate.");
                    }
                    return "Клиент обновлён:\n" + clientView(api.updateClient(client));
                });
    }

    private static Tool deleteClient(ClubApi api) {
        return new Tool("delete_client",
                "Удалить карточку клиента. Удалить можно только клиента без бронирований: "
                        + "история броней — основа статистики и отчётности. Если клиент "
                        + "ещё ходит в клуб, удаление запрещено правилом бизнес-логики.",
                schema(Map.of("id", num("Идентификатор клиента (см. list_clients)")), "id"),
                args -> {
                    long id = requireLong(args, "id");
                    api.deleteClient(id);
                    return "Клиент #" + id + " удалён.";
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

    private static String freeSlotsView(LocalDateTime start, LocalDateTime end, StationType zone,
                                        List<Station> free, List<Booking> occupied) {
        String range = start + " — " + end + (zone == null ? "" : " (" + zone.getTitle() + ")");
        StringBuilder out = new StringBuilder();
        if (free.isEmpty()) {
            out.append("Свободных мест на ").append(range).append(" нет.\n");
        } else {
            out.append("Свободны на ").append(range).append(": ").append(free.size()).append('\n');
            for (Station s : free) {
                out.append("  #").append(s.getId()).append(" | ").append(s.getName())
                        .append(" | ").append(s.getType().getTitle())
                        .append(" | ").append(s.getHourlyRate()).append(" ₽/ч\n");
            }
        }
        if (!occupied.isEmpty()) {
            out.append("Заняты в это время:\n");
            for (Booking b : occupied) {
                out.append("  ").append(bookingOneLine(b)).append('\n');
            }
        }
        return out.toString().stripTrailing();
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

    private static Long findClientByPhone(ClubApi api, String phone) {
        for (Client c : api.allClients()) {
            if (phone.equals(c.getPhone())) {
                return c.getId();
            }
        }
        throw new EntityNotFoundException("Клиент с телефоном " + phone + " не найден.");
    }

    private static Long findStationByName(ClubApi api, String name) {
        String wanted = name.trim().toLowerCase();
        for (Station st : api.allStations()) {
            if (st.getName() != null && st.getName().trim().toLowerCase().equals(wanted)) {
                return st.getId();
            }
        }
        throw new EntityNotFoundException("Стационар с названием \"" + name + "\" не найден.");
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
