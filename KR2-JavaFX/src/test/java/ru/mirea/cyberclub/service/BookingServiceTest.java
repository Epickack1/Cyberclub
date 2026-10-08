package ru.mirea.cyberclub.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
 * Бизнес-правила бронирования. «Сейчас» зафиксировано через Clock:
 * 17.09.2026 12:00, чтобы тесты не зависели от реального времени.
 */
class BookingServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 17, 12, 0);

    private InMemoryClientRepository clients;
    private InMemoryStationRepository stations;
    private InMemoryBookingRepository bookings;
    private BookingService service;

    private Client adult;
    private Client minor;
    private Station pc;
    private Station vip;
    private Station broken;

    @BeforeEach
    void setUp() {
        clients = new InMemoryClientRepository();
        stations = new InMemoryStationRepository();
        bookings = new InMemoryBookingRepository(clients, stations);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new BookingService(bookings, clients, stations, clock);

        adult = clients.save(new Client("Shadow", "Иванов Иван", "+79160000001", "shadow@mail.ru", LocalDate.of(2001, 3, 15)));
        minor = clients.save(new Client("Kirya", "Смирнов Кирилл", "+79160000002", "kirya@mail.ru", LocalDate.of(2010, 11, 5)));
        pc = stations.save(new Station("PC-01", StationType.STANDARD, new BigDecimal("150.00"), null, true));
        vip = stations.save(new Station("VIP-01", StationType.VIP, new BigDecimal("300.00"), null, true));
        broken = stations.save(new Station("PC-02", StationType.STANDARD, new BigDecimal("150.00"), null, false));
    }

    private static LocalDateTime tomorrow(int hour, int minute) {
        return NOW.toLocalDate().plusDays(1).atTime(hour, minute);
    }

    @Test
    @DisplayName("Корректная бронь создаётся со статусом CREATED и рассчитанной стоимостью")
    void createValidBooking() {
        Booking booking = service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), " Турнир ");

        assertEquals(BookingStatus.CREATED, booking.getStatus());
        assertEquals(new BigDecimal("300.00"), booking.getTotalPrice());
        assertEquals("Турнир", booking.getComment());
        assertEquals("Shadow", booking.getClientNickname());
        assertEquals(1, bookings.count());
    }

    @Test
    @DisplayName("Правило 9: стоимость округляется вверх до 30 минут")
    void priceRoundsUpToHalfHour() {
        // 1 ч 40 мин -> 2 ч * 150 = 300
        assertEquals(new BigDecimal("300.00"), service.calculatePrice(pc, tomorrow(14, 0), tomorrow(15, 40)));
        // 1 ч 30 мин -> 1.5 ч * 150 = 225
        assertEquals(new BigDecimal("225.00"), service.calculatePrice(pc, tomorrow(14, 0), tomorrow(15, 30)));
        // 4 ч на VIP по 300 = 1200
        assertEquals(new BigDecimal("1200.00"), service.calculatePrice(vip, tomorrow(18, 0), tomorrow(22, 0)));
    }

    @Test
    @DisplayName("Правило 1: несуществующий клиент или место -> EntityNotFoundException")
    void unknownClientOrStation() {
        assertThrows(EntityNotFoundException.class,
                () -> service.create(999L, pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null));
        assertThrows(EntityNotFoundException.class,
                () -> service.create(adult.getId(), 999L, tomorrow(14, 0), tomorrow(16, 0), null));
    }

    @Test
    @DisplayName("Правило 2: место на обслуживании бронировать нельзя")
    void inactiveStation() {
        BusinessRuleException e = assertThrows(BusinessRuleException.class,
                () -> service.create(adult.getId(), broken.getId(), tomorrow(14, 0), tomorrow(16, 0), null));
        assertTrue(e.getMessage().contains("обслуживании"));
    }

    @Test
    @DisplayName("Правило 3: конец раньше начала, слишком короткая/длинная бронь, бронь в прошлом")
    void invalidInterval() {
        assertThrows(ValidationException.class,
                () -> service.create(adult.getId(), pc.getId(), tomorrow(16, 0), tomorrow(14, 0), null));
        assertThrows(BusinessRuleException.class,
                () -> service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(14, 20), null));
        assertThrows(BusinessRuleException.class,
                () -> service.create(adult.getId(), pc.getId(), tomorrow(8, 0), tomorrow(20, 30), null));
        assertThrows(BusinessRuleException.class,
                () -> service.create(adult.getId(), pc.getId(), NOW.minusHours(2), NOW.minusHours(1), null));
    }

    @Test
    @DisplayName("Правило 4: пересекающиеся брони на одном месте запрещены, смежные — разрешены")
    void overlappingBookings() {
        service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);

        assertThrows(BusinessRuleException.class,
                () -> service.create(adult.getId(), pc.getId(), tomorrow(15, 0), tomorrow(17, 0), null));
        assertThrows(BusinessRuleException.class,
                () -> service.create(adult.getId(), pc.getId(), tomorrow(13, 0), tomorrow(17, 0), null));

        // бронь встык (16:00-18:00) и бронь на другом месте не конфликтуют
        service.create(adult.getId(), pc.getId(), tomorrow(16, 0), tomorrow(18, 0), null);
        service.create(adult.getId(), vip.getId(), tomorrow(15, 0), tomorrow(17, 0), null);
        assertEquals(3, bookings.count());
    }

    @Test
    @DisplayName("Правило 4: отменённая бронь место не занимает")
    void cancelledBookingDoesNotBlock() {
        Booking first = service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);
        service.changeStatus(first.getId(), BookingStatus.CANCELLED);

        Booking second = service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);
        assertEquals(BookingStatus.CREATED, second.getStatus());
    }

    @Test
    @DisplayName("Правило 5: переходы статусов только по схеме")
    void statusTransitions() {
        Booking booking = service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(booking.getId(), BookingStatus.ACTIVE));
        assertThrows(BusinessRuleException.class, () -> service.changeStatus(booking.getId(), BookingStatus.COMPLETED));

        assertEquals(BookingStatus.CONFIRMED, service.changeStatus(booking.getId(), BookingStatus.CONFIRMED).getStatus());
        assertEquals(BookingStatus.ACTIVE, service.changeStatus(booking.getId(), BookingStatus.ACTIVE).getStatus());
        assertEquals(BookingStatus.COMPLETED, service.changeStatus(booking.getId(), BookingStatus.COMPLETED).getStatus());

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(booking.getId(), BookingStatus.CANCELLED));
    }

    @Test
    @DisplayName("Правило 6: завершённую бронь нельзя изменять, активную — удалять")
    void editAndDeleteRestrictions() {
        Booking booking = service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);
        service.changeStatus(booking.getId(), BookingStatus.CONFIRMED);
        service.changeStatus(booking.getId(), BookingStatus.ACTIVE);

        assertThrows(BusinessRuleException.class, () -> service.delete(booking.getId()));
        assertThrows(BusinessRuleException.class,
                () -> service.update(booking.getId(), null, tomorrow(15, 0), tomorrow(17, 0), null));

        service.changeStatus(booking.getId(), BookingStatus.COMPLETED);
        service.delete(booking.getId());
        assertEquals(0, bookings.count());
    }

    @Test
    @DisplayName("Правило 7: несовершеннолетним недоступны VIP-зона и ночное время")
    void ageRestrictions() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(minor.getId(), vip.getId(), tomorrow(14, 0), tomorrow(16, 0), null));
        assertThrows(BusinessRuleException.class,
                () -> service.create(minor.getId(), pc.getId(), tomorrow(21, 0), tomorrow(23, 0), null));
        assertThrows(BusinessRuleException.class,
                () -> service.create(minor.getId(), pc.getId(), tomorrow(4, 0), tomorrow(6, 0), null));

        // днём на стандартном месте — можно; взрослому ночью — можно
        service.create(minor.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);
        service.create(adult.getId(), vip.getId(), tomorrow(21, 0), tomorrow(23, 0), null);
        assertEquals(2, bookings.count());
    }

    @Test
    @DisplayName("Определение ночного интервала")
    void touchesNight() {
        assertTrue(BookingService.touchesNight(tomorrow(21, 0), tomorrow(23, 0)));
        assertTrue(BookingService.touchesNight(tomorrow(23, 0), tomorrow(23, 30)));
        assertTrue(BookingService.touchesNight(tomorrow(5, 0), tomorrow(7, 0)));
        assertTrue(BookingService.touchesNight(tomorrow(10, 0), tomorrow(22, 30)));
        assertFalse(BookingService.touchesNight(tomorrow(6, 0), tomorrow(22, 0)));
        assertFalse(BookingService.touchesNight(tomorrow(14, 0), tomorrow(16, 0)));
    }

    @Test
    @DisplayName("Изменение брони пересчитывает стоимость и проверяет пересечения")
    void updateRecalculatesPrice() {
        Booking booking = service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), "старый");
        service.create(adult.getId(), pc.getId(), tomorrow(18, 0), tomorrow(20, 0), null);

        Booking updated = service.update(booking.getId(), vip.getId(), null, tomorrow(17, 0), "");
        assertEquals(vip.getId(), updated.getStationId());
        assertEquals(new BigDecimal("900.00"), updated.getTotalPrice());
        assertNull(updated.getComment());

        // возврат на PC-01 с наездом на бронь 18:00-20:00 запрещён
        assertThrows(BusinessRuleException.class,
                () -> service.update(booking.getId(), pc.getId(), tomorrow(17, 0), tomorrow(19, 0), null));
    }

    @Test
    @DisplayName("Сортировка и фильтры на Stream API")
    void sortingAndFiltering() {
        service.create(adult.getId(), pc.getId(), tomorrow(14, 0), tomorrow(16, 0), null);     // 300
        service.create(adult.getId(), vip.getId(), tomorrow(14, 0), tomorrow(18, 0), null);    // 1200
        service.create(minor.getId(), pc.getId(), tomorrow(10, 0), tomorrow(11, 0), null);     // 150

        List<Booking> byPriceDesc = service.sorted(BookingSortField.PRICE, false);
        assertEquals(new BigDecimal("1200.00"), byPriceDesc.get(0).getTotalPrice());
        assertEquals(new BigDecimal("150.00"), byPriceDesc.get(2).getTotalPrice());

        List<Booking> byStart = service.sorted(BookingSortField.START_TIME, true);
        assertEquals(tomorrow(10, 0), byStart.get(0).getStartTime());

        assertEquals(2, service.filterByPriceRange(new BigDecimal("200"), null).size());
        assertEquals(1, service.filterByPriceRange(null, new BigDecimal("200")).size());
        assertThrows(ValidationException.class, () -> service.filterByPriceRange(new BigDecimal("500"), new BigDecimal("100")));
        assertEquals(3, service.getUpcoming().size());
    }

    @Test
    @DisplayName("Пустая строка поиска отклоняется")
    void emptySearchQuery() {
        assertThrows(ValidationException.class, () -> service.searchByClientNickname("   "));
        assertThrows(ValidationException.class, () -> service.searchByComment(null));
    }
}
