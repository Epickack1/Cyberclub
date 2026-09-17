package ru.mirea.cyberclub.ui;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.repository.SchemaInspector.TableData;
import ru.mirea.cyberclub.service.StatisticsService.Indicator;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Вывод списков сущностей в виде выровненных текстовых таблиц.
 */
public class TablePrinter {

    private static final int MAX_COLUMN_WIDTH = 40;

    private final ConsoleIO io;

    public TablePrinter(ConsoleIO io) {
        this.io = io;
    }

    public void printClients(List<Client> clients) {
        LocalDate today = LocalDate.now();
        List<List<String>> rows = new ArrayList<>();
        for (Client c : clients) {
            rows.add(List.of(str(c.getId()), c.getNickname(), c.getFullName(), c.getPhone(), c.getEmail(),
                    DateTimeUtil.format(c.getBirthDate()), String.valueOf(c.getAge(today))));
        }
        print(List.of("ID", "Ник", "ФИО", "Телефон", "Email", "Дата рождения", "Возраст"), rows);
    }

    public void printStations(List<Station> stations) {
        List<List<String>> rows = new ArrayList<>();
        for (Station s : stations) {
            rows.add(List.of(str(s.getId()), s.getName(), s.getType().getTitle(),
                    DateTimeUtil.formatMoney(s.getHourlyRate()) + "/ч", str(s.getSpecs()),
                    s.isActive() ? "активно" : "НА ОБСЛУЖИВАНИИ"));
        }
        print(List.of("ID", "Название", "Тип", "Тариф", "Характеристики", "Состояние"), rows);
    }

    public void printBookings(List<Booking> bookings) {
        List<List<String>> rows = new ArrayList<>();
        for (Booking b : bookings) {
            rows.add(List.of(str(b.getId()), b.getClientNickname(), b.getStationName(),
                    DateTimeUtil.format(b.getStartTime()), DateTimeUtil.format(b.getEndTime()),
                    DateTimeUtil.formatDuration(b.getDuration()), b.getStatus().getTitle(),
                    DateTimeUtil.formatMoney(b.getTotalPrice()), str(b.getComment())));
        }
        print(List.of("ID", "Клиент", "Место", "Начало", "Окончание", "Длит.", "Статус", "Стоимость", "Комментарий"), rows);
    }

    public void printBooking(Booking b) {
        io.println("ID:          " + b.getId());
        io.println("Клиент:      " + b.getClientNickname() + " (ID " + b.getClientId() + ")");
        io.println("Место:       " + b.getStationName() + " (ID " + b.getStationId() + ")");
        io.println("Начало:      " + DateTimeUtil.format(b.getStartTime()));
        io.println("Окончание:   " + DateTimeUtil.format(b.getEndTime()));
        io.println("Длительность: " + DateTimeUtil.formatDuration(b.getDuration()));
        io.println("Статус:      " + b.getStatus().getTitle());
        io.println("Стоимость:   " + DateTimeUtil.formatMoney(b.getTotalPrice()));
        io.println("Комментарий: " + str(b.getComment()));
        io.println("Создано:     " + DateTimeUtil.format(b.getCreatedAt()));
    }

    public void printIndicators(List<Indicator> indicators) {
        int width = indicators.stream().mapToInt(i -> i.name().length()).max().orElse(10);
        for (Indicator i : indicators) {
            io.println(pad(i.name(), width) + " : " + i.value());
        }
    }

    public void printTableData(TableData data) {
        print(data.columnNames(), data.rows());
    }

    /** Универсальная печать: заголовки + строки, ширина колонок по содержимому. */
    public void print(List<String> headers, List<List<String>> rows) {
        if (rows.isEmpty()) {
            io.println("(записей нет)");
            return;
        }
        int columns = headers.size();
        int[] widths = new int[columns];
        for (int i = 0; i < columns; i++) {
            widths[i] = Math.min(MAX_COLUMN_WIDTH, headers.get(i).length());
        }
        for (List<String> row : rows) {
            for (int i = 0; i < columns; i++) {
                widths[i] = Math.max(widths[i], Math.min(MAX_COLUMN_WIDTH, cut(row.get(i)).length()));
            }
        }
        io.println(formatRow(headers, widths));
        StringBuilder separator = new StringBuilder();
        for (int i = 0; i < columns; i++) {
            if (i > 0) {
                separator.append("-+-");
            }
            separator.append("-".repeat(widths[i]));
        }
        io.println(separator.toString());
        for (List<String> row : rows) {
            io.println(formatRow(row, widths));
        }
        io.println("Всего записей: " + rows.size());
    }

    private static String formatRow(List<String> cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(pad(cut(cells.get(i)), widths[i]));
        }
        return sb.toString();
    }

    private static String cut(String value) {
        String text = value == null ? "" : value;
        return text.length() <= MAX_COLUMN_WIDTH ? text : text.substring(0, MAX_COLUMN_WIDTH - 1) + "...";
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text : text + " ".repeat(width - text.length());
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
