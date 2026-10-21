package ru.mirea.cyberclub.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.AppServices;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.model.Statistics;
import ru.mirea.cyberclub.repository.InMemoryBookingRepository;
import ru.mirea.cyberclub.repository.InMemoryClientRepository;
import ru.mirea.cyberclub.repository.InMemoryStationRepository;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.service.ExportService;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Инструменты MCP: проверяется и то, что ИИ получит понятные текстовые ответы,
 * и то, что бизнес-правила доходят до вызывающей стороны текстом, а не стектрейсом.
 *
 * <p>БД не нужна — сервисы собираются на InMemory-репозиториях, как и в остальных
 * тестах проекта; «сейчас» зафиксировано на 17.09.2026 12:00.</p>
 */
class CyberclubToolsTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);

    private static final Statistics STATS = new Statistics(
            2, 3, 2, 1,
            Map.of(BookingStatus.CREATED, 1L),
            BigDecimal.ZERO, new BigDecimal("600.00"),
            120.0, "PC-01", 1, "Shadow", 1, 0);

    private InMemoryClientRepository clientRepo;
    private InMemoryStationRepository stationRepo;
    private InMemoryBookingRepository bookingRepo;
    private AppServices services;

    private Client adult;
    private Client minor;
    private Station pc;
    private Station vip;
    private Station broken;
    private Booking booking;

    @BeforeEach
    void setUp() {
        clientRepo = new InMemoryClientRepository();
        stationRepo = new InMemoryStationRepository();
        bookingRepo = new InMemoryBookingRepository(clientRepo, stationRepo);

        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        ClientService clients = new ClientService(clientRepo, bookingRepo, clock);
        StationService stations = new StationService(stationRepo, bookingRepo);
        BookingService bookings = new BookingService(bookingRepo, clientRepo, stationRepo, clock);
        StatisticsService statistics = new StatisticsService(() -> STATS);
        services = new AppServices(clients, stations, bookings, statistics,
                new ExportService(statistics));

        adult = clientRepo.save(new Client("Shadow", "Иванов Иван", "+79160000001",
                "shadow@mail.ru", LocalDate.of(2001, 3, 15)));
        minor = clientRepo.save(new Client("Kirya", "Смирнов Кирилл", "+79160000002",
                "kirya@mail.ru", LocalDate.of(2010, 11, 5)));
        pc = stationRepo.save(new Station("PC-01", StationType.STANDARD, new BigDecimal("150.00"), null, true));
        vip = stationRepo.save(new Station("VIP-01", StationType.VIP, new BigDecimal("300.00"), null, true));
        broken = stationRepo.save(new Station("PC-02", StationType.STANDARD, new BigDecimal("150.00"), null, false));

        booking = bookings.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), "Турнир");
    }

    private static LocalDateTime tomorrow(int hour, int minute) {
        return NOW.toLocalDate().plusDays(1).atTime(hour, minute);
    }

    private CyberclubTools.Tool byName(String name) {
        return CyberclubTools.all(services).stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Инструмент " + name + " не зарегистрирован"));
    }

    private String run(String name, Map<String, Object> args) {
        return byName(name).run(args);
    }

    // ---------------------------------------------------------------- состав

    @Test
    @DisplayName("Инструменты имеют уникальные имена в змеином регистре")
    void toolNamesAreUniqueAndSnakeCase() {
        List<CyberclubTools.Tool> tools = CyberclubTools.all(services);

        assertThat(tools).isNotEmpty();
        Set<String> names = new HashSet<>();
        for (CyberclubTools.Tool tool : tools) {
            assertThat(tool.name()).matches("[a-z][a-z0-9_]*");
            assertThat(tool.description()).isNotBlank();
            assertThat(names.add(tool.name())).as("имя " + tool.name() + " не повторяется").isTrue();
        }
    }

    @Test
    @DisplayName("Каждая JSON-схема валидна: тип объекта, свойства с типом и описанием, required внутри properties")
    void inputSchemasAreValidJsonSchema() {
        for (CyberclubTools.Tool tool : CyberclubTools.all(services)) {
            Map<String, Object> schema = tool.inputSchema();
            String id = tool.name();

            assertThat(schema.get(id)).isNull();
            assertThat(schema).containsEntry("type", "object");
            assertThat(schema).containsKey("properties");

            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
            // У инструмента без параметров (get_statistics) свойств может не быть — это валидный JSON Schema.
            assertThat(properties).as("%s: properties должен быть объектом", id).isNotNull();

            Object required = schema.get("required");
            if (required != null) {
                for (Object key : (List<?>) required) {
                    assertThat(properties)
                            .as("%s: required \"%s\" нет в properties", id, key)
                            .containsKey(String.valueOf(key));
                }
            }

            for (Map.Entry<String, Object> prop : properties.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> def = (Map<String, Object>) prop.getValue();
                assertThat(def).as("%s.%s: нет типа", id, prop.getKey()).containsKey("type");
                assertThat(def).as("%s.%s: нет описания", id, prop.getKey()).containsKey("description");
                assertThat(String.valueOf(def.get("type")))
                        .as("%s.%s: недопустимый тип JSON Schema", id, prop.getKey())
                        .isIn("string", "integer", "number", "boolean", "array", "object");
            }
        }
    }

    @Test
    @DisplayName("Опубликован именно тот набор инструментов, что заявлен в задании")
    void expectedToolset() {
        assertThat(CyberclubTools.all(services).stream().map(CyberclubTools.Tool::name))
                .containsExactlyInAnyOrder(
                        "list_bookings", "get_booking", "create_booking",
                        "set_booking_status", "search_bookings",
                        "list_clients", "create_client",
                        "list_stations", "get_statistics");
    }

    // ---------------------------------------------------------------- чтение

    @Test
    @DisplayName("list_bookings без параметров возвращает брони строкой")
    void listBookingsReturnsReadableText() {
        String out = run("list_bookings", Map.of());

        assertThat(out).contains("Брони: 1").contains("#" + booking.getId())
                .contains("Shadow").contains("PC-01").contains("Создано")
                .contains("300.00").contains("Турнир");
    }

    @Test
    @DisplayName("list_bookings фильтрует по статусу и по дате")
    void listBookingsFilters() {
        assertThat(run("list_bookings", Map.of("status", "created")))
                .contains("Брони: 1");

        assertThat(run("list_bookings", Map.of("status", "CANCELLED")))
                .contains("ничего не найдено");

        assertThat(run("list_bookings", Map.of("date", tomorrow(0, 0).toLocalDate().toString())))
                .contains("Брони: 1");

        assertThat(run("list_bookings", Map.of("date", "2027-01-01")))
                .contains("ничего не найдено");
    }

    @Test
    @DisplayName("list_bookings обрезает ответ по limit, а не печатает всё подряд")
    void listBookingsRespectsLimit() {
        String out = run("list_bookings", Map.of("limit", 1));

        assertThat(out).contains("показаны первые 1");
    }

    @Test
    @DisplayName("get_booking отдаёт подробную карточку")
    void getBookingShowsDetails() {
        String out = run("get_booking", Map.of("id", booking.getId()));

        assertThat(out)
                .contains("Идентификатор: " + booking.getId())
                .contains("Shadow")
                .contains("Создано")
                .contains("300.00")
                .contains("Турнир");
    }

    @Test
    @DisplayName("get_booking без id сообщает, какой параметр пропущен")
    void getBookingRequiresId() {
        assertThatThrownBy(() -> run("get_booking", Map.of()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("get_booking на несуществующий id возвращает понятное сообщение")
    void getBookingUnknownId() {
        assertThatThrownBy(() -> run("get_booking", Map.of("id", 999_999L)))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не существует");
    }

    @Test
    @DisplayName("get_booking принимает id, переданный строкой (так JSON-клиенты часто шлют числа)")
    void getBookingAcceptsStringId() {
        assertThat(run("get_booking", Map.of("id", String.valueOf(booking.getId()))))
                .contains("Идентификатор: " + booking.getId());
    }

    @Test
    @DisplayName("list_clients ищет по нику")
    void listClientsSearches() {
        assertThat(run("list_clients", Map.of())).contains("Shadow").contains("Kirya");
        assertThat(run("list_clients", Map.of("query", "kir"))).contains("Kirya").doesNotContain("Shadow");
        assertThat(run("list_clients", Map.of("query", "нет такого"))).contains("ничего не найдено");
    }

    @Test
    @DisplayName("list_stations фильтрует по типу и отделяет отключённые")
    void listStationsFilters() {
        assertThat(run("list_stations", Map.of())).contains("PC-01").contains("VIP-01");
        assertThat(run("list_stations", Map.of("type", "VIP"))).contains("VIP-01").doesNotContain("PC-01");
        assertThat(run("list_stations", Map.of("type", "консоль"))).contains("ничего не найдено");
        assertThat(run("list_stations", Map.of("activeOnly", true)))
                .contains("активен").doesNotContain("отключён");
    }

    @Test
    @DisplayName("get_statistics выводит сводку из сервиса статистики")
    void getStatisticsShowsIndicators() {
        String out = run("get_statistics", Map.of());

        assertThat(out).contains("Статистика клуба")
                .contains("Всего клиентов: 2")
                .contains("Выручка по завершённым броням");
    }

    @Test
    @DisplayName("search_bookings находит бронь по нику клиента")
    void searchBookingsByNickname() {
        assertThat(run("search_bookings", Map.of("query", "Shadow")))
                .contains("#" + booking.getId());
        assertThat(run("search_bookings", Map.of("query", "Турнир")))
                .contains("#" + booking.getId());
        assertThat(run("search_bookings", Map.of("query", "abc")))
                .contains("ничего не найдено");
    }

    @Test
    @DisplayName("search_bookings требует текст запроса")
    void searchBookingsRequiresQuery() {
        assertThatThrownBy(() -> run("search_bookings", Map.of()))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("query");
    }

    // ---------------------------------------------------------------- запись

    @Test
    @DisplayName("create_booking по id клиента и стационара создаёт бронь")
    void createBookingByIds() {
        String out = run("create_booking", Map.of(
                "clientId", adult.getId(),
                "stationId", vip.getId(),
                "startTime", tomorrow(18, 0).toString(),
                "endTime", tomorrow(20, 0).toString(),
                "comment", "Приглашение от ИИ"));

        assertThat(out).contains("Бронь создана").contains("VIP-01").contains("Приглашение от ИИ");
        assertThat(bookingRepo.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("create_booking принимает телефон клиента вместо его id")
    void createBookingByPhone() {
        String out = run("create_booking", Map.of(
                "clientPhone", "+79160000001",
                "stationId", vip.getId(),
                "startTime", tomorrow(21, 0).toString(),
                "endTime", tomorrow(22, 0).toString()));

        assertThat(out).contains("Бронь создана").contains("Shadow");
    }

    @Test
    @DisplayName("create_booking принимает название стационара вместо его id")
    void createBookingByStationName() {
        String out = run("create_booking", Map.of(
                "clientId", adult.getId(),
                "stationName", "VIP-01",
                "startTime", tomorrow(8, 0).toString(),
                "endTime", tomorrow(9, 0).toString()));

        assertThat(out).contains("Бронь создана").contains("VIP-01");
    }

    @Test
    @DisplayName("create_booking отклоняет занятый стационар текстом, а не стектрейсом")
    void createBookingRejectsOverlap() {
        assertThatThrownBy(() -> run("create_booking", Map.of(
                "clientId", adult.getId(),
                "stationId", pc.getId(),
                "startTime", tomorrow(15, 0).toString(),
                "endTime", tomorrow(17, 0).toString())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("занято");
    }

    @Test
    @DisplayName("create_booking отклоняет бронь в прошлом")
    void createBookingRejectsPast() {
        assertThatThrownBy(() -> run("create_booking", Map.of(
                "clientId", adult.getId(),
                "stationId", pc.getId(),
                "startTime", "2026-09-01T14:00",
                "endTime", "2026-09-01T16:00")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("прошлом");
    }

    @Test
    @DisplayName("create_booking отклоняет бронь на отключённое место")
    void createBookingRejectsInactiveStation() {
        assertThatThrownBy(() -> run("create_booking", Map.of(
                "clientId", adult.getId(),
                "stationId", broken.getId(),
                "startTime", tomorrow(9, 0).toString(),
                "endTime", tomorrow(10, 0).toString())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("обслуживании");
    }

    @Test
    @DisplayName("create_booking с телефоном неизвестного клиента сообщает об этом")
    void createBookingUnknownPhone() {
        assertThatThrownBy(() -> run("create_booking", Map.of(
                "clientPhone", "+79990000000",
                "stationId", pc.getId(),
                "startTime", tomorrow(9, 0).toString(),
                "endTime", tomorrow(10, 0).toString())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("create_booking без обязательных дат — понятная ошибка")
    void createBookingRequiresTimes() {
        assertThatThrownBy(() -> run("create_booking", Map.of(
                "clientId", adult.getId(), "stationId", pc.getId())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("startTime");
    }

    @Test
    @DisplayName("create_booking сообщает о неверном формате даты")
    void createBookingRejectsBadDateFormat() {
        assertThatThrownBy(() -> run("create_booking", Map.of(
                "clientId", adult.getId(),
                "stationId", pc.getId(),
                "startTime", "завтра в два",
                "endTime", tomorrow(16, 0).toString())))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("2026");
    }

    @Test
    @DisplayName("set_booking_status переводит CREATED → CONFIRMED")
    void setBookingStatusTransitions() {
        String out = run("set_booking_status", Map.of("id", booking.getId(), "status", "confirmed"));

        assertThat(out).contains("Статус обновлён").contains("Подтверждено");
        assertThat(bookingRepo.findById(booking.getId())).hasValueSatisfying(
                b -> assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED));
    }

    @Test
    @DisplayName("set_booking_status отклоняет недопустимый переход машиной состояний")
    void setBookingStatusRejectsIllegalTransition() {
        assertThatThrownBy(() -> run("set_booking_status",
                Map.of("id", booking.getId(), "status", "COMPLETED")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("запрещён")
                .hasMessageContaining("Подтверждено");
    }

    @Test
    @DisplayName("set_booking_status сообщает о неизвестном статусе")
    void setBookingStatusUnknownStatus() {
        assertThatThrownBy(() -> run("set_booking_status",
                Map.of("id", booking.getId(), "status", "НеСтатус")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    @DisplayName("create_client принимает корректную карточку")
    void createClientSucceeds() {
        String out = run("create_client", Map.of(
                "nickname", "Neo",
                "fullName", "Морфеус Томас",
                "phone", "+79160000009",
                "email", "neo@mail.ru",
                "birthDate", "1990-02-03"));

        assertThat(out).contains("Клиент создан").contains("Neo");
        assertThat(clientRepo.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("create_client отклоняет некорректный телефон")
    void createClientRejectsBadPhone() {
        assertThatThrownBy(() -> run("create_client", Map.of(
                "nickname", "Neo",
                "fullName", "Морфеус Томас",
                "phone", "8916",
                "email", "neo@mail.ru",
                "birthDate", "1990-02-03")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("+7XXXXXXXXXX");
    }

    @Test
    @DisplayName("create_client отклоняет клиента младше 12 лет")
    void createClientRejectsMinor() {
        assertThatThrownBy(() -> run("create_client", Map.of(
                "nickname", "Kid",
                "fullName", "Малыш Малышев",
                "phone", "+79160000010",
                "email", "kid@mail.ru",
                "birthDate", "2025-01-01")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не младше");
    }

    @Test
    @DisplayName("create_client сообщает о пропущенном обязательном поле")
    void createClientRequiresFields() {
        assertThatThrownBy(() -> run("create_client", Map.of("nickname", "Neo")))
                .isInstanceOf(AppException.class);
    }

    // ---------------------------------------------------------------- разбор

    @Test
    @DisplayName("Неизвестный тип зоны перечисляет допустимые")
    void unknownStationTypeListsOptions() {
        assertThatThrownBy(() -> run("list_stations", Map.of("type", "sparta")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("BOOTCAMP");
    }

    @Test
    @DisplayName("Нечисловой id — понятная ошибка, а не NumberFormatException")
    void nonNumericIdGivesClearMessage() {
        assertThatThrownBy(() -> run("get_booking", Map.of("id", "abc")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("числом")
                .isNotInstanceOf(NumberFormatException.class);
    }

    @Test
    @DisplayName("minor используется в тесте — данные действительно загружены")
    void fixtureIsLoaded() {
        assertThat(minor).isNotNull();
        assertThat(clientRepo.findAll()).hasSize(2);
        assertThat(stationRepo.findAll()).hasSize(3);
    }
}
