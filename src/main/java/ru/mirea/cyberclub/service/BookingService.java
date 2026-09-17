package ru.mirea.cyberclub.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

import ru.mirea.cyberclub.exception.BusinessRuleException;
import ru.mirea.cyberclub.exception.EntityNotFoundException;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.repository.ClientRepository;
import ru.mirea.cyberclub.repository.StationRepository;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Бизнес-логика бронирования игровых мест. Здесь реализованы все бизнес-правила:
 * <ol>
 *   <li>клиент и игровое место должны существовать;</li>
 *   <li>нельзя бронировать место, находящееся на обслуживании;</li>
 *   <li>интервал корректен: конец позже начала, длительность 30 мин – 12 ч, не в прошлом;</li>
 *   <li>место не может быть занято пересекающейся неотменённой бронью;</li>
 *   <li>смена статуса только по схеме переходов {@link BookingStatus};</li>
 *   <li>менять время/место можно только у CREATED/CONFIRMED, удалять нельзя ACTIVE;</li>
 *   <li>ночное время (22:00–06:00) и зоны VIP/буткемп — только клиентам 18+;</li>
 *   <li>стоимость считается автоматически: часы (с округлением вверх до 30 мин) × тариф.</li>
 * </ol>
 */
public class BookingService {

    public static final int MIN_DURATION_MINUTES = 30;
    public static final int MAX_DURATION_HOURS = 12;
    public static final int PAST_GRACE_MINUTES = 10;
    public static final LocalTime NIGHT_START = LocalTime.of(22, 0);
    public static final LocalTime NIGHT_END = LocalTime.of(6, 0);

