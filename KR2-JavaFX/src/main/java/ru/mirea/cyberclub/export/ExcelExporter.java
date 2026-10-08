package ru.mirea.cyberclub.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ru.mirea.cyberclub.exception.ExportException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.service.StatisticsService;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Экспорт в Excel (Apache POI): лист «Бронирования» с настоящими датами и числами
 * (по ним можно сортировать и считать в Excel) и лист «Показатели».
 */
public class ExcelExporter implements Exporter {

    @Override
    public String extension() {
        return "xlsx";
    }

    @Override
    public String description() {
        return "Книга Excel";
    }

    @Override
    public void export(ExportData data, Path file) {
        try (Workbook wb = new XSSFWorkbook()) {
            CellStyle header = headerStyle(wb);
            CellStyle dateTime = wb.createCellStyle();
            dateTime.setDataFormat(wb.createDataFormat().getFormat(DateTimeUtil.DATE_TIME_PATTERN));
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Sheet sheet = wb.createSheet("Бронирования");
            writeHeader(sheet, header, CsvExporter.HEADER);
            int r = 1;
            for (Booking b : data.bookings()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(b.getId());
                row.createCell(1).setCellValue(str(b.getClientNickname()));
                row.createCell(2).setCellValue(str(b.getClientFullName()));
                row.createCell(3).setCellValue(str(b.getStationName()));
                row.createCell(4).setCellValue(b.getStationType() == null ? "" : b.getStationType().getTitle());
                dateCell(row, 5, b.getStartTime(), dateTime);
                dateCell(row, 6, b.getEndTime(), dateTime);
                row.createCell(7).setCellValue(DateTimeUtil.formatDuration(b.getDuration()));
                row.createCell(8).setCellValue(b.getStatus().getTitle());
                Cell price = row.createCell(9);
                price.setCellValue(b.getTotalPrice().doubleValue());
                price.setCellStyle(money);
                row.createCell(10).setCellValue(str(b.getComment()));
            }
            finish(sheet, data.bookings().size() + 1, CsvExporter.HEADER.length);

            Sheet stats = wb.createSheet("Показатели");
            writeHeader(stats, header, "Показатель", "Значение");
            r = 1;
            for (StatisticsService.Indicator indicator : data.indicators()) {
                Row row = stats.createRow(r++);
                row.createCell(0).setCellValue(indicator.name());
                row.createCell(1).setCellValue(indicator.value());
            }
            finish(stats, data.indicators().size() + 1, 2);

            try (OutputStream out = Files.newOutputStream(file)) {
                wb.write(out);
            }
        } catch (IOException e) {
            throw new ExportException("Не удалось записать файл " + file + ": " + e.getMessage(), e);
        }
    }

    private static void writeHeader(Sheet sheet, CellStyle style, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(titles[i]);
            cell.setCellStyle(style);
        }
    }

    /** Автоширина столбцов, закреплённая строка заголовков и автофильтр. */
    private static void finish(Sheet sheet, int rows, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
        sheet.createFreezePane(0, 1);
        if (rows > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rows - 1, 0, cols - 1));
        }
    }

    private static void dateCell(Row row, int col, LocalDateTime value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value);
            cell.setCellStyle(style);
        }
    }

    private static CellStyle headerStyle(Workbook wb) {
        Font font = wb.createFont();
        font.setBold(true);
        CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }
}
