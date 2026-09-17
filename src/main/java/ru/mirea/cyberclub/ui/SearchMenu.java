package ru.mirea.cyberclub.ui;

import java.time.LocalDate;

import ru.mirea.cyberclub.service.BookingService;

/**
 * Меню «Поиск бронирований»: четыре способа поиска, все выполняются
 * параметризованными SQL-запросами (ILIKE / диапазон дат).
 */
public class SearchMenu extends AbstractMenu {

    private final BookingService bookingService;
    private final TablePrinter tables;

    public SearchMenu(ConsoleIO io, BookingService bookingService) {
        super(io, "Поиск бронирований");
        this.bookingService = bookingService;
        this.tables = new TablePrinter(io);
        add("По нику клиента", this::byClient);
        add("По названию игрового места", this::byStation);
        add("По дате", this::byDate);
        add("По тексту комментария", this::byComment);
    }

    private void byClient() {
        String query = io.readLine("Ник клиента (или его часть):");
        tables.printBookings(bookingService.searchByClientNickname(query));
    }

    private void byStation() {
        String query = io.readLine("Название места (или его часть, например VIP):");
        tables.printBookings(bookingService.searchByStationName(query));
    }

    private void byDate() {
        LocalDate date = io.readDate("Дата начала бронирования");
        tables.printBookings(bookingService.searchByDate(date));
    }

    private void byComment() {
        String query = io.readLine("Текст комментария (или его часть):");
        tables.printBookings(bookingService.searchByComment(query));
    }
}
