package ru.mirea.cyberclub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.InMemoryBookingRepository;
import ru.mirea.cyberclub.repository.InMemoryClientRepository;
import ru.mirea.cyberclub.repository.InMemoryStationRepository;

/**
 * Валидация и правила для игровых мест.
 */
class StationServiceTest {

    private InMemoryClientRepository clients;
    private InMemoryStationRepository stations;
    private InMemoryBookingRepository bookings;
    private StationService service;

    @BeforeEach
    void setUp() {
        clients = new InMemoryClientRepository();
        stations = new InMemoryStationRepository();
        bookings = new InMemoryBookingRepository(clients, stations);
        service = new StationService(stations, bookings);
    }

    @Test
    @DisplayName("Название приводится к верхнему регистру, тариф положительный, имя уникально")
    void createAndValidate() {
        Station saved = service.create(new Station("pc-01", StationType.STANDARD, new BigDecimal("150"), " ", true));
        assertEquals("PC-01", saved.getName());
        assertNull(saved.getSpecs());

        assertThrows(ValidationException.class,
                () -> service.create(new Station("PC-01", StationType.VIP, new BigDecimal("300"), null, true)));
        assertThrows(ValidationException.class,
                () -> service.create(new Station("PC 02", StationType.VIP, new BigDecimal("300"), null, true)));
        assertThrows(ValidationException.class,
                () -> service.create(new Station("PC-02", StationType.VIP, BigDecimal.ZERO, null, true)));
        assertThrows(ValidationException.class,
                () -> service.create(new Station("PC-02", null, new BigDecimal("300"), null, true)));
    }

    @Test
    @DisplayName("Место с бронированиями удалить нельзя, но можно перевести на обслуживание")
    void deleteRestriction() {
        Station station = service.create(new Station("PC-01", StationType.STANDARD, new BigDecimal("150"), null, true));
        Client client = clients.save(new Client("Shadow", "Иванов Иван", "+79160000001", "s@mail.ru", java.time.LocalDate.of(2001, 1, 1)));
        bookings.save(new Booking(client.getId(), station.getId(), LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).plusHours(1), null));

        assertThrows(BusinessRuleException.class, () -> service.delete(station.getId()));

        assertFalse(service.setActive(station.getId(), false).isActive());
        assertEquals(0, service.getActive().size());
    }
}
