package ru.mirea.cyberclub.export;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import ru.mirea.cyberclub.exception.ExportException;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.service.StatisticsService;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Экспорт в CSV: четыре файла в отдельной папке, разделитель «;»,
 * UTF-8 с BOM (корректно открывается в Excel), экранирование по RFC 4180.
 */
public class CsvExporter implements Exporter {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    @Override
    public String getTitle() {
        return "CSV";
    }

    @Override
    public Path export(ExportData data, Path dir) {
        Path folder = dir.resolve("cyberclub_csv_" + LocalDateTime.now().format(FILE_TIME));
        try {
            Files.createDirectories(folder);
            writeClients(folder.resolve("clients.csv"), data.clients());
            writeStations(folder.resolve("stations.csv"), data.stations());
            writeBookings(folder.resolve("bookings.csv"), data.bookings());
            writeIndicators(folder.resolve("statistics.csv"), data.indicators());
            return folder;
        } catch (IOException e) {
            throw new ExportException("Не удалось записать CSV-файлы в " + folder, e);
        }
    }

    private static void writeClients(Path file, List<Client> clients) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Ник", "ФИО", "Телефон", "Email", "Дата рождения", "Создан"});
        for (Client c : clients) {
            rows.add(new String[]{
                    str(c.getId()), str(c.getNickname()), str(c.getFullName()),
                    str(c.getPhone()), str(c.getEmail()),
                    DateTimeUtil.format(c.getBirthDate()), DateTimeUtil.format(c.getCreatedAt())
            });
        }
        writeCsv(file, rows);
    }

    private static void writeStations(Path file, List<Station> stations) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Название", "Тип", "Тариф (руб./ч)", "Характеристики", "Активно"});
        for (Station s : stations) {
            rows.add(new String[]{
                    str(s.getId()), str(s.getName()),
                    s.getType() == null ? "" : s.getType().getTitle(),
                    s.getHourlyRate() == null ? "" : s.getHourlyRate().toPlainString(),
                    str(s.getSpecs()), s.isActive() ? "да" : "нет"
            });
        }
        writeCsv(file, rows);
    }

    private static void writeBookings(Path file, List<Booking> bookings) throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Клиент", "Место", "Начало", "Конец",
                "Статус", "Стоимость", "Комментарий", "Создан"});
        for (Booking b : bookings) {
            String client = b.getClientNickname() != null ? b.getClientNickname()
                    : str(b.getClientId());
            String station = b.getStationName() != null ? b.getStationName()
                    : str(b.getStationId());
            rows.add(new String[]{
                    str(b.getId()), client, station,
                    DateTimeUtil.format(b.getStartTime()), DateTimeUtil.format(b.getEndTime()),
                    b.getStatus() == null ? "" : b.getStatus().getTitle(),
                    b.getTotalPrice() == null ? "" : b.getTotalPrice().toPlainString(),
                    str(b.getComment()), DateTimeUtil.format(b.getCreatedAt())
            });
        }
        writeCsv(file, rows);
    }

    private static void writeIndicators(Path file, List<StatisticsService.Indicator> indicators)
            throws IOException {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Показатель", "Значение"});
        for (StatisticsService.Indicator i : indicators) {
            rows.add(new String[]{i.name(), i.value()});
        }
        writeCsv(file, rows);
    }

    private static void writeCsv(Path file, List<String[]> rows) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('\uFEFF'); // BOM — чтобы Excel открыл файл в UTF-8
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
        }
    }

    /** Экранирование значения по RFC 4180: кавычки, точка с запятой, переводы строк. */
    private static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean quote = value.indexOf(';') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        if (!quote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
