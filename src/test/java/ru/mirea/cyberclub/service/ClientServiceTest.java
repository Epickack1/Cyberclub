package ru.mirea.cyberclub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.InMemoryBookingRepository;
import ru.mirea.cyberclub.repository.InMemoryClientRepository;
import ru.mirea.cyberclub.repository.InMemoryStationRepository;

/**
 * Валидация и бизнес-правила для клиентов.
 */
class ClientServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);

    private InMemoryClientRepository clients;
    private InMemoryStationRepository stations;
    private InMemoryBookingRepository bookings;
    private ClientService service;

    @BeforeEach
    void setUp() {
        clients = new InMemoryClientRepository();
        stations = new InMemoryStationRepository();
        bookings = new InMemoryBookingRepository(clients, stations);
        service = new ClientService(clients, bookings, Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE));
    }

    private static Client valid() {
        return new Client("Shadow", "Иванов Иван Иванович", "+79161234567", "shadow@mail.ru", LocalDate.of(2001, 3, 15));
    }

    @Test
    @DisplayName("Корректный клиент сохраняется, поля нормализуются")
    void createValid() {
        Client client = valid();
        client.setNickname("  Shadow ");
        client.setEmail("Shadow@Mail.RU");
        client.setPhone("+7 (916) 123-45-67");

        Client saved = service.create(client);
        assertEquals(1L, saved.getId());
        assertEquals("Shadow", saved.getNickname());
        assertEquals("shadow@mail.ru", saved.getEmail());
        assertEquals("+79161234567", saved.getPhone());
    }

    @Test
    @DisplayName("Обязательные поля и форматы: ник, ФИО, телефон, email, дата рождения")
    void validation() {
        Client noNick = valid();
        noNick.setNickname("  ");
        assertThrows(ValidationException.class, () -> service.create(noNick));

        Client badNick = valid();
        badNick.setNickname("Тень!");
        assertThrows(ValidationException.class, () -> service.create(badNick));

        Client noName = valid();
        noName.setFullName(null);
        assertThrows(ValidationException.class, () -> service.create(noName));

        Client badPhone = valid();
        badPhone.setPhone("89161234567");
        assertThrows(ValidationException.class, () -> service.create(badPhone));

        Client badEmail = valid();
        badEmail.setEmail("shadow.mail.ru");
        assertThrows(ValidationException.class, () -> service.create(badEmail));

        Client futureBirth = valid();
        futureBirth.setBirthDate(LocalDate.of(2030, 1, 1));
        assertThrows(ValidationException.class, () -> service.create(futureBirth));
    }

    @Test
    @DisplayName("Клиент младше 12 лет не регистрируется")
    void minimumAge() {
        Client child = valid();
        child.setBirthDate(LocalDate.of(2016, 1, 1));
        BusinessRuleException e = assertThrows(BusinessRuleException.class, () -> service.create(child));
        assertTrue(e.getMessage().contains("12"));
    }

    @Test
    @DisplayName("Ник, телефон и email уникальны (без учёта регистра)")
    void uniqueness() {
        service.create(valid());

        Client sameNick = valid();
        sameNick.setNickname("shadow");
        sameNick.setPhone("+79160000000");
        sameNick.setEmail("other@mail.ru");
        assertThrows(ValidationException.class, () -> service.create(sameNick));

        Client samePhone = valid();
        samePhone.setNickname("Other");
        samePhone.setEmail("other@mail.ru");
        assertThrows(ValidationException.class, () -> service.create(samePhone));

        Client sameEmail = valid();
        sameEmail.setNickname("Other");
        sameEmail.setPhone("+79160000000");
        assertThrows(ValidationException.class, () -> service.create(sameEmail));
    }

    @Test
    @DisplayName("При обновлении клиент не конфликтует сам с собой")
    void updateKeepsOwnValues() {
        Client saved = service.create(valid());
        saved.setFullName("Иванов Иван Петрович");
        Client updated = service.update(saved);
        assertEquals("Иванов Иван Петрович", updated.getFullName());

        Client missing = valid();
        missing.setId(42L);
        assertThrows(EntityNotFoundException.class, () -> service.update(missing));
    }

    @Test
    @DisplayName("Клиента с незавершёнными бронями удалить нельзя, с завершёнными — можно")
    void deleteWithBookings() {
        Client client = service.create(valid());
        Station station = stations.save(new Station("PC-01", StationType.STANDARD, new BigDecimal("150"), null, true));
        Booking booking = new Booking(client.getId(), station.getId(), NOW.plusDays(1), NOW.plusDays(1).plusHours(2), null);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookings.save(booking);

        assertThrows(BusinessRuleException.class, () -> service.delete(client.getId()));

        booking.setStatus(BookingStatus.COMPLETED);
        bookings.update(booking);
        service.delete(client.getId());
        assertEquals(0, clients.count());

        assertThrows(EntityNotFoundException.class, () -> service.delete(client.getId()));
    }

    @Test
    @DisplayName("Поиск с пустой строкой отклоняется")
    void emptySearch() {
        assertThrows(ValidationException.class, () -> service.search(" "));
    }
}
