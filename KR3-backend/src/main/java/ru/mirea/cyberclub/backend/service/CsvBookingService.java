package ru.mirea.cyberclub.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import ru.mirea.cyberclub.backend.domain.Booking;
import ru.mirea.cyberclub.backend.domain.Client;
import ru.mirea.cyberclub.backend.domain.Station;
import ru.mirea.cyberclub.backend.dto.BookingDtos.BookingRequest;
import ru.mirea.cyberclub.backend.dto.ImportDtos.ImportMode;
import ru.mirea.cyberclub.backend.dto.ImportDtos.ImportPreview;
import ru.mirea.cyberclub.backend.dto.ImportDtos.ImportResult;
import ru.mirea.cyberclub.backend.dto.ImportDtos.RowIssue;
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
 * Импорт и экспорт броней в CSV (разделитель «;», UTF-8 с BOM — файл сразу открывается в Excel).
 *
 * <p>Внешний идентификатор брони — её номер ({@code number}). Импорт идёт в два запроса:
 * {@link #preview} прогоняет все строки через те же проверки, что и обычное создание или изменение
 * (включая занятость места), внутри транзакции, которая в конце откатывается; {@link #apply}
 * применяет строки по одной, каждую в своей транзакции, и пропускает ошибочные.</p>
 */
@Service
public class CsvBookingService {
    public static final int MAX_ROWS = 5000;
    public static final int MAX_EXPORT_ROWS = 10_000;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_ISSUES = 500;

    static final String[] EXPORT_HEADERS = {"number", "client", "client_name", "station", "station_type", "start",
            "end", "status", "price", "paid", "risk", "tags", "comment", "archived", "created", "updated"};
    static final List<String> REQUIRED_HEADERS = List.of("client", "station", "start", "end");

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> IN = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

    private final BookingRepository bookings;
    private final ClientRepository clients;
    private final StationRepository stations;
    private final BookingService bookingService;
    private final AuditService audit;
    private final TransactionTemplate previewTx;

    public CsvBookingService(BookingRepository bookings, ClientRepository clients, StationRepository stations,
                             BookingService bookingService, AuditService audit, PlatformTransactionManager txManager) {
        this.bookings = bookings;
        this.clients = clients;
        this.stations = stations;
        this.bookingService = bookingService;
        this.audit = audit;
        this.previewTx = new TransactionTemplate(txManager);
    }

    // ------------------------------------------------------------------ экспорт

    /**
     * Экспорт с учётом того же фильтра, что у списка; порядок — по номеру брони.
     * Транзакция не read-only: в конце пишется событие журнала аудита.
     */
    @Transactional
    public void export(BookingFilter filter, OutputStream output) throws IOException {
        List<Booking> rows = bookings.findAll(BookingSpecifications.from(filter),
                PageRequest.of(0, MAX_EXPORT_ROWS, Sort.by("number"))).getContent();
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        // BOM пишется до CSVPrinter: иначе Excel не узнает UTF-8 и покажет кириллицу «кракозябрами».
        writer.write('﻿');
        CSVPrinter printer = new CSVPrinter(writer, format().setHeader(EXPORT_HEADERS).get());
        for (Booking b : rows) {
            printer.printRecord(b.getNumber(), safe(b.getClient().getNickname()), safe(b.getClient().getFullName()),
                    safe(b.getStation().getName()), b.getStation().getType().name(), OUT.format(b.getStartTime()),
                    OUT.format(b.getEndTime()), b.getStatus().name(), b.getTotalPrice().toPlainString(), b.isPaid(),
                    b.getRiskLevel().name(), String.join("|", b.getTags()), safe(b.getComment()), b.isArchived(),
                    b.getCreatedAt(), b.getUpdatedAt());
        }
        printer.flush();
        audit.record("BOOKING_EXPORT", AuditService.BOOKING, null, "Экспорт CSV: " + rows.size() + " броней");
    }

    // ------------------------------------------------------------------ импорт

    /** Предварительная проверка: ничего не сохраняет, считает корректные и ошибочные строки. */
    public ImportPreview preview(MultipartFile file, ImportMode mode) {
        Parsed parsed = parse(file);
        List<RowIssue> issues = new ArrayList<>(parsed.issues());
        int[] counts = new int[2]; // [создать, обновить]
        previewTx.executeWithoutResult(status -> {
            for (Row row : parsed.rows()) {
                RowIssue issue = applyRow(row, mode, counts);
                if (issue != null) {
                    issues.add(issue);
                }
            }
            status.setRollbackOnly();
        });
        int invalid = (int) issues.stream().map(RowIssue::row).distinct().count();
        return new ImportPreview(mode, parsed.total(), parsed.total() - invalid, invalid, counts[0], counts[1],
                limit(sorted(issues)));
    }

    /** Применение: корректные строки сохраняются, ошибочные пропускаются с объяснением. */
    public ImportResult apply(MultipartFile file, ImportMode mode) {
        Parsed parsed = parse(file);
        List<RowIssue> issues = new ArrayList<>(parsed.issues());
        int[] counts = new int[2];
        for (Row row : parsed.rows()) {
            RowIssue issue = applyRow(row, mode, counts);
            if (issue != null) {
                issues.add(issue);
            }
        }
        int skipped = (int) issues.stream().map(RowIssue::row).distinct().count();
        audit.record("BOOKING_IMPORT", AuditService.BOOKING, null, "Импорт CSV (" + mode + ", файл "
                + file.getOriginalFilename() + "): создано " + counts[0] + ", обновлено " + counts[1]
                + ", пропущено " + skipped);
        return new ImportResult(mode, parsed.total(), counts[0], counts[1], skipped, limit(sorted(issues)));
    }

    /** Одна строка: создание или обновление через BookingService; ошибка правила — замечание к строке. */
    private RowIssue applyRow(Row row, ImportMode mode, int[] counts) {
        try {
            boolean exists = row.number() != null && bookings.findByNumber(row.number()).isPresent();
            if (row.number() != null && exists && mode == ImportMode.CREATE_ONLY) {
                return new RowIssue(row.line(), "number", "Бронь #" + row.number()
                        + " уже есть — в режиме «только новые» строка пропускается.");
            }
            if (row.number() != null && !exists && mode == ImportMode.UPSERT_BY_NUMBER) {
                return new RowIssue(row.line(), "number", "Брони #" + row.number()
                        + " нет — обновить нельзя. Чтобы создать новую, оставьте номер пустым.");
            }
            if (exists) {
                bookingService.updateByNumber(row.number(), row.request());
                counts[1]++;
            } else {
                bookingService.create(row.request());
                counts[0]++;
            }
            return null;
        } catch (BusinessRuleException | BadRequestException | ConflictException | NotFoundException ex) {
            String field = ex instanceof BadRequestException bad ? bad.getField() : null;
            return new RowIssue(row.line(), field, ex.getMessage());
        } catch (ObjectOptimisticLockingFailureException ex) {
            return new RowIssue(row.line(), null, "Бронь изменена другим пользователем во время импорта.");
        } catch (DataIntegrityViolationException ex) {
            return new RowIssue(row.line(), null, "Строка нарушает ограничения данных (например, место уже занято).");
        }
    }

    private Parsed parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file", "Файл не выбран или пустой.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BadRequestException("file", "Файл больше 2 МБ.");
        }
        String text;
        try (InputStream in = file.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BadRequestException("file", "Не удалось прочитать файл.");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        String firstLine = text.lines().findFirst().orElse("");
        char delimiter = firstLine.contains(";") ? ';' : ',';

        List<Row> rows = new ArrayList<>();
        List<RowIssue> issues = new ArrayList<>();
        int total = 0;
        try (Reader reader = new StringReader(text);
             CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setHeader()
                     .setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).get().parse(reader)) {
            Map<String, Integer> headers = new HashMap<>();
            parser.getHeaderMap().forEach((name, index) -> headers.put(name.trim().toLowerCase(Locale.ROOT), index));
            List<String> missing = REQUIRED_HEADERS.stream().filter(h -> !headers.containsKey(h)).toList();
            if (!missing.isEmpty()) {
                throw new BadRequestException("file", "В заголовке нет колонок: " + String.join(", ", missing)
                        + ". Нужны: " + String.join(", ", REQUIRED_HEADERS) + "; необязательные: number, paid, comment, tags.");
            }
            Set<Long> numbersInFile = new HashSet<>();
            for (CSVRecord record : parser) {
                total++;
                if (total > MAX_ROWS) {
                    throw new BadRequestException("file", "В файле больше " + MAX_ROWS + " строк.");
                }
                long line = parser.getCurrentLineNumber();
                List<RowIssue> rowIssues = new ArrayList<>();
                Row row = toRow(record, headers, line, rowIssues);
                if (row != null && row.number() != null && !numbersInFile.add(row.number())) {
                    rowIssues.add(new RowIssue(line, "number", "Номер #" + row.number() + " повторяется в файле."));
                }
                if (rowIssues.isEmpty()) {
                    rows.add(row);
                } else {
                    issues.addAll(rowIssues);
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException ex) {
            throw new BadRequestException("file", "Файл не похож на CSV: " + ex.getMessage());
        }
        return new Parsed(total, rows, issues);
    }

    /** Разбор строки в запрос на создание или изменение. Ошибки полей копятся в {@code issues}. */
    private Row toRow(CSVRecord record, Map<String, Integer> headers, long line, List<RowIssue> issues) {
        Long number = null;
        String numberText = cell(record, headers, "number");
        if (!numberText.isEmpty()) {
            try {
                number = Long.parseLong(numberText.startsWith("#") ? numberText.substring(1) : numberText);
            } catch (NumberFormatException ex) {
                issues.add(new RowIssue(line, "number", "Номер брони — целое число, получено \"" + numberText + "\"."));
            }
        }
        Client client = null;
        String nickname = cell(record, headers, "client");
        if (nickname.isEmpty()) {
            issues.add(new RowIssue(line, "client", "Укажите ник клиента."));
        } else {
            client = clients.findByNicknameIgnoreCase(nickname).orElse(null);
            if (client == null) {
                issues.add(new RowIssue(line, "client", "Клиент с ником \"" + nickname + "\" не найден."));
            }
        }
        Station station = null;
        String stationName = cell(record, headers, "station");
        if (stationName.isEmpty()) {
            issues.add(new RowIssue(line, "station", "Укажите игровое место."));
        } else {
            station = stations.findByNameIgnoreCase(stationName).orElse(null);
            if (station == null) {
                issues.add(new RowIssue(line, "station", "Игровое место \"" + stationName + "\" не найдено."));
            }
        }
        LocalDateTime start = dateTime(cell(record, headers, "start"), "start", line, issues);
        LocalDateTime end = dateTime(cell(record, headers, "end"), "end", line, issues);
        Boolean paid = bool(cell(record, headers, "paid"), line, issues);
        String comment = cell(record, headers, "comment");
        if (comment.length() > 255) {
            issues.add(new RowIssue(line, "comment", "Комментарий не длиннее 255 символов."));
        }
        Set<String> tags = new TreeSet<>();
        String tagsText = cell(record, headers, "tags");
        if (!tagsText.isEmpty()) {
            try {
                tags = Tags.normalizeAll(Arrays.stream(tagsText.split("[|,]")).map(String::trim)
                        .filter(t -> !t.isEmpty()).toList(), "tags");
            } catch (BadRequestException ex) {
                issues.add(new RowIssue(line, "tags", ex.getMessage()));
            }
        }
        if (!issues.isEmpty()) {
            return null;
        }
        return new Row(line, number, new BookingRequest(client.getId(), station.getId(), start, end,
                comment.isEmpty() ? null : comment, paid, tags, null));
    }

    private static LocalDateTime dateTime(String value, String field, long line, List<RowIssue> issues) {
        if (value.isEmpty()) {
            issues.add(new RowIssue(line, field, "Укажите дату и время (" + field + ")."));
            return null;
        }
        for (DateTimeFormatter format : IN) {
            try {
                return LocalDateTime.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // пробуем следующий формат
            }
        }
        issues.add(new RowIssue(line, field, "Дата \"" + value + "\": ожидается ГГГГ-ММ-ДД ЧЧ:ММ или ДД.ММ.ГГГГ ЧЧ:ММ."));
        return null;
    }

    private static Boolean bool(String value, long line, List<RowIssue> issues) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "", "false", "0", "нет", "no" -> false;
            case "true", "1", "да", "yes" -> true;
            default -> {
                issues.add(new RowIssue(line, "paid", "Оплата: true/false или да/нет, получено \"" + value + "\"."));
                yield false;
            }
        };
    }

    private static String cell(CSVRecord record, Map<String, Integer> headers, String name) {
        Integer index = headers.get(name);
        if (index == null || index >= record.size()) {
            return "";
        }
        String value = record.get(index).trim();
        // Снимаем защитный апостроф, который ставит экспорт (см. safe()).
        if (value.length() > 1 && value.charAt(0) == '\'' && "=+-@".indexOf(value.charAt(1)) >= 0) {
            value = value.substring(1);
        }
        return value;
    }

    /**
     * Защита от CSV-инъекции: ячейка, начинающаяся с =, +, -, @, в Excel стала бы формулой.
     * Такой текст экспортируется с апострофом впереди.
     */
    static String safe(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return "=+-@\t\r".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static CSVFormat.Builder format() {
        return CSVFormat.DEFAULT.builder().setDelimiter(';').setRecordSeparator("\r\n");
    }

    private static List<RowIssue> sorted(List<RowIssue> issues) {
        return issues.stream().sorted((a, b) -> Long.compare(a.row(), b.row())).toList();
    }

    private static List<RowIssue> limit(List<RowIssue> issues) {
        return issues.size() <= MAX_ISSUES ? issues : issues.subList(0, MAX_ISSUES);
    }

    private record Row(long line, Long number, BookingRequest request) {
    }

    private record Parsed(int total, List<Row> rows, List<RowIssue> issues) {
    }
}
