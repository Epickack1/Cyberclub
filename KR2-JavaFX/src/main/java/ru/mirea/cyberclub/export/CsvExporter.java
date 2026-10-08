package ru.mirea.cyberclub.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import ru.mirea.cyberclub.exception.ExportException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Экспорт бронирований в CSV: разделитель «;», UTF-8 с BOM
 * (файл корректно открывается в Excel), экранирование по RFC 4180.
 */
public class CsvExporter implements Exporter {

    static final String[] HEADER = {"№", "Клиент", "ФИО клиента", "Место", "Тип места", "Начало", "Окончание",
            "Длительность", "Статус", "Стоимость, руб.", "Комментарий"};

    @Override
    public String extension() {
        return "csv";
    }

    @Override
    public String description() {
        return "CSV (разделитель ;)";
    }

    @Override
    public void export(ExportData data, Path file) {
        List<String[]> rows = new ArrayList<>();
        rows.add(HEADER);
        for (Booking b : data.bookings()) {
            rows.add(new String[]{
                    String.valueOf(b.getId()),
                    str(b.getClientNickname()),
                    str(b.getClientFullName()),
                    str(b.getStationName()),
                    b.getStationType() == null ? "" : b.getStationType().getTitle(),
                    DateTimeUtil.format(b.getStartTime()),
                    DateTimeUtil.format(b.getEndTime()),
                    DateTimeUtil.formatDuration(b.getDuration()),
                    b.getStatus().getTitle(),
                    b.getTotalPrice() == null ? "" : b.getTotalPrice().toPlainString(),
                    str(b.getComment())
            });
        }
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM: Excel поймёт, что файл в UTF-8
            for (String[] row : rows) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        line.append(';');
                    }
                    line.append(escape(row[i]));
                }
                w.write(line.toString());
                w.newLine();
            }
        } catch (IOException e) {
            throw new ExportException("Не удалось записать файл " + file + ": " + e.getMessage(), e);
        }
    }

    /** Значение с «;», кавычками или переводом строки заключается в кавычки, кавычки удваиваются. */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean quote = value.indexOf(';') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        return quote ? '"' + value.replace("\"", "\"\"") + '"' : value;
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }
}
