package ru.mirea.cyberclub.ui;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Меню «Бронирования»: CRUD основной сущности и смена статуса.
 */
public class BookingMenu extends AbstractMenu {

    private final BookingService bookingService;
    private final ClientService clientService;
    private final StationService stationService;
    private final TablePrinter tables;

    public BookingMenu(ConsoleIO io, BookingService bookingService, ClientService clientService,
                       StationService stationService) {
        super(io, "Бронирования");
        this.bookingService = bookingService;
        this.clientService = clientService;
        this.stationService = stationService;
        this.tables = new TablePrinter(io);
        add("Все бронирования", this::listAll);
        add("Создать бронирование", this::create);
        add("Найти бронирование по ID", this::findById);
        add("Изменить бронирование (место, время, комментарий)", this::update);
        add("Сменить статус бронирования", this::changeStatus);
        add("Удалить бронирование", this::delete);
        add("Предстоящие бронирования", this::listUpcoming);
    }

    private void listAll() {
        tables.printBookings(bookingService.getAll());
    }

    private void create() {
        io.println("Новое бронирование (пустая строка - отмена).");
        io.println("Клиенты:");
        tables.printClients(clientService.getAll());
        long clientId = io.readLong("Введите ID клиента:");

        io.println("Доступные игровые места:");
        tables.printStations(stationService.getActive());
        long stationId = io.readLong("Введите ID места:");

        LocalDateTime start = io.readDateTime("Начало");
        LocalDateTime end = io.readDateTime("Окончание");
        String comment = io.readOptionalLine("Комментарий (необязательно):");

        Booking booking = bookingService.create(clientId, stationId, start, end, comment);
        io.printSuccess("Бронирование #" + booking.getId() + " создано. Стоимость: "
                + DateTimeUtil.formatMoney(booking.getTotalPrice()) + ", статус: " + booking.getStatus().getTitle() + ".");
    }

    private void findById() {
        long id = io.readLong("Введите ID бронирования:");
        tables.printBooking(bookingService.getById(id));
    }

    private void update() {
        long id = io.readLong("Введите ID бронирования:");
        Booking booking = bookingService.getEditable(id);
        tables.printBooking(booking);
        io.println("Введите новые значения (пустая строка - оставить текущее):");
        io.println("Доступные игровые места:");
        tables.printStations(stationService.getActive());
        Long stationId = io.readOptionalLong("ID места [" + booking.getStationName() + "]:");
        LocalDateTime start = io.readOptionalDateTime("Начало [" + DateTimeUtil.format(booking.getStartTime()) + "]");
        LocalDateTime end = io.readOptionalDateTime("Окончание [" + DateTimeUtil.format(booking.getEndTime()) + "]");
        String comment = io.readOptionalLine("Комментарий [" + (booking.getComment() == null ? "" : booking.getComment())
                + "] (\"-\" - очистить):");
        if ("-".equals(comment)) {
            comment = "";
        }
        Booking updated = bookingService.update(id, stationId, start, end, comment);
        io.printSuccess("Бронирование #" + id + " обновлено. Новая стоимость: "
                + DateTimeUtil.formatMoney(updated.getTotalPrice()));
    }

    private void changeStatus() {
        long id = io.readLong("Введите ID бронирования:");
        Booking booking = bookingService.getById(id);
        io.println(booking.describe() + ", текущий статус: " + booking.getStatus().getTitle());
        List<BookingStatus> allowed = List.copyOf(booking.getStatus().allowedTransitions());
        if (allowed.isEmpty()) {
            io.println("Статус \"" + booking.getStatus().getTitle() + "\" конечный, переходы из него невозможны.");
            return;
        }
        io.println("Допустимые переходы: " + allowed.stream().map(BookingStatus::getTitle).collect(Collectors.joining(", ")));
        // Показываем все статусы: выбор запрещённого продемонстрирует срабатывание бизнес-правила
        BookingStatus newStatus = io.readEnum("Новый статус:", BookingStatus.values(), BookingStatus::getTitle);
        Booking updated = bookingService.changeStatus(id, newStatus);
        io.printSuccess("Статус бронирования #" + id + " изменён на \"" + updated.getStatus().getTitle() + "\".");
    }

    private void delete() {
        long id = io.readLong("Введите ID бронирования:");
        Booking booking = bookingService.getById(id);
        if (!io.confirm("Удалить " + booking.describe() + "?")) {
            io.println("Удаление отменено.");
            return;
        }
        bookingService.delete(id);
        io.printSuccess("Бронирование #" + id + " удалено.");
    }

    private void listUpcoming() {
        List<Booking> upcoming = bookingService.getUpcoming();
        tables.printBookings(upcoming);
    }
}
