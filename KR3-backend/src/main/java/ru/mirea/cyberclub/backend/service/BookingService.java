package ru.mirea.cyberclub.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
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
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.BusySlot;
import ru.mirea.cyberclub.backend.error.BadRequestException;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.filter.BookingSpecifications;
import ru.mirea.cyberclub.backend.repository.BookingRepository;
import ru.mirea.cyberclub.backend.repository.ClientRepository;
import ru.mirea.cyberclub.backend.repository.StationRepository;

/**
 * Бронирования: создание, изменение, статусы, архив, теги. Стоимость и риск неоплаты считает
 * только сервер. Каждое изменение записывается в журнал аудита в той же транзакции.
 */
@Service
public class BookingService {
    /** Подставляется вместо "исключить бронь" при создании: такого UUID в базе не бывает. */
    private static final UUID NIL = new UUID(0, 0);

    private final BookingRepository bookings;
    private final ClientRepository clients;
    private final StationRepository stations;
    private final DtoMapper mapper;
    private final AppProperties properties;
    private final AuditService audit;
    private final Clock clock;

    public BookingService(BookingRepository bookings, ClientRepository clients, StationRepository stations,
                          DtoMapper mapper, AppProperties properties, AuditService audit, Clock clock) {
        this.bookings = bookings;
        this.clients = clients;
        this.stations = stations;
        this.mapper = mapper;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> search(BookingFilter filter, Pageable pageable) {
        return bookings.findAll(BookingSpecifications.from(filter), pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID id) {
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

        Booking booking = new Booking(UUID.randomUUID(), client, station);
        booking.setStartTime(r.startTime());
        booking.setEndTime(r.endTime());
        booking.setComment(blankToNull(r.comment()));
        booking.setPaid(r.paid());
        booking.setTags(Tags.normalizeAll(r.tags(), "tags"));
        booking.setTotalPrice(BookingRules.calculatePrice(station.getHourlyRate(), r.startTime(), r.endTime()));
        Booking saved = bookings.saveAndFlush(booking);
        audit.record("BOOKING_CREATED", AuditService.BOOKING, saved.getId(), "Создана бронь #" + saved.getNumber()
                + ": " + client.getNickname() + ", " + station.getName() + ", " + BookingRules.format(saved.getStartTime()));
        return mapper.toResponse(saved);
    }

    /** Место, время, комментарий и оплату можно менять только у брони в статусе "Создано" или "Подтверждено". */
    @Transactional
    public BookingResponse update(UUID id, BookingRequest r) {
        Booking booking = require(id);
        Versions.check(r.version(), booking.getVersion());
        return applyUpdate(booking, r);
    }

    /**
     * Изменение при импорте CSV: бронь ищется по номеру (внешний идентификатор), версия — текущая
     * из базы, потому что в файле её нет. Остальные правила те же, что у обычного изменения.
     */
    @Transactional
    public BookingResponse updateByNumber(long number, BookingRequest r) {
        Booking booking = bookings.findByNumber(number)
                .orElseThrow(() -> new NotFoundException("Бронирование #" + number + " не найдено."));
        return applyUpdate(booking, r);
    }

    private BookingResponse applyUpdate(Booking booking, BookingRequest r) {
        UUID id = booking.getId();
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
        if (r.tags() != null) {
            booking.setTags(Tags.normalizeAll(r.tags(), "tags"));
        }
        booking.setTotalPrice(BookingRules.calculatePrice(station.getHourlyRate(), r.startTime(), r.endTime()));
        Booking saved = bookings.saveAndFlush(booking);
        audit.record("BOOKING_UPDATED", AuditService.BOOKING, id, "Изменена бронь #" + saved.getNumber()
                + ": " + station.getName() + ", " + BookingRules.format(saved.getStartTime())
                + (saved.isPaid() ? ", оплачено" : ", не оплачено"));
        return mapper.toResponse(saved);
    }

    /** Переход статуса только по схеме CREATED -> CONFIRMED -> ACTIVE -> COMPLETED (или отмена). */
    @Transactional
    public BookingResponse changeStatus(UUID id, StatusRequest r) {
        Booking booking = require(id);
        Versions.check(r.version(), booking.getVersion());
        return applyStatus(booking, r.status());
    }

    /** Смена статуса из массовой операции: версия не передаётся, правила переходов те же. */
    @Transactional
    public BookingResponse changeStatus(UUID id, BookingStatus status) {
        return applyStatus(require(id), status);
    }

    private BookingResponse applyStatus(Booking booking, BookingStatus next) {
        StatusRequest r = new StatusRequest(next, booking.getVersion());
        requireNotArchived(booking);
        BookingStatus current = booking.getStatus();
        if (current == next) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " уже в статусе \"" + next.getTitle() + "\".");
        }
        if (!current.canTransitionTo(r.status())) {
            String allowed = current.allowedTransitions().stream()
                    .map(BookingStatus::getTitle).collect(Collectors.joining(", "));
            throw new BusinessRuleException("Переход из статуса \"" + current.getTitle() + "\" в \""
                    + r.status().getTitle() + "\" запрещён. "
                    + (allowed.isEmpty() ? "Статус конечный." : "Допустимые статусы: " + allowed + "."));
        }
        booking.setStatus(r.status());
        Booking saved = bookings.saveAndFlush(booking);
        audit.record("BOOKING_STATUS", AuditService.BOOKING, saved.getId(), "Бронь #" + saved.getNumber()
                + ": статус \"" + current.getTitle() + "\" -> \"" + next.getTitle() + "\"");
        return mapper.toResponse(saved);
    }

