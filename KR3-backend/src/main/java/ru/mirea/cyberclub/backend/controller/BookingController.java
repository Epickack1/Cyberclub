package ru.mirea.cyberclub.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.StationType;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.BookingDtos.StatusRequest;
import ru.mirea.cyberclub.backend.dto.PageResponse;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.service.BookingService;
import ru.mirea.cyberclub.backend.web.PageableFactory;

/** REST API бронирований — основной сущности. */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Бронирования")
public class BookingController {
    private final BookingService bookings;
    private final PageableFactory pageableFactory;

    public BookingController(BookingService bookings, PageableFactory pageableFactory) {
        this.bookings = bookings;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @Operation(summary = "Поиск, фильтры, сортировка и пагинация",
            description = "sort: number|start|price|status|client|station|created|updated, через запятую asc|desc. "
                    + "archive: ACTIVE (по умолчанию), ARCHIVED, ALL. priceOp: EQ|GT|GTE|LT|LTE вместе с price.")
    public PageResponse<BookingResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) StationType stationType,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(defaultValue = "ACTIVE") BookingFilter.ArchiveScope archive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingFilter.NumberOp priceOp,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "start,desc") String sort) {
        BookingFilter filter = new BookingFilter(q, stationId, stationType, clientId, status, paid, archive,
                from, to, priceOp, price);
        return PageResponse.from(bookings.search(filter, pageableFactory.bookings(page, size, sort)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Карточка брони по UUID")
    public BookingResponse get(@PathVariable UUID id) {
        return bookings.get(id);
    }

    @GetMapping("/number/{number}")
    @Operation(summary = "Карточка брони по номеру (#12)")
    public BookingResponse getByNumber(@PathVariable Long number) {
        return bookings.getByNumber(number);
    }

    @PostMapping
    @Operation(summary = "Создать бронь (стоимость считает сервер)")
    public ResponseEntity<BookingResponse> create(@Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookings.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить бронь (нужен актуальный version)")
    public BookingResponse update(@PathVariable UUID id, @Valid @RequestBody BookingRequest request) {
        return bookings.update(id, request);
    }

    @PostMapping("/{id}/status")
    @Operation(summary = "Сменить статус по допустимой схеме")
    public BookingResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return bookings.changeStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Убрать в архив (вместо удаления)")
    public BookingResponse archive(@PathVariable UUID id) {
        return bookings.archive(id);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Восстановить из архива")
    public BookingResponse restore(@PathVariable UUID id) {
        return bookings.restore(id);
    }

    @DeleteMapping("/{id}/permanent")
    @Operation(summary = "Удалить окончательно",
            description = "Работает только при PERMANENT_DELETE_ENABLED=true и заголовке X-Confirm-Delete: PERMANENTLY_DELETE.")
    public ResponseEntity<Void> permanentDelete(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Confirm-Delete", required = false) String confirmation) {
        bookings.permanentDelete(id, confirmation);
        return ResponseEntity.noContent().build();
    }
}
