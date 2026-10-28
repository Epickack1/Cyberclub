package ru.mirea.cyberclub.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.config.AppProperties;
import ru.mirea.cyberclub.backend.domain.Booking;
import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.BookingDtos.StatusRequest;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.filter.BookingSpecifications;
import ru.mirea.cyberclub.backend.repository.BookingRepository;
import ru.mirea.cyberclub.backend.repository.ClientRepository;
import ru.mirea.cyberclub.backend.repository.StationRepository;

/** Бронирования: создание, изменение, статусы, архив. Стоимость считает только сервер. */
@Service
public class BookingService {
    /** Подставляется вместо "исключить бронь" при создании: записи с id 0 не бывает. */
    private static final long NIL = 0L;

    private final BookingRepository bookings;
    private final ClientRepository clients;
    private final StationRepository stations;
    private final DtoMapper mapper;
    private final AppProperties properties;
    private final Clock clock;

    public BookingService(BookingRepository bookings, ClientRepository clients, StationRepository stations,
                          DtoMapper mapper, AppProperties properties, Clock clock) {
        this.bookings = bookings;
        this.clients = clients;
        this.stations = stations;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> search(BookingFilter filter, Pageable pageable) {
        return bookings.findAll(BookingSpecifications.from(filter), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional(readOnly = true)
    public BookingResponse getByNumber(Long number) {
        return mapper.toResponse(bookings.findByNumber(number)
                .orElseThrow(() -> new NotFoundException("Бронирование #" + number + " не найдено.")));
    }

    @Transactional
    public BookingResponse create(BookingRequest r) {
        Client client = requireClient(r.clientId());
        Station station = requireStation(r.stationId());
        BookingRules.validateInterval(r.startTime(), r.endTime(), true, now());
        BookingRules.checkStationActive(station);
        checkOverlap(station, r.startTime(), r.endTime(), NIL);
        BookingRules.checkAgeRestrictions(client, station, r.startTime(), r.endTime());

        Booking booking = new Booking(client, station);
        booking.setStartTime(r.startTime());
        booking.setEndTime(r.endTime());
        booking.setComment(blankToNull(r.comment()));
        booking.setPaid(r.paid());
        booking.setTotalPrice(BookingRules.calculatePrice(station.getHourlyRate(), r.startTime(), r.endTime()));
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    /** Место, время, комментарий и оплату можно менять только у брони в статусе "Создано" или "Подтверждено". */
    @Transactional
    public BookingResponse update(Long id, BookingRequest r) {
        Booking booking = require(id);
        Versions.check(r.version(), booking.getVersion());
        requireEditable(booking);
        if (!booking.getClient().getId().equals(r.clientId())) {
            throw new BusinessRuleException("Клиента у существующей брони менять нельзя: создайте новую бронь.");
        }
        Station station = requireStation(r.stationId());
        boolean timeChanged = !r.startTime().equals(booking.getStartTime()) || !r.endTime().equals(booking.getEndTime());
        BookingRules.validateInterval(r.startTime(), r.endTime(), timeChanged, now());
        BookingRules.checkStationActive(station);
        checkOverlap(station, r.startTime(), r.endTime(), id);
        BookingRules.checkAgeRestrictions(booking.getClient(), station, r.startTime(), r.endTime());

        booking.setStation(station);
        booking.setStartTime(r.startTime());
        booking.setEndTime(r.endTime());
        booking.setComment(blankToNull(r.comment()));
        booking.setPaid(r.paid());
        booking.setTotalPrice(BookingRules.calculatePrice(station.getHourlyRate(), r.startTime(), r.endTime()));
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    /** Переход статуса только по схеме CREATED -> CONFIRMED -> ACTIVE -> COMPLETED (или отмена). */
    @Transactional
    public BookingResponse changeStatus(Long id, StatusRequest r) {
        Booking booking = require(id);
        Versions.check(r.version(), booking.getVersion());
        requireNotArchived(booking);
        BookingStatus current = booking.getStatus();
        if (!current.canTransitionTo(r.status())) {
            String allowed = current.allowedTransitions().stream()
                    .map(BookingStatus::getTitle).collect(Collectors.joining(", "));
            throw new BusinessRuleException("Переход из статуса \"" + current.getTitle() + "\" в \""
                    + r.status().getTitle() + "\" запрещён. "
                    + (allowed.isEmpty() ? "Статус конечный." : "Допустимые статусы: " + allowed + "."));
        }
        booking.setStatus(r.status());
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    /** Обычное удаление — архивирование. Активную бронь в архив не убрать: сначала её надо завершить. */
    @Transactional
    public BookingResponse archive(Long id) {
        Booking booking = require(id);
        if (booking.isArchived()) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " уже в архиве.");
        }
        if (booking.getStatus() == BookingStatus.ACTIVE) {
            throw new BusinessRuleException("Активное бронирование нельзя убрать в архив: сначала завершите его.");
        }
        booking.archive();
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    /** Восстановление из архива, если за это время место не заняли другой бронью. */
    @Transactional
    public BookingResponse restore(Long id) {
        Booking booking = require(id);
        if (!booking.isArchived()) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " не в архиве.");
        }
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            checkOverlap(booking.getStation(), booking.getStartTime(), booking.getEndTime(), id);
        }
        booking.restore();
        return mapper.toResponse(bookings.saveAndFlush(booking));
    }

    /** Окончательное удаление: включается настройкой и требует явного подтверждения заголовком. */
    @Transactional
    public void permanentDelete(Long id, String confirmation) {
        if (!properties.permanentDeleteEnabled()) {
            throw new BusinessRuleException("Окончательное удаление отключено настройкой сервера.");
        }
        if (!"PERMANENTLY_DELETE".equals(confirmation)) {
            throw new BadRequestException("Для удаления нужен заголовок X-Confirm-Delete: PERMANENTLY_DELETE.");
        }
        bookings.delete(require(id));
    }

    private Booking require(Long id) {
        return bookings.findById(id).orElseThrow(() -> new NotFoundException("Бронирование " + id + " не найдено."));
    }

    /** Ссылка на несуществующего клиента или место — ошибка данных запроса (400), а не 404 самой брони. */
    private Client requireClient(Long id) {
        return clients.findById(id).orElseThrow(() -> new BadRequestException("clientId", "Клиент с id " + id + " не найден."));
    }

    private Station requireStation(Long id) {
        return stations.findById(id).orElseThrow(() -> new BadRequestException("stationId", "Игровое место с id " + id + " не найдено."));
    }

    private void requireEditable(Booking booking) {
        requireNotArchived(booking);
        if (!booking.getStatus().isEditable()) {
            throw new BusinessRuleException("Бронирование в статусе \"" + booking.getStatus().getTitle()
                    + "\" изменять нельзя (только \"Создано\" и \"Подтверждено\").");
        }
    }

    private void requireNotArchived(Booking booking) {
        if (booking.isArchived()) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " в архиве: сначала восстановите его.");
        }
    }

    /** Одно место — одна действующая бронь на любой момент времени. */
    private void checkOverlap(Station station, LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<Booking> conflicts = bookings.findOverlapping(station.getId(), start, end, excludeId);
        if (!conflicts.isEmpty()) {
            Booking other = conflicts.get(0);
            throw new ConflictException("Место " + station.getName() + " уже занято: бронирование #" + other.getNumber()
                    + " с " + BookingRules.format(other.getStartTime()) + " по " + BookingRules.format(other.getEndTime()) + ".");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
