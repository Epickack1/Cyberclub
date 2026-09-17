package ru.mirea.cyberclub.ui;

import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.BookingSortField;

/**
 * Меню «Сортировка бронирований»: пункты строятся по значениям
 * {@link BookingSortField}, направление спрашивается отдельно.
 */
public class SortMenu extends AbstractMenu {

    private final BookingService bookingService;
    private final TablePrinter tables;

    public SortMenu(ConsoleIO io, BookingService bookingService) {
        super(io, "Сортировка бронирований");
        this.bookingService = bookingService;
        this.tables = new TablePrinter(io);
        for (BookingSortField field : BookingSortField.values()) {
            add(field.getTitle(), () -> sortBy(field));
        }
    }

    private void sortBy(BookingSortField field) {
        boolean ascending = !io.confirm("Сортировать по убыванию?");
        io.println(field.getTitle() + (ascending ? " (по возрастанию)" : " (по убыванию)") + ":");
        tables.printBookings(bookingService.sorted(field, ascending));
    }
}
