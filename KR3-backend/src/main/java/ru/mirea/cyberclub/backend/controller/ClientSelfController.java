package ru.mirea.cyberclub.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.ClientDtos.ClientResponse;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.BusySlot;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.SelfBookingRequest;
import ru.mirea.cyberclub.backend.dto.PageResponse;
import ru.mirea.cyberclub.backend.dto.StationDtos.StationResponse;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.security.CurrentUser;
import ru.mirea.cyberclub.backend.service.ClientSelfService;
import ru.mirea.cyberclub.backend.web.PageableFactory;

/** Личный кабинет клиента для сайта клуба. Доступно только роли CLIENT (правило в SecurityConfig). */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Личный кабинет клиента", description = "Своя карточка, свободное время мест, свои брони; войдите учётной записью клиента")
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

    @GetMapping("/stations")
    @Operation(summary = "Игровые места, доступные для бронирования")
    public List<StationResponse> stations() {
        return self.stations();
    }

    @GetMapping("/busy")
    @Operation(summary = "Занятое время места на дату (без сведений о других клиентах)")
    public List<BusySlot> busy(@RequestParam Long stationId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return self.busy(stationId, date);
    }

    @PostMapping("/bookings")
    @Operation(summary = "Забронировать место",
            description = "Бронь создаётся в статусе «Создано» и неоплаченной; оплату подтверждает администратор зала. "
                    + "Правила: от 30 минут до 12 часов, не в прошлом, место свободно, VIP/буткемп и ночь — 18+.")
    public ResponseEntity<BookingResponse> book(@AuthenticationPrincipal CurrentUser user,
                                                @Valid @RequestBody SelfBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(self.book(user, request));
    }

    @PostMapping("/bookings/{id}/cancel")
    @Operation(summary = "Отменить свою бронь (до начала игры, статус «Создано» или «Подтверждено»)")
    public BookingResponse cancel(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID id) {
        return self.cancel(user, id);
    }
}
