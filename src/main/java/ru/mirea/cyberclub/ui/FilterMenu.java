package ru.mirea.cyberclub.ui;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;

/**
 * Меню «Фильтрация бронирований»: по статусу, типу места, периоду,
 * клиенту (SQL) и по диапазону стоимости (Stream API).
 */
public class FilterMenu extends AbstractMenu {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final TablePrinter tables;

    public FilterMenu(ConsoleIO io, BookingService bookingService, ClientService clientService) {
        super(io, "Фильтрация бронирований");
        this.bookingService = bookingService;
        this.clientService = clientService;
        this.tables = new TablePrinter(io);
        add("По статусу", this::byStatus);
        add("По типу игрового места", this::byStationType);
        add("По периоду (диапазон дат начала)", this::byPeriod);
        add("По клиенту", this::byClient);
        add("По диапазону стоимости", this::byPrice);
    }

    private void byStatus() {
        BookingStatus status = io.readEnum("Статус:", BookingStatus.values(), BookingStatus::getTitle);
        tables.printBookings(bookingService.filterByStatus(status));
    }

    private void byStationType() {
        StationType type = io.readEnum("Тип игрового места:", StationType.values(), StationType::getTitle);
        tables.printBookings(bookingService.filterByStationType(type));
    }

    private void byPeriod() {
        LocalDateTime from = io.readDateTime("Начало периода");
        LocalDateTime to = io.readDateTime("Конец периода");
        tables.printBookings(bookingService.filterByPeriod(from, to));
    }

    private void byClient() {
        tables.printClients(clientService.getAll());
        long clientId = io.readLong("Введите ID клиента:");
        tables.printBookings(bookingService.filterByClient(clientId));
    }

    private void byPrice() {
        BigDecimal min = io.readOptionalMoney("Минимальная стоимость (пусто - без ограничения):");
        BigDecimal max = io.readOptionalMoney("Максимальная стоимость (пусто - без ограничения):");
        tables.printBookings(bookingService.filterByPriceRange(min, max));
    }
}
