package ru.mirea.cyberclub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;

class ExportServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 7, 18, 0);

    @TempDir
    Path dir;

    private final StatisticsService statistics = new StatisticsService(() -> {
        throw new UnsupportedOperationException("общая статистика в экспорте не используется");
    });
    private final ExportService service = new ExportService(statistics);

    private final List<Booking> rows = List.of(
            BookingFilterTest.booking(1, 1, "Shadow", "Иванов Иван", "PC-01", StationType.STANDARD, START, 3,
                    BookingStatus.COMPLETED, "450.00", "Катка; \"CS2\""),
            BookingFilterTest.booking(2, 2, "Nova_Q", "Петрова Анна", "VIP-01", StationType.VIP, START.plusDays(1), 4,
                    BookingStatus.CANCELLED, "1200.00", null));

    @Test
    @DisplayName("CSV: BOM, заголовок, разделитель ; и экранирование кавычек")
    void csvExport() throws Exception {
        Path file = dir.resolve("bookings.csv");
        assertThat(service.export(rows, file)).isEqualTo(2);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).startsWith("﻿№;Клиент;ФИО клиента;Место");
        List<String> lines = content.lines().toList();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).startsWith("1;Shadow;Иванов Иван;PC-01;Стандарт;07.10.2026 18:00;07.10.2026 21:00;3 ч;Завершено;450.00;")
                .endsWith("\"Катка; \"\"CS2\"\"\"");
    }

    @Test
    @DisplayName("XLSX: лист бронирований и лист показателей по выборке")
    void excelExport() throws Exception {
        Path file = dir.resolve("bookings.xlsx");
        service.export(rows, file);

        try (InputStream in = Files.newInputStream(file); Workbook wb = new XSSFWorkbook(in)) {
            Sheet bookings = wb.getSheet("Бронирования");
            assertThat(bookings.getLastRowNum()).isEqualTo(2);
            assertThat(bookings.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Shadow");
            assertThat(bookings.getRow(2).getCell(9).getNumericCellValue()).isEqualTo(1200.0);

            Sheet stats = wb.getSheet("Показатели");
            assertThat(stats.getRow(1).getCell(0).getStringCellValue()).isEqualTo("Бронирований в выборке");
            assertThat(stats.getRow(1).getCell(1).getStringCellValue()).isEqualTo("2");
        }
    }

    @Test
    @DisplayName("Пустая выборка и неизвестное расширение отклоняются")
    void rejectsBadInput() {
        assertThatThrownBy(() -> service.export(List.of(), dir.resolve("a.csv")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Нет данных");
        assertThatThrownBy(() -> service.export(rows, dir.resolve("a.pdf")))
                .isInstanceOf(ValidationException.class).hasMessageContaining(".xlsx или .csv");
    }

    @Test
    @DisplayName("Показатели выборки: отменённые брони не входят в денежные показатели")
    void selectionIndicators() {
        var indicators = statistics.forBookings(rows);
        assertThat(indicators).hasSizeGreaterThanOrEqualTo(5);
        assertThat(indicators.get(1).value()).isEqualTo("450.00 руб.");
        assertThat(indicators).anySatisfy(i -> {
            assertThat(i.name()).isEqualTo("Доля отменённых");
            assertThat(i.value()).isEqualTo("50.0 %");
        });
    }
}
