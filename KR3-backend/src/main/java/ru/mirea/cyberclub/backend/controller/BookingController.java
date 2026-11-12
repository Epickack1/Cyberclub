package ru.mirea.cyberclub.backend.controller;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ru.mirea.cyberclub.backend.domain.BookingStatus;
import ru.mirea.cyberclub.backend.domain.RiskLevel;
import ru.mirea.cyberclub.backend.domain.StationType;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingResponse;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BulkRequest;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BulkResult;
import ru.mirea.cyberclub.backend.dto.BookingDtos.StatusRequest;
import ru.mirea.cyberclub.backend.dto.ClientSelfDtos.PaymentRequest;
import ru.mirea.cyberclub.backend.dto.ImportDtos.ImportMode;
import ru.mirea.cyberclub.backend.dto.ImportDtos.ImportPreview;
import ru.mirea.cyberclub.backend.dto.ImportDtos.ImportResult;
import ru.mirea.cyberclub.backend.dto.PageResponse;
import ru.mirea.cyberclub.backend.filter.BookingFilter;
import ru.mirea.cyberclub.backend.service.BookingService;
import ru.mirea.cyberclub.backend.service.BulkBookingService;
import ru.mirea.cyberclub.backend.service.CsvBookingService;
import ru.mirea.cyberclub.backend.web.PageableFactory;

/** REST API бронирований — основной сущности. */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Бронирования")
public class BookingController {
    private final BookingService bookings;
    private final BulkBookingService bulk;
    private final CsvBookingService csv;
    private final PageableFactory pageableFactory;

    public BookingController(BookingService bookings, BulkBookingService bulk, CsvBookingService csv,
                             PageableFactory pageableFactory) {
        this.bookings = bookings;
        this.bulk = bulk;
        this.csv = csv;
        this.pageableFactory = pageableFactory;
    }

    @GetMapping
    @Operation(summary = "Поиск, фильтры, сортировка и пагинация",
            description = "q — номер (#12), ник, ФИО, место или комментарий. "
                    + "sort: number|start|price|status|client|station|created|updated, через запятую asc|desc. "
                    + "archive: ACTIVE (по умолчанию), ARCHIVED, ALL. priceOp: EQ|GT|GTE|LT|LTE вместе с price. "
                    + "riskLevel: NONE|LOW|MEDIUM|HIGH (риск неоплаты, вычисляется сервером). tag — один тег.")
    public PageResponse<BookingResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) StationType stationType,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "ACTIVE") BookingFilter.ArchiveScope archive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingFilter.NumberOp priceOp,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "start,desc") String sort) {
        BookingFilter filter = new BookingFilter(q, stationId, stationType, clientId, status, paid, riskLevel, tag, archive,
                from, to, priceOp, price);
        return PageResponse.from(bookings.search(filter, pageableFactory.bookings(page, size, sort)));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    @Operation(summary = "Экспорт в CSV с теми же фильтрами, что у списка",
            description = "Разделитель «;», UTF-8 с BOM (открывается в Excel). Не больше 10 000 строк, по номеру брони.")
    public void export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) StationType stationType,
            @RequestParam(required = false) Long clientId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Boolean paid,
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "ACTIVE") BookingFilter.ArchiveScope archive,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BookingFilter.NumberOp priceOp,
            @RequestParam(required = false) BigDecimal price,
            HttpServletResponse response) throws IOException {
        BookingFilter filter = new BookingFilter(q, stationId, stationType, clientId, status, paid, riskLevel, tag, archive,
                from, to, priceOp, price);
        String fileName = "bookings-" + LocalDate.now() + ".csv";
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
        csv.export(filter, response.getOutputStream());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Импорт CSV, шаг 1: проверка файла без сохранения",
            description = "Колонки: client (ник), station (название), start, end — обязательные; number, paid, comment, "
                    + "tags (через |) — необязательные. Даты: ГГГГ-ММ-ДД ЧЧ:ММ. Подходит файл, полученный экспортом. "
                    + "mode: CREATE_ONLY — только новые; UPSERT_BY_NUMBER — обновить брони с этими номерами.")
    public ImportPreview previewImport(@RequestPart("file") MultipartFile file,
                                       @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.preview(file, mode);
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Импорт CSV, шаг 2: применить (корректные строки сохраняются, ошибочные пропускаются)")
    public ImportResult applyImport(@RequestPart("file") MultipartFile file,
                                    @RequestParam(defaultValue = "CREATE_ONLY") ImportMode mode) {
        return csv.apply(file, mode);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Массовая операция над набором броней",
            description = "action: ARCHIVE | RESTORE | SET_STATUS (нужен status) | ADD_TAG | REMOVE_TAG (нужен tag) | MARK_PAID. "
                    + "Каждая бронь обрабатывается отдельно; в ответе — успешные и ошибки по каждой.")
    public BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return bulk.apply(request);
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

    @PostMapping("/{id}/confirm-payment")
    @Operation(summary = "Подтвердить оплату (клиент заплатил на стойке)",
            description = "Администратор зала или системы. Брони с сайта создаются неоплаченными.")
    public BookingResponse confirmPayment(@PathVariable UUID id,
                                          @RequestBody(required = false) PaymentRequest request) {
        return bookings.confirmPayment(id, request == null ? null : request.version());
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
