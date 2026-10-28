package ru.mirea.cyberclub.backend.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.mirea.cyberclub.backend.domain.UserAccount;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.error.NotFoundException;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.repository.ClientRepository;
import ru.mirea.cyberclub.backend.repository.UserAccountRepository;
import ru.mirea.cyberclub.backend.security.CurrentUser;

/**
 * Данные вошедшего клиента для сайта клуба. Номер клиента берётся из его учётной записи,
 * а не из запроса, поэтому клиент видит только свою карточку и свои брони.
 */
@Service
public class ClientSelfService {
    private final UserAccountRepository users;
    private final ClientRepository clients;
    private final BookingService bookings;
    private final DtoMapper mapper;

    public ClientSelfService(UserAccountRepository users, ClientRepository clients, BookingService bookings,
                             DtoMapper mapper) {
        this.users = users;
        this.clients = clients;
        this.bookings = bookings;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ClientResponse card(CurrentUser current) {
        Long clientId = clientId(current);
        return clients.findById(clientId).map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Карточка клиента не найдена."));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> bookings(CurrentUser current, BookingFilter.ArchiveScope archive, Pageable pageable) {
        BookingFilter filter = new BookingFilter(null, null, null, clientId(current), null, null, archive,
                null, null, null, null);
        return bookings.search(filter, pageable);
    }

    private Long clientId(CurrentUser current) {
        return users.findById(current.id()).map(UserAccount::getClientId)
                .orElseThrow(() -> new NotFoundException("Учётная запись не привязана к клиенту клуба."));
    }
}
