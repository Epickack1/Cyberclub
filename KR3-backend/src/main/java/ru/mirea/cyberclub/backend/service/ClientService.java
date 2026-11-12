package ru.mirea.cyberclub.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientRequest;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.error.BusinessRuleException;
import ru.mirea.cyberclub.backend.error.ConflictException;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.filter.BookingSpecifications;
import ru.mirea.cyberclub.backend.repository.BookingRepository;
import ru.mirea.cyberclub.backend.repository.ClientRepository;

/** Клиенты клуба: правила из КР1 (уникальность, возраст от 12 лет, запрет удаления с историей). */
@Service
public class ClientService {
    public static final int MIN_AGE = 12;
    private static final long NO_ID = -1L;

    private final ClientRepository clients;
    private final BookingRepository bookings;
    private final DtoMapper mapper;
    private final AuditService audit;
    private final Clock clock;

    public ClientService(ClientRepository clients, BookingRepository bookings, DtoMapper mapper, AuditService audit,
                         Clock clock) {
        this.clients = clients;
        this.bookings = bookings;
        this.mapper = mapper;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list(String q) {
        List<Client> found = q == null || q.isBlank()
                ? clients.findAll(Sort.by("nickname"))
                : clients.search("%" + BookingSpecifications.escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%");
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return mapper.toResponse(require(id));
    }

    @Transactional
    public ClientResponse create(ClientRequest request) {
        checkRules(request, NO_ID);
        Client client = new Client(request.nickname().trim(), request.fullName().trim(), request.phone().trim(),
                request.email().trim(), request.birthDate());
        Client saved = clients.save(client);
        audit.record("CLIENT_CREATED", AuditService.CLIENT, saved.getId(), "Добавлен клиент " + saved.getNickname()
                + " (" + saved.getFullName() + ")");
        return mapper.toResponse(saved);
    }

    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = require(id);
        Versions.check(request.version(), client.getVersion());
        checkRules(request, id);
        client.setNickname(request.nickname().trim());
        client.setFullName(request.fullName().trim());
        client.setPhone(request.phone().trim());
        client.setEmail(request.email().trim());
        client.setBirthDate(request.birthDate());
        Client saved = clients.saveAndFlush(client);
        audit.record("CLIENT_UPDATED", AuditService.CLIENT, id, "Изменён клиент " + saved.getNickname());
        return mapper.toResponse(saved);
    }

    /** Клиента с бронированиями удалить нельзя: история нужна для отчётности. */
    @Transactional
    public void delete(Long id) {
        Client client = require(id);
        if (bookings.existsByClientId(id)) {
            throw new ConflictException("Нельзя удалить клиента " + client.getNickname()
                    + ": у него есть бронирования. История сохраняется для отчётности.");
        }
        clients.delete(client);
        audit.record("CLIENT_DELETED", AuditService.CLIENT, id, "Удалён клиент " + client.getNickname()
                + " (" + client.getFullName() + ")");
    }

    Client require(Long id) {
        return clients.findById(id).orElseThrow(() -> new NotFoundException("Клиент с id " + id + " не найден."));
    }

    private void checkRules(ClientRequest r, long selfId) {
        if (r.birthDate() != null && Period.between(r.birthDate(), LocalDate.now(clock)).getYears() < MIN_AGE) {
            throw new BusinessRuleException("Клиент клуба должен быть не младше " + MIN_AGE + " лет.");
        }
        if (clients.existsByNicknameIgnoreCaseAndIdNot(r.nickname().trim(), selfId)) {
            throw new ConflictException("Ник \"" + r.nickname().trim() + "\" уже занят.");
        }
        if (clients.existsByPhoneAndIdNot(r.phone().trim(), selfId)) {
            throw new ConflictException("Телефон " + r.phone().trim() + " уже зарегистрирован.");
        }
        if (clients.existsByEmailIgnoreCaseAndIdNot(r.email().trim(), selfId)) {
            throw new ConflictException("Email " + r.email().trim() + " уже зарегистрирован.");
        }
    }
}
