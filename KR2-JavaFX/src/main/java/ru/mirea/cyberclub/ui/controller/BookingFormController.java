package ru.mirea.cyberclub.ui.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.model.BaseEntity;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.ui.util.Cells;
import ru.mirea.cyberclub.ui.util.FormInput;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Форма бронирования (booking-form.fxml). Клиент и игровое место выбираются из списков
 * (ComboBox), а не вводятся идентификаторами. Стоимость считает сервис и показывает
 * форма заранее, до сохранения.
 */
public class BookingFormController extends FormController<Booking> {

    private static final int MAX_COMMENT = 255;
    private static final BigDecimal MIN_HOURS = BigDecimal.valueOf(BookingService.MIN_DURATION_MINUTES)
            .divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    private static final BigDecimal MAX_HOURS = BigDecimal.valueOf(BookingService.MAX_DURATION_HOURS);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private ComboBox<Client> clientCombo;
    @FXML
    private Label clientHint;
    @FXML
    private ComboBox<Station> stationCombo;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField startField;
    @FXML
    private TextField durationField;
    @FXML
    private TextArea commentArea;
    @FXML
    private Label commentCounter;
    @FXML
    private Label priceLabel;

    private final AppContext context;
    private final BookingService bookingService;
    private Booking editing;

    public BookingFormController(AppContext context) {
        this.context = context;
        this.bookingService = context.bookings();
    }

    @FXML
    private void initialize() {
        FormInput.setupDatePicker(datePicker);
        Cells.display(clientCombo, Cells::clientText);
        Cells.display(stationCombo, Cells::stationText);

        registerField("client", clientCombo);
        registerField("station", stationCombo);
        registerField("date", datePicker);
        registerField("start", startField);
        registerField("duration", durationField);
        registerField("comment", commentArea);

        clientCombo.valueProperty().addListener((obs, old, client) -> updateClientHint());
        datePicker.valueProperty().addListener((obs, old, date) -> {
            updateClientHint();
            updatePrice();
        });
        stationCombo.valueProperty().addListener((obs, old, station) -> updatePrice());
        startField.textProperty().addListener((obs, old, text) -> updatePrice());
        durationField.textProperty().addListener((obs, old, text) -> updatePrice());
        commentArea.textProperty().addListener((obs, old, text) -> updateCommentCounter());
        updateCommentCounter();
    }

    /** Новая бронь: ближайший целый час, 2 часа; клиент может быть выбран заранее. */
    public void create(Client preselected) {
        loadLists(null);
        clientCombo.setValue(preselected == null ? null : find(clientCombo.getItems(), preselected.getId()));
        LocalDateTime next = LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0);
        datePicker.setValue(next.toLocalDate());
        startField.setText(next.toLocalTime().format(TIME));
        durationField.setText("2");
    }

    /** Редактирование: форма открывается с текущими значениями. Клиента у брони сменить нельзя. */
    public void edit(Booking booking) {
        this.editing = booking;
        loadLists(booking.getStationId());
        clientCombo.setValue(find(clientCombo.getItems(), booking.getClientId()));
        clientCombo.setDisable(true);
        stationCombo.setValue(find(stationCombo.getItems(), booking.getStationId()));
        datePicker.setValue(booking.getStartTime().toLocalDate());
        startField.setText(booking.getStartTime().toLocalTime().format(TIME));
        long minutes = booking.getDuration().toMinutes();
        durationField.setText(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString());
        commentArea.setText(booking.getComment());
    }

    @Override
    public String title() {
        return editing == null ? "Новое бронирование" : "Изменение бронирования #" + editing.getId();
    }

    @Override
    protected Booking submit() {
        Client client = FormInput.selected(clientCombo, "client", "Клиент");
        Station station = FormInput.selected(stationCombo, "station", "Игровое место");
        LocalDate date = FormInput.date(datePicker, "date", "Дата", true);
        LocalTime time = FormInput.time(startField, "start", "Начало");
        BigDecimal hours = FormInput.decimal(durationField, "duration", "Длительность, ч", MIN_HOURS, MAX_HOURS);
        String comment = FormInput.text(commentArea, "comment", "Комментарий", false, MAX_COMMENT);

        LocalDateTime start = date.atTime(time);
        LocalDateTime end = start.plusMinutes(toMinutes(hours));
        if (editing == null) {
            return bookingService.create(client.getId(), station.getId(), start, end, comment);
        }
        // Пустая строка (а не null) — чтобы сервис очистил комментарий, если его стёрли
        return bookingService.update(editing.getId(), station.getId(), start, end, comment == null ? "" : comment);
    }

    /** Активные места + текущее место редактируемой брони (даже если оно на обслуживании). */
    private void loadLists(Long currentStationId) {
        clientCombo.setItems(FXCollections.observableArrayList(context.clients().getAll()));
        List<Station> stations = context.stations().getAll().stream()
                .filter(s -> s.isActive() || s.getId().equals(currentStationId))
                .toList();
        stationCombo.setItems(FXCollections.observableArrayList(stations));
    }

    /** Подсказка о возрастных ограничениях выбранного клиента. */
    private void updateClientHint() {
        Client client = clientCombo.getValue();
        if (client == null) {
            clientHint.setText("Клиент выбирается из списка зарегистрированных");
            return;
        }
        LocalDate onDate = datePicker.getValue() == null ? LocalDate.now() : datePicker.getValue();
        int age = client.getAge(onDate);
        clientHint.setText(client.isAdult(onDate)
                ? "Возраст: " + age + " — доступны все зоны и ночное время"
                : "Возраст: " + age + " — VIP, буткемп и ночь (22:00–06:00) недоступны");
    }

    /** Предварительный расчёт стоимости тем же методом сервиса, что и при сохранении. */
    private void updatePrice() {
        Station station = stationCombo.getValue();
        if (station == null || datePicker.getValue() == null) {
            priceLabel.setText("Стоимость: выберите место и дату");
            return;
        }
        try {
            LocalTime time = FormInput.time(startField, "start", "Начало");
            BigDecimal hours = FormInput.decimal(durationField, "duration", "Длительность", MIN_HOURS, MAX_HOURS);
            LocalDateTime start = datePicker.getValue().atTime(time);
            LocalDateTime end = start.plusMinutes(toMinutes(hours));
            priceLabel.setText("Стоимость: " + DateTimeUtil.formatMoney(bookingService.calculatePrice(station, start, end))
                    + "  ·  до " + DateTimeUtil.format(end));
        } catch (AppException e) {
            // Поля ещё заполняются: вместо суммы — подсказка, ошибка покажется при сохранении
            priceLabel.setText("Стоимость: укажите время начала и длительность");
        }
    }

    private void updateCommentCounter() {
        int length = commentArea.getText() == null ? 0 : commentArea.getText().length();
        commentCounter.setText(length + " / " + MAX_COMMENT);
        commentCounter.getStyleClass().remove("counter-over");
        if (length > MAX_COMMENT) {
            commentCounter.getStyleClass().add("counter-over");
        }
    }

    private static long toMinutes(BigDecimal hours) {
        return hours.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static <T extends BaseEntity> T find(List<T> items, Long id) {
        return items.stream().filter(item -> Objects.equals(item.getId(), id)).findFirst().orElse(null);
    }
}