    /**
     * Подтверждение оплаты администратором (клиент заплатил на стойке). Риск неоплаты пересчитывается
     * сам. Нельзя подтвердить оплату отменённой или архивной брони.
     */
    @Transactional
    public BookingResponse confirmPayment(UUID id, Long version) {
        Booking booking = require(id);
        if (version != null) {
            Versions.check(version, booking.getVersion());
        }
        requireNotArchived(booking);
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " отменено — оплату подтверждать не нужно.");
        }
        if (booking.isPaid()) {
            throw new BusinessRuleException("Оплата брони #" + booking.getNumber() + " уже подтверждена.");
        }
        booking.setPaid(true);
        Booking saved = bookings.saveAndFlush(booking);
        audit.record("BOOKING_PAID", AuditService.BOOKING, id, "Подтверждена оплата брони #" + saved.getNumber()
                + " (" + saved.getTotalPrice().toPlainString() + " руб.)");
        return mapper.toResponse(saved);
    }

    /**
     * Отмена брони самим клиентом из личного кабинета: только своей, только до начала игры
     * и только в статусе «Создано» или «Подтверждено». Чужая бронь для клиента «не существует» (404).
     */
    @Transactional
    public BookingResponse cancelByClient(UUID id, long clientId) {
        Booking booking = require(id);
        if (!booking.getClient().getId().equals(clientId)) {
            throw new NotFoundException("Бронирование " + id + " не найдено.");
        }
        if (!booking.getStatus().isEditable()) {
            throw new BusinessRuleException("Бронь в статусе \"" + booking.getStatus().getTitle()
                    + "\" отменить нельзя — обратитесь к администратору зала.");
        }
        if (!booking.getStartTime().isAfter(now())) {
            throw new BusinessRuleException("Игра уже началась — отменить бронь может только администратор зала.");
        }
        return applyStatus(booking, BookingStatus.CANCELLED);
    }

    /** Занятые интервалы места на день — чтобы клиент выбрал свободное время. Кто занял, не раскрывается. */
    @Transactional(readOnly = true)
    public List<BusySlot> busySlots(Long stationId, LocalDate date) {
        requireStation(stationId);
        return bookings.findOverlapping(stationId, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), NIL).stream()
                .map(b -> new BusySlot(b.getStartTime(), b.getEndTime()))
                .toList();
    }

    /** Добавление тега (массовая операция). Повторное добавление — не ошибка, бронь просто не меняется. */
    @Transactional
    public BookingResponse addTag(UUID id, String tag) {
        Booking booking = require(id);
        String value = Tags.normalize(tag, "tag");
        if (booking.getTags().size() >= Tags.MAX_PER_BOOKING && !booking.getTags().contains(value)) {
            throw new BusinessRuleException("У брони #" + booking.getNumber() + " уже " + Tags.MAX_PER_BOOKING + " тегов.");
        }
        if (booking.addTag(value)) {
            bookings.saveAndFlush(booking);
            audit.record("BOOKING_TAG_ADDED", AuditService.BOOKING, id, "Бронь #" + booking.getNumber() + ": добавлен тег " + value);
        }
        return mapper.toResponse(booking);
    }

    @Transactional
    public BookingResponse removeTag(UUID id, String tag) {
        Booking booking = require(id);
        String value = Tags.normalize(tag, "tag");
        if (booking.removeTag(value)) {
            bookings.saveAndFlush(booking);
            audit.record("BOOKING_TAG_REMOVED", AuditService.BOOKING, id, "Бронь #" + booking.getNumber() + ": снят тег " + value);
        }
        return mapper.toResponse(booking);
    }

    /** Обычное удаление — архивирование. Активную бронь в архив не убрать: сначала её надо завершить. */
    @Transactional
    public BookingResponse archive(UUID id) {
        Booking booking = require(id);
        if (booking.isArchived()) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " уже в архиве.");
        }
        if (booking.getStatus() == BookingStatus.ACTIVE) {
            throw new BusinessRuleException("Активное бронирование нельзя убрать в архив: сначала завершите его.");
        }
        booking.archive();
        Booking saved = bookings.saveAndFlush(booking);
        audit.record("BOOKING_ARCHIVED", AuditService.BOOKING, id, "Бронь #" + saved.getNumber() + " убрана в архив");
        return mapper.toResponse(saved);
    }

    /** Восстановление из архива, если за это время место не заняли другой бронью. */
    @Transactional
    public BookingResponse restore(UUID id) {
        Booking booking = require(id);
        if (!booking.isArchived()) {
            throw new BusinessRuleException("Бронирование #" + booking.getNumber() + " не в архиве.");
        }
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            checkOverlap(booking.getStation(), booking.getStartTime(), booking.getEndTime(), id);
        }
        booking.restore();
        Booking saved = bookings.saveAndFlush(booking);
        audit.record("BOOKING_RESTORED", AuditService.BOOKING, id, "Бронь #" + saved.getNumber() + " восстановлена из архива");
        return mapper.toResponse(saved);
    }

    /** Окончательное удаление: включается настройкой и требует явного подтверждения заголовком. */
    @Transactional
    public void permanentDelete(UUID id, String confirmation) {
        if (!properties.permanentDeleteEnabled()) {
            throw new BusinessRuleException("Окончательное удаление отключено настройкой сервера.");
        }
        if (!"PERMANENTLY_DELETE".equals(confirmation)) {
            throw new BadRequestException("Для удаления нужен заголовок X-Confirm-Delete: PERMANENTLY_DELETE.");
        }
        Booking booking = require(id);
        bookings.delete(booking);
        audit.record("BOOKING_DELETED", AuditService.BOOKING, id, "Бронь #" + booking.getNumber()
                + " удалена окончательно (" + booking.getClient().getNickname() + ", " + booking.getStation().getName() + ")");
    }

    /** Бронь по UUID или 404. Публичный: им пользуется сервис заметок (вызов идёт через прокси Spring). */
    public Booking require(UUID id) {
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
    private void checkOverlap(Station station, LocalDateTime start, LocalDateTime end, UUID excludeId) {
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
