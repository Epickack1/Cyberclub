package ru.mirea.cyberclub.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ru.mirea.cyberclub.exception.ExportException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.service.StatisticsService;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Экспорт в Excel (Apache POI): четыре листа, жирные заголовки,
 * форматы дат и денег, автоширина, автофильтр, закреплённая строка.
 */
public class ExcelExporter implements Exporter {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String getTitle() {
        return "Excel (.xlsx)";
    }

    @Override
    public Path export(ExportData data, Path dir) {
        Path file = dir.resolve("cyberclub_export_" + LocalDateTime.now().format(FILE_TIME) + ".xlsx");
        try {
            Files.createDirectories(dir);
            try (Workbook wb = new XSSFWorkbook()) {
                CellStyle header = headerStyle(wb);
                CellStyle date = dateStyle(wb);
                CellStyle dateTime = dateTimeStyle(wb);
                CellStyle money = moneyStyle(wb);

                Sheet clients = wb.createSheet("Клиенты");
                fillClients(clients, data.clients(), header, date, dateTime);
                Sheet stations = wb.createSheet("Игровые места");
                fillStations(stations, data.stations(), header, money);
                Sheet bookings = wb.createSheet("Бронирования");
                fillBookings(bookings, data.bookings(), header, dateTime, money);
                Sheet stats = wb.createSheet("Статистика");
                fillIndicators(stats, data.indicators(), header);

                try (OutputStream out = Files.newOutputStream(file)) {
                    wb.write(out);
                }
            }
            return file;
        } catch (IOException e) {
            throw new ExportException("Не удалось записать файл " + file, e);
        }
    }

    private static void fillClients(Sheet sheet, List<Client> clients, CellStyle header,
                                    CellStyle date, CellStyle dateTime) {
        header(sheet, header, "ID", "Ник", "ФИО", "Телефон", "Email", "Дата рождения", "Создан");
        int r = 1;
        for (Client c : clients) {
            Row row = sheet.createRow(r++);
            text(row, 0, c.getId());
            text(row, 1, c.getNickname());
            text(row, 2, c.getFullName());
            text(row, 3, c.getPhone());
            text(row, 4, c.getEmail());
            dateCell(row, 5, c.getBirthDate(), date);
            dateCell(row, 6, c.getCreatedAt(), dateTime);
        }
        finish(sheet, clients.size() + 1, 7);
    }

    private static void fillStations(Sheet sheet, List<Station> stations, CellStyle header,
                                     CellStyle money) {
        header(sheet, header, "ID", "Название", "Тип", "Тариф (руб./ч)", "Характеристики", "Активно");
        int r = 1;
        for (Station s : stations) {
            Row row = sheet.createRow(r++);
            text(row, 0, s.getId());
            text(row, 1, s.getName());
            text(row, 2, s.getType() == null ? "" : s.getType().getTitle());
            moneyCell(row, 3, s.getHourlyRate(), money);
            text(row, 4, s.getSpecs());
            text(row, 5, s.isActive() ? "да" : "нет");
        }
        finish(sheet, stations.size() + 1, 6);
    }

    private static void fillBookings(Sheet sheet, List<Booking> bookings, CellStyle header,
                                     CellStyle dateTime, CellStyle money) {
        header(sheet, header, "ID", "Клиент", "Место", "Начало", "Конец",
                "Статус", "Стоимость", "Комментарий", "Создан");
        int r = 1;
        for (Booking b : bookings) {
            Row row = sheet.createRow(r++);
            text(row, 0, b.getId());
            text(row, 1, b.getClientNickname() != null ? b.getClientNickname()
                    : (b.getClientId() == null ? "" : String.valueOf(b.getClientId())));
            text(row, 2, b.getStationName() != null ? b.getStationName()
                    : (b.getStationId() == null ? "" : String.valueOf(b.getStationId())));
            dateCell(row, 3, b.getStartTime(), dateTime);
            dateCell(row, 4, b.getEndTime(), dateTime);
            text(row, 5, b.getStatus() == null ? "" : b.getStatus().getTitle());
            moneyCell(row, 6, b.getTotalPrice(), money);
            text(row, 7, b.getComment());
            dateCell(row, 8, b.getCreatedAt(), dateTime);
        }
        finish(sheet, bookings.size() + 1, 9);
    }

    private static void fillIndicators(Sheet sheet, List<StatisticsService.Indicator> indicators,
                                       CellStyle header) {
        header(sheet, header, "Показатель", "Значение");
        int r = 1;
        for (StatisticsService.Indicator i : indicators) {
            Row row = sheet.createRow(r++);
            text(row, 0, i.name());
            text(row, 1, i.value());
        }
        finish(sheet, indicators.size() + 1, 2);
    }

    private static void header(Sheet sheet, CellStyle style, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(style);
        }
    }

    /** Автоширина, автофильтр и закрепление строки заголовков. */
    private static void finish(Sheet sheet, int rows, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(0, 1);
        if (rows > 1) {
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rows - 1, 0, cols - 1));
        }
    }

    private static void text(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : String.valueOf(value));
    }

    private static void dateCell(Row row, int col, LocalDate value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(toDate(value.atStartOfDay()));
            cell.setCellStyle(style);
        }
    }

    private static void dateCell(Row row, int col, LocalDateTime value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(toDate(value));
            cell.setCellStyle(style);
        }
    }

    private static void moneyCell(Row row, int col, java.math.BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value == null) {
            cell.setCellValue("");
        } else {
            cell.setCellValue(value.doubleValue());
            cell.setCellStyle(style);
        }
    }

    private static Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static CellStyle dateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat(DateTimeUtil.DATE_PATTERN));
        return style;
    }

    private static CellStyle dateTimeStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat(DateTimeUtil.DATE_TIME_PATTERN));
        return style;
    }

    private static CellStyle moneyStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
        return style;
    }
}