    private final BookingRepository bookingRepository;
    private final ClientRepository clientRepository;
    private final StationRepository stationRepository;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, ClientRepository clientRepository,
                          StationRepository stationRepository) {
        this(bookingRepository, clientRepository, stationRepository, Clock.systemDefaultZone());
    }

    public BookingService(BookingRepository bookingRepository, ClientRepository clientRepository,
                          StationRepository stationRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.clientRepository = clientRepository;
        this.stationRepository = stationRepository;
        this.clock = clock;
    }

    // ------------------------------------------------------------------- CRUD

    public Booking create(Long clientId, Long stationId, LocalDateTime start, LocalDateTime end, String comment) {
        Client client = requireClient(clientId);
        Station station = requireStation(stationId);
        validateInterval(start, end, true);
        checkStationActive(station);
        checkOverlap(station, start, end, null);
        checkAgeRestrictions(client, station, start, end);

        Booking booking = new Booking(clientId, stationId, start, end, normalizeComment(comment));
        booking.setStatus(BookingStatus.CREATED);
        booking.setTotalPrice(calculatePrice(station, start, end));
        return bookingRepository.save(booking);
    }

    public List<Booking> getAll() {
        return bookingRepository.findAll();
    }

    public Booking getById(Long id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Бронирование", id));
    }

    /** Правило 6: менять время и место можно только у брони в статусе CREATED или CONFIRMED. */
    public Booking getEditable(Long id) {
        Booking booking = getById(id);
        if (!booking.getStatus().isEditable()) {
            throw new BusinessRuleException("Бронирование в статусе \"" + booking.getStatus().getTitle()
                    + "\" изменять нельзя (только \"Создано\" и \"Подтверждено\").");
        }
        return booking;
    }

    /**
     * Изменяет место, время и комментарий брони. Параметр null означает «оставить как было».
     */
    public Booking update(Long id, Long newStationId, LocalDateTime newStart, LocalDateTime newEnd, String newComment) {
        Booking booking = getEditable(id);
        Client client = requireClient(booking.getClientId());
        Station station = requireStation(newStationId != null ? newStationId : booking.getStationId());
        LocalDateTime start = newStart != null ? newStart : booking.getStartTime();
        LocalDateTime end = newEnd != null ? newEnd : booking.getEndTime();
        boolean timeChanged = !start.equals(booking.getStartTime()) || !end.equals(booking.getEndTime());

        validateInterval(start, end, timeChanged);
        checkStationActive(station);
        checkOverlap(station, start, end, id);
        checkAgeRestrictions(client, station, start, end);

        booking.setStationId(station.getId());
        booking.setStartTime(start);
        booking.setEndTime(end);
        if (newComment != null) {
            booking.setComment(normalizeComment(newComment));
        }
        booking.setTotalPrice(calculatePrice(station, start, end));
        if (!bookingRepository.update(booking)) {
            throw new EntityNotFoundException("Бронирование", id);
        }
        return getById(id);
    }

    /** Правило 5: переход статуса только по допустимой схеме. */
    public Booking changeStatus(Long id, BookingStatus newStatus) {
        Booking booking = getById(id);
        if (newStatus == null) {
            throw new ValidationException("Новый статус не указан.");
        }
        BookingStatus current = booking.getStatus();
        if (!current.canTransitionTo(newStatus)) {
            String allowed = current.allowedTransitions().stream()
                    .map(BookingStatus::getTitle)
                    .collect(Collectors.joining(", "));
            throw new BusinessRuleException("Переход из статуса \"" + current.getTitle() + "\" в \""
                    + newStatus.getTitle() + "\" запрещён. "
                    + (allowed.isEmpty() ? "Статус конечный." : "Допустимые статусы: " + allowed + "."));
        }
        booking.setStatus(newStatus);
        if (!bookingRepository.update(booking)) {
            throw new EntityNotFoundException("Бронирование", id);
        }
        return booking;
    }

    /** Правило 6: активное бронирование удалить нельзя. */
    public void delete(Long id) {
        Booking booking = getById(id);
        if (booking.getStatus() == BookingStatus.ACTIVE) {
            throw new BusinessRuleException("Активное бронирование нельзя удалить: сначала завершите его.");
        }
        if (!bookingRepository.deleteById(id)) {
            throw new EntityNotFoundException("Бронирование", id);
        }
    }

    // ------------------------------------------------------------------ поиск

    public List<Booking> searchByClientNickname(String query) {
        return bookingRepository.searchByClientNickname(requireQuery(query));
    }

    public List<Booking> searchByStationName(String query) {
        return bookingRepository.searchByStationName(requireQuery(query));
    }

    public List<Booking> searchByDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("Дата не указана.");
        }
        return bookingRepository.findByDate(date);
    }

    public List<Booking> searchByComment(String query) {
        return bookingRepository.searchByComment(requireQuery(query));
    }

    // ------------------------------------------------------------- фильтрация

    public List<Booking> filterByStatus(BookingStatus status) {
        if (status == null) {
            throw new ValidationException("Статус не указан.");
        }
        return bookingRepository.findByStatus(status);
    }

    public List<Booking> filterByStationType(StationType type) {
        if (type == null) {
            throw new ValidationException("Тип игрового места не указан.");
        }
        return bookingRepository.findByStationType(type);
    }

    public List<Booking> filterByPeriod(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new ValidationException("Границы периода обязательны.");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("Конец периода раньше его начала.");
        }
        return bookingRepository.findByPeriod(from, to);
    }

    public List<Booking> filterByClient(Long clientId) {
        requireClient(clientId);
        return bookingRepository.findByClientId(clientId);
    }

    /** Фильтр по диапазону стоимости — выполняется в Java через Stream API. */
    public List<Booking> filterByPriceRange(BigDecimal min, BigDecimal max) {
        BigDecimal low = min == null ? BigDecimal.ZERO : min;
        if (low.signum() < 0 || (max != null && max.compareTo(low) < 0)) {
            throw new ValidationException("Некорректный диапазон стоимости.");
        }
        return bookingRepository.findAll().stream()
                .filter(b -> b.getTotalPrice().compareTo(low) >= 0)
                .filter(b -> max == null || b.getTotalPrice().compareTo(max) <= 0)
                .toList();
    }

    /** Предстоящие брони: начинаются позже текущего момента и не отменены (Stream API). */
    public List<Booking> getUpcoming() {
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.findAll().stream()
                .filter(b -> b.getStatus().isUnfinished())
                .filter(b -> b.getStartTime().isAfter(now))
                .sorted(BookingSortField.START_TIME.comparator(true))
                .toList();
    }

    // ------------------------------------------------------------- сортировка

    /** Сортировка списка бронирований компаратором из {@link BookingSortField}. */
    public List<Booking> sorted(BookingSortField field, boolean ascending) {
        if (field == null) {
            throw new ValidationException("Поле сортировки не указано.");
        }
        return bookingRepository.findAll().stream()
                .sorted(field.comparator(ascending))
                .toList();
    }

    // ------------------------------------------------------------- стоимость

    /**
     * Правило 9: стоимость = тариф × часы, длительность округляется вверх до 30 минут.
     * Например, 1 ч 40 мин на месте за 150 ₽/ч → 2 ч → 300 ₽.
     */
    public BigDecimal calculatePrice(Station station, LocalDateTime start, LocalDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        long halfHours = (minutes + 29) / 30;
        return station.getHourlyRate()
                .multiply(BigDecimal.valueOf(halfHours))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- правила

    /** Правило 1: клиент должен существовать. */
    private Client requireClient(Long clientId) {
        if (clientId == null) {
            throw new ValidationException("Клиент не указан.");
        }
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Клиент", clientId));
    }

    /** Правило 1: игровое место должно существовать. */
    private Station requireStation(Long stationId) {
        if (stationId == null) {
            throw new ValidationException("Игровое место не указано.");
        }
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new EntityNotFoundException("Игровое место", stationId));
    }

    /** Правило 2: место на обслуживании бронировать нельзя. */
    private void checkStationActive(Station station) {
        if (!station.isActive()) {
            throw new BusinessRuleException("Игровое место " + station.getName()
                    + " находится на обслуживании и недоступно для бронирования.");
        }
    }

    /** Правило 3: корректность временного интервала. */
    private void validateInterval(LocalDateTime start, LocalDateTime end, boolean mustBeInFuture) {
        if (start == null || end == null) {
            throw new ValidationException("Время начала и окончания обязательны.");
        }
        if (!end.isAfter(start)) {
            throw new ValidationException("Время окончания должно быть позже времени начала.");
        }
        Duration duration = Duration.between(start, end);
        if (duration.toMinutes() < MIN_DURATION_MINUTES) {
            throw new BusinessRuleException("Минимальная длительность бронирования - " + MIN_DURATION_MINUTES + " минут.");
        }
        if (duration.toHours() > MAX_DURATION_HOURS
                || (duration.toHours() == MAX_DURATION_HOURS && duration.toMinutesPart() > 0)) {
            throw new BusinessRuleException("Максимальная длительность бронирования - " + MAX_DURATION_HOURS + " часов.");
        }
        if (mustBeInFuture && start.isBefore(LocalDateTime.now(clock).minusMinutes(PAST_GRACE_MINUTES))) {
            throw new BusinessRuleException("Нельзя создать бронирование в прошлом (начало "
                    + DateTimeUtil.format(start) + ").");
        }
    }

    /** Правило 4: на одном месте брони не пересекаются. */
    private void checkOverlap(Station station, LocalDateTime start, LocalDateTime end, Long excludeBookingId) {
        List<Booking> conflicts = bookingRepository.findOverlapping(station.getId(), start, end, excludeBookingId);
        if (!conflicts.isEmpty()) {
            Booking other = conflicts.get(0);
            throw new BusinessRuleException("Место " + station.getName() + " уже занято: бронирование #"
                    + other.getId() + " с " + DateTimeUtil.format(other.getStartTime())
                    + " по " + DateTimeUtil.format(other.getEndTime()) + ".");
        }
    }

    /** Правило 7: возрастные ограничения для VIP/буткемпа и ночного времени. */
    private void checkAgeRestrictions(Client client, Station station, LocalDateTime start, LocalDateTime end) {
        int age = client.getAge(start.toLocalDate());
        boolean adult = age >= 18;
        if (station.getType().isAdultsOnly() && !adult) {
            throw new BusinessRuleException("Зона \"" + station.getType().getTitle()
                    + "\" доступна только клиентам 18+ (клиенту " + client.getNickname() + " - " + age + ").");
        }
        if (touchesNight(start, end) && !adult) {
            throw new BusinessRuleException("Ночное время (" + NIGHT_START + "-" + NIGHT_END
                    + ") доступно только клиентам 18+ (клиенту " + client.getNickname() + " - " + age + ").");
        }
    }

    /** Захватывает ли интервал [start, end) ночные часы 22:00–06:00. */
    static boolean touchesNight(LocalDateTime start, LocalDateTime end) {
        if (isNight(start.toLocalTime())) {
            return true;
        }
        // начало днём: ночь начнётся в ближайшие 22:00 — попадает ли этот момент внутрь интервала
        LocalDateTime nextNight = start.toLocalDate().atTime(NIGHT_START);
        if (!nextNight.isAfter(start)) {
            nextNight = nextNight.plusDays(1);
        }
        return nextNight.isBefore(end);
    }

    private static boolean isNight(LocalTime time) {
        return !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END);
    }

    private static String requireQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("Строка поиска не может быть пустой.");
        }
        return query.trim();
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > 255) {
            throw new ValidationException("Комментарий не может быть длиннее 255 символов.");
        }
        return trimmed;
    }
}
