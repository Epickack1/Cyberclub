package ru.mirea.cyberclub.backend.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.dto.PageResponse;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.service.ClientSelfService;
import ru.mirea.cyberclub.backend.web.PageableFactory;

/** Личный кабинет клиента для сайта клуба. Доступно только роли CLIENT (правило в SecurityConfig). */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Личный кабинет клиента", description = "Своя карточка и свои бронирования; войдите учётной записью клиента")
public class ClientSelfController {
    private final ClientSelfService self;
    private final PageableFactory pageableFactory;

    public ClientSelfController(ClientSelfService self, PageableFactory pageableFactory) {
        this.self = self;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping("/client")
    @Operation(summary = "Моя карточка клиента")
    public ClientResponse card(@AuthenticationPrincipal CurrentUser user) {
        return self.card(user);
    }

    @GetMapping("/bookings")
    @Operation(summary = "Мои бронирования",
            description = "archive: ACTIVE (по умолчанию), ARCHIVED, ALL. sort — как в /api/bookings.")
    public PageResponse<BookingResponse> bookings(
            @AuthenticationPrincipal CurrentUser user,
            @RequestParam(defaultValue = "ACTIVE") BookingFilter.ArchiveScope archive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "start,desc") String sort) {
        return PageResponse.from(self.bookings(user, archive, pageableFactory.bookings(page, size, sort)));
    }
}
