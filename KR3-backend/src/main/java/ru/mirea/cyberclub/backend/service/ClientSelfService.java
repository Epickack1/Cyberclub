package ru.mirea.cyberclub.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Role;
import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientRequest;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.BusySlot;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.RegisterRequest;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.SelfBookingRequest;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationResponse;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.repository.ClientRepository;
import ru.mirea.cyberclub.backend.repository.UserAccountRepository;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.security.PasswordPolicy;

/**
 * Клиент клуба на сайте: регистрация, своя карточка, свободное время мест, свои брони.
 * Номер клиента берётся из его учётной записи, а не из запроса, поэтому клиент видит и меняет
 * только своё. Брони с сайта создаются неоплаченными — оплату подтверждает администратор.
 * Все правила бронирования (длительность, занятость места, возраст 18+) — те же, что у сотрудников.
 */
@Service
public class ClientSelfService {
    private static final Logger log = LoggerFactory.getLogger(ClientSelfService.class);

    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final ClientService clientService;
    private final BookingService bookings;
    private final StationService stations;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AuditService audit;
    private final DtoMapper mapper;

    public ClientSelfService(UserAccountRepository users, ClientRepository clients, ClientService clientService,
                             BookingService bookings, StationService stations, PasswordEncoder passwordEncoder,
                             PasswordPolicy passwordPolicy, AuditService audit, DtoMapper mapper) {
        this.users = users;
        this.clients = clients;
        this.clientService = clientService;
        this.bookings = bookings;
        this.stations = stations;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.audit = audit;
        this.mapper = mapper;
    }

    /**
     * Регистрация на сайте: карточка клиента (правила КР1: уникальные ник, телефон, email, возраст от 12)
     * и учётная запись CLIENT с паролем, который клиент придумал сам, — его менять не нужно.
     */
    @Transactional
    public void register(RegisterRequest r) {
        String email = UserAccount.normalizeEmail(r.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Пользователь с таким email уже зарегистрирован — войдите или восстановите пароль у администратора.");
        }
        passwordPolicy.validate(r.password(), email, "password");
        ClientResponse client = clientService.create(new ClientRequest(r.nickname(), r.fullName(), r.phone(), email,
                r.birthDate(), null));
        UserAccount account = new UserAccount(email, client.fullName(), passwordEncoder.encode(r.password()),
                Set.of(Role.CLIENT));
        account.setClientId(client.id());
        UserAccount saved = users.saveAndFlush(account);
        audit.recordAs(saved.getId(), saved.getEmail(), "CLIENT_REGISTERED", AuditService.USER, saved.getId(),
                "Клиент " + client.nickname() + " зарегистрировался на сайте");
        log.info("Регистрация клиента на сайте: clientId={} userId={}", client.id(), saved.getId());
    }

    @Transactional(readOnly = true)
    public ClientResponse card(CurrentUser current) {
        Long clientId = clientId(current);
        return clients.findById(clientId).map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Карточка клиента не найдена."));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> bookings(CurrentUser current, BookingFilter.ArchiveScope archive, Pageable pageable) {
        BookingFilter filter = new BookingFilter(null, null, null, clientId(current), null, null, null, null, archive,
                null, null, null, null);
        return bookings.search(filter, pageable);
    }

    /** Места, доступные для бронирования (без отключённых на обслуживание). */
    @Transactional(readOnly = true)
    public List<StationResponse> stations() {
        return stations.list(true);
    }

    @Transactional(readOnly = true)
    public List<BusySlot> busy(Long stationId, LocalDate date) {
        return bookings.busySlots(stationId, date);
    }

    /** Бронь от имени вошедшего клиента: всегда без оплаты и без тегов. */
    @Transactional
    public BookingResponse book(CurrentUser current, SelfBookingRequest r) {
        return bookings.create(new BookingRequest(clientId(current), r.stationId(), r.startTime(), r.endTime(),
                r.comment(), false, null, null));
    }

    @Transactional
    public BookingResponse cancel(CurrentUser current, UUID bookingId) {
        return bookings.cancelByClient(bookingId, clientId(current));
    }

    private Long clientId(CurrentUser current) {
        return users.findById(current.id()).map(UserAccount::getClientId)
                .orElseThrow(() -> new NotFoundException("Учётная запись не привязана к клиенту клуба."));
    }
}
