package ru.mirea.cyberclub.ui.controller;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.export.Exporter;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.model.Station;
import ru.mirea.cyberclub.model.StationType;
import ru.mirea.cyberclub.service.BookingFilter;
import ru.mirea.cyberclub.service.BookingSortField;
import ru.mirea.cyberclub.ui.util.Cells;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FormDialog;
import ru.mirea.cyberclub.ui.util.FormInput;
import ru.mirea.cyberclub.ui.util.FxTasks;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Вкладка «Бронирования» (bookings-tab.fxml) — основная сущность системы.
 *
 * <p>Цепочка коллекций для таблицы:
 * <pre>
 * ObservableList master  — все брони из БД (заменяется при «Обновить»)
 *   → FilteredList       — поиск и фильтры (предикат строит BookingFilter из слоя сервисов)
 *     → SortedList       — сортировка: компаратор привязан к comparatorProperty() таблицы
 *       → TableView      — показывает результат и сама перерисовывается при любом изменении
 * </pre>
 */
public class BookingsController implements RefreshableTab {

    private static final PseudoClass CANCELLED = PseudoClass.getPseudoClass("cancelled");
    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    // --- таблица
    @FXML
    private TableView<Booking> table;
    @FXML
    private TableColumn<Booking, Long> idCol;
    @FXML
    private TableColumn<Booking, String> clientCol;
    @FXML
    private TableColumn<Booking, String> stationCol;
    @FXML
    private TableColumn<Booking, StationType> typeCol;
    @FXML
    private TableColumn<Booking, LocalDateTime> startCol;
    @FXML
    private TableColumn<Booking, LocalDateTime> endCol;
    @FXML
    private TableColumn<Booking, Duration> durationCol;
    @FXML
    private TableColumn<Booking, BookingStatus> statusCol;
    @FXML
    private TableColumn<Booking, BigDecimal> priceCol;
    @FXML
    private TableColumn<Booking, String> commentCol;

    // --- поиск, фильтры, сортировка
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<BookingStatus> statusFilter;
    @FXML
    private ComboBox<StationType> typeFilter;
    @FXML
    private ComboBox<Client> clientFilter;
    @FXML
    private DatePicker fromPicker;
    @FXML
    private DatePicker toPicker;
    @FXML
    private ComboBox<BookingSortField> sortCombo;
    @FXML
    private ToggleButton sortDirection;

    // --- кнопки, которые зависят от выбранной строки
    @FXML
    private Button editButton;
    @FXML
    private Button statusButton;
    @FXML
    private Button deleteButton;

    // --- карточка выбранной брони и строка состояния
    @FXML
    private VBox detailsBox;
    @FXML
    private Label detailsPlaceholder;
    @FXML
    private Label detailsTitle;
    @FXML
    private Label detailsStatus;
    @FXML
    private GridPane bookingGrid;
    @FXML
    private GridPane clientGrid;
    @FXML
    private GridPane stationGrid;
    @FXML
    private Label nextStatusLabel;
    @FXML
    private Label countLabel;
    @FXML
    private Label filterMessage;

    private final AppContext context;

    private final ObservableList<Booking> master = FXCollections.observableArrayList();
    private final FilteredList<Booking> filtered = new FilteredList<>(master, booking -> true);
    private final SortedList<Booking> sorted = new SortedList<>(filtered);

    /** Клиенты и места по id — для карточки брони без лишних запросов к БД. */
    private Map<Long, Client> clientsById = Map.of();
    private Map<Long, Station> stationsById = Map.of();

    private final Map<BookingSortField, TableColumn<Booking, ?>> sortColumns = new EnumMap<>(BookingSortField.class);
    private boolean syncingSort;
    private Runnable onShowStatistics = () -> { };

    public BookingsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        setupColumns();
        setupFilters();
        setupSorting();

        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Нет бронирований, удовлетворяющих условиям поиска"));
        table.setRowFactory(view -> {
            TableRow<Booking> row = new TableRow<>() {
                @Override
                protected void updateItem(Booking booking, boolean empty) {
                    super.updateItem(booking, empty);
                    pseudoClassStateChanged(CANCELLED, !empty && booking != null
                            && booking.getStatus() == BookingStatus.CANCELLED);
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    onEdit();
                }
            });
            return row;
        });

        var selected = table.getSelectionModel().selectedItemProperty();
        editButton.disableProperty().bind(selected.isNull());
        statusButton.disableProperty().bind(selected.isNull());
        deleteButton.disableProperty().bind(selected.isNull());
        selected.addListener((obs, old, booking) -> showDetails(booking));
        showDetails(null);
    }

    /** Колонке задаётся функция «бронь → значение»; значение хранится как есть, формат — в ячейке. */
    private static <T> void column(TableColumn<Booking, T> column, Function<Booking, T> getter) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
    }

    private void setupColumns() {
        column(idCol, Booking::getId);
        column(clientCol, Booking::getClientNickname);
        column(stationCol, Booking::getStationName);
        column(typeCol, Booking::getStationType);
        column(startCol, Booking::getStartTime);
        column(endCol, Booking::getEndTime);
        column(durationCol, Booking::getDuration);
        column(statusCol, Booking::getStatus);
        column(priceCol, Booking::getTotalPrice);
        column(commentCol, Booking::getComment);

        clientCol.setComparator(String.CASE_INSENSITIVE_ORDER);
        startCol.setCellFactory(Cells.dateTime());
        endCol.setCellFactory(Cells.dateTime());
        durationCol.setCellFactory(Cells.duration());
        priceCol.setCellFactory(Cells.money());
        statusCol.setCellFactory(Cells.statusBadge());
    }

    private void setupFilters() {
        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(BookingStatus.values());
        Cells.withAllOption(statusFilter, "Все статусы", BookingStatus::getTitle);

        typeFilter.getItems().add(null);
        typeFilter.getItems().addAll(StationType.values());
        Cells.withAllOption(typeFilter, "Все зоны", StationType::getTitle);

        Cells.withAllOption(clientFilter, "Все клиенты", Cells::clientText);
        clientFilter.getItems().add(null);

        FormInput.setupDatePicker(fromPicker);
        FormInput.setupDatePicker(toPicker);

        searchField.textProperty().addListener((obs, old, text) -> applyFilter());
        statusFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        typeFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        clientFilter.valueProperty().addListener((obs, old, value) -> applyFilter());
        fromPicker.valueProperty().addListener((obs, old, value) -> applyFilter());
        toPicker.valueProperty().addListener((obs, old, value) -> applyFilter());
        filtered.addListener((ListChangeListener<Booking>) change -> updateCount());
    }

    /**
     * Сортировка выполняется встроенным механизмом TableView: клик по заголовку столбца
     * меняет sortOrder таблицы. Выпадающий список «Сортировка» делает то же самое
     * программно и показывает текущий порядок, если пользователь кликнул по заголовку.
     */
    private void setupSorting() {
        sortColumns.put(BookingSortField.START_TIME, startCol);
        sortColumns.put(BookingSortField.PRICE, priceCol);
        sortColumns.put(BookingSortField.STATUS, statusCol);
        sortColumns.put(BookingSortField.CLIENT, clientCol);
        sortColumns.put(BookingSortField.DURATION, durationCol);

        sorted.comparatorProperty().bind(table.comparatorProperty());

        sortCombo.getItems().setAll(BookingSortField.values());
        sortCombo.valueProperty().addListener((obs, old, field) -> applySortFromControls());
        sortDirection.selectedProperty().addListener((obs, old, descending) -> {
            sortDirection.setText(descending ? "▼ по убыванию" : "▲ по возрастанию");
            applySortFromControls();
        });
        table.getSortOrder().addListener((ListChangeListener<TableColumn<Booking, ?>>) c -> syncSortControls());
        sortColumns.values().forEach(col -> col.sortTypeProperty().addListener((obs, old, type) -> syncSortControls()));

        // По умолчанию — сначала самые поздние брони
        sortCombo.setValue(BookingSortField.START_TIME);
        sortDirection.setSelected(true);
    }

    private void applySortFromControls() {
        if (syncingSort || sortCombo.getValue() == null) {
            return;
        }
        syncingSort = true;
        TableColumn<Booking, ?> column = sortColumns.get(sortCombo.getValue());
        column.setSortType(sortDirection.isSelected() ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
        table.getSortOrder().setAll(List.of(column));
        syncingSort = false;
    }

    private void syncSortControls() {
        if (syncingSort) {
            return;
        }
        syncingSort = true;
        TableColumn<Booking, ?> first = table.getSortOrder().isEmpty() ? null : table.getSortOrder().get(0);
        BookingSortField field = sortColumns.entrySet().stream()
                .filter(e -> e.getValue() == first)
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
        sortCombo.setValue(field);
        if (first != null) {
            boolean descending = first.getSortType() == TableColumn.SortType.DESCENDING;
            sortDirection.setSelected(descending);
            sortDirection.setText(descending ? "▼ по убыванию" : "▲ по возрастанию");
        }
        syncingSort = false;
    }

    // ------------------------------------------------------------------ данные

    /** Перечитывает брони, клиентов и места из БД в фоновом потоке. */
    @Override
    @FXML
    public void refresh() {
        Long selectedId = selectedId();
        countLabel.setText("Загрузка…");
        FxTasks.run(() -> new Snapshot(context.bookings().getAll(), context.clients().getAll(), context.stations().getAll()),
                snapshot -> {
                    clientsById = snapshot.clients().stream().collect(Collectors.toMap(Client::getId, c -> c));
                    stationsById = snapshot.stations().stream().collect(Collectors.toMap(Station::getId, s -> s));
                    updateClientFilter(snapshot.clients());
                    master.setAll(snapshot.bookings());
                    select(selectedId);
                    updateCount();
                },
                error -> {
                    updateCount();
                    Dialogs.error(owner(), error);
                });
    }

    private record Snapshot(List<Booking> bookings, List<Client> clients, List<Station> stations) {
    }

    /** Брони в том виде, как они сейчас показаны в таблице (с фильтрами и сортировкой). */
    public List<Booking> visibleBookings() {
        return new ArrayList<>(sorted);
    }

    private void updateClientFilter(List<Client> clients) {
        Long selectedClient = clientFilter.getValue() == null ? null : clientFilter.getValue().getId();
        List<Client> items = new ArrayList<>();
        items.add(null);
        items.addAll(clients);
        clientFilter.getItems().setAll(items);
        clientFilter.setValue(selectedClient == null ? null : clientsById.get(selectedClient));
    }

    /** Собирает критерии из элементов управления; правило отбора строит BookingFilter (слой сервисов). */
    private void applyFilter() {
        Client client = clientFilter.getValue();
        BookingFilter filter = new BookingFilter(searchField.getText(), statusFilter.getValue(), typeFilter.getValue(),
                client == null ? null : client.getId(), fromPicker.getValue(), toPicker.getValue());
        try {
            filtered.setPredicate(filter.toPredicate());
            setFilterError(null);
        } catch (ValidationException e) {
            setFilterError(e.getMessage());
        }
        updateCount();
    }

    private void setFilterError(String message) {
        boolean error = message != null;
        fromPicker.pseudoClassStateChanged(ERROR, error);
        toPicker.pseudoClassStateChanged(ERROR, error);
        filterMessage.setText(error ? message : "");
    }

    @FXML
    private void onResetFilters() {
        searchField.clear();
        statusFilter.setValue(null);
        typeFilter.setValue(null);
        clientFilter.setValue(null);
        fromPicker.setValue(null);
        toPicker.setValue(null);
        fromPicker.getEditor().clear();
        toPicker.getEditor().clear();
    }

    private void updateCount() {
        countLabel.setText("Показано: " + filtered.size() + " из " + master.size());
    }

    // ---------------------------------------------------------------- действия

    @FXML
    public void onAdd() {
        try {
            AppContext.View<BookingFormController> view = context.load("booking-form.fxml");
            view.controller().create(clientFilter.getValue());
            FormDialog.show(owner(), view).ifPresent(saved -> reloadAndSelect(saved.getId()));
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    @FXML
    private void onEdit() {
        Booking booking = table.getSelectionModel().getSelectedItem();
        if (booking == null) {
            return;
        }
        if (!booking.getStatus().isEditable()) {
            Dialogs.info(owner(), "Бронирование #" + booking.getId() + " нельзя изменить",
                    "Время и место можно менять только у броней в статусах «Создано» и «Подтверждено».\n"
                            + "Текущий статус: «" + booking.getStatus().getTitle() + "».");
            return;
        }
        try {
            AppContext.View<BookingFormController> view = context.load("booking-form.fxml");
            view.controller().edit(booking);
            FormDialog.show(owner(), view).ifPresent(saved -> reloadAndSelect(saved.getId()));
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    /** Смена статуса — только на допустимые по схеме CREATED → CONFIRMED → ACTIVE → COMPLETED. */
    @FXML
    private void onChangeStatus() {
        Booking booking = table.getSelectionModel().getSelectedItem();
        if (booking == null) {
            return;
        }
        List<BookingStatus> allowed = List.copyOf(booking.getStatus().allowedTransitions());
        if (allowed.isEmpty()) {
            Dialogs.info(owner(), "Статус «" + booking.getStatus().getTitle() + "» — конечный",
                    "Завершённое или отменённое бронирование больше не меняет статус.");
            return;
        }
        Dialogs.choose(owner(), "Бронирование #" + booking.getId() + ": сейчас «" + booking.getStatus().getTitle() + "»",
                        "Новый статус:", allowed)
                .ifPresent(status -> {
                    try {
                        context.bookings().changeStatus(booking.getId(), status);
                        reloadAndSelect(booking.getId());
                    } catch (RuntimeException e) {
                        Dialogs.error(owner(), e);
                    }
                });
    }

    @FXML
    private void onDelete() {
        Booking booking = table.getSelectionModel().getSelectedItem();
        if (booking == null) {
            return;
        }
        String text = "Бронирование #" + booking.getId() + ": " + booking.getClientNickname() + ", "
                + booking.getStationName() + ", " + DateTimeUtil.format(booking.getStartTime()) + " – "
                + DateTimeUtil.format(booking.getEndTime()) + ".\n\nЗапись будет удалена из базы данных без возможности восстановления.";
        if (!Dialogs.confirm(owner(), "Удалить бронирование?", text)) {
            return;
        }
        try {
            context.bookings().delete(booking.getId());
            master.remove(booking);
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    /** Экспорт текущего набора (с учётом поиска, фильтров и сортировки) в .xlsx или .csv. */
    @FXML
    public void onExport() {
        List<Booking> rows = visibleBookings();
        if (rows.isEmpty()) {
            Dialogs.info(owner(), "Нечего экспортировать", "В таблице нет записей. Измените условия поиска или фильтры.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Экспорт бронирований");
        for (Exporter exporter : context.export().getExporters()) {
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    exporter.description() + " (*." + exporter.extension() + ")", "*." + exporter.extension()));
        }
        chooser.setInitialFileName("cyberclub_bookings_" + LocalDateTime.now().format(FILE_STAMP));
        File home = new File(System.getProperty("user.home"));
        File documents = new File(home, "Documents");
        chooser.setInitialDirectory(documents.isDirectory() ? documents : home);

        File file = chooser.showSaveDialog(owner());
        if (file == null) {
            return; // пользователь нажал «Отмена»
        }
        Path path = withExtension(file.toPath(), chooser.getSelectedExtensionFilter());
        FxTasks.run(() -> context.export().export(rows, path),
                count -> Dialogs.info(owner(), "Экспорт завершён",
                        "Выгружено записей: " + count + "\nФайл: " + path.toAbsolutePath()),
                error -> Dialogs.error(owner(), error));
    }

    /** Если расширение не набрано вручную, добавляем расширение выбранного фильтра. */
    private static Path withExtension(Path path, FileChooser.ExtensionFilter filter) {
        String name = path.getFileName().toString();
        if (name.contains(".") || filter == null) {
            return path;
        }
        String ext = filter.getExtensions().get(0).substring(1); // "*.xlsx" -> ".xlsx"
        return path.resolveSibling(name + ext);
    }

    @FXML
    private void onShowStatistics() {
        onShowStatistics.run();
    }

    public void setOnShowStatistics(Runnable action) {
        this.onShowStatistics = Objects.requireNonNull(action);
    }

    // ------------------------------------------------------- карточка брони

    /** Просмотр связанных данных: бронь + её клиент + игровое место. */
    private void showDetails(Booking booking) {
        boolean has = booking != null;
        detailsBox.setVisible(has);
        detailsBox.setManaged(has);
        detailsPlaceholder.setVisible(!has);
        detailsPlaceholder.setManaged(!has);
        if (!has) {
            return;
        }
        detailsTitle.setText("Бронирование #" + booking.getId());
        detailsStatus.setText(booking.getStatus().getTitle());
        detailsStatus.getStyleClass().setAll("label", "status-badge", Cells.styleClass(booking.getStatus()));

        fill(bookingGrid,
                "Начало", DateTimeUtil.format(booking.getStartTime()),
                "Окончание", DateTimeUtil.format(booking.getEndTime()),
                "Длительность", DateTimeUtil.formatDuration(booking.getDuration()),
                "Стоимость", DateTimeUtil.formatMoney(booking.getTotalPrice()),
                "Комментарий", booking.getComment() == null ? "—" : booking.getComment(),
                "Создано", DateTimeUtil.format(booking.getCreatedAt()));

        Client client = clientsById.get(booking.getClientId());
        if (client != null) {
            int age = client.getAge(LocalDate.now());
            fill(clientGrid,
                    "Ник", client.getNickname(),
                    "ФИО", client.getFullName(),
                    "Телефон", client.getPhone(),
                    "Email", client.getEmail(),
                    "Возраст", age + (client.isAdult(LocalDate.now()) ? "" : " (несовершеннолетний)"));
        } else {
            fill(clientGrid, "Клиент", booking.getClientNickname());
        }

        Station station = stationsById.get(booking.getStationId());
        if (station != null) {
            fill(stationGrid,
                    "Место", station.getName(),
                    "Зона", station.getType().getTitle() + (station.getType().isAdultsOnly() ? " (18+)" : ""),
                    "Тариф", DateTimeUtil.formatMoney(station.getHourlyRate()) + " / ч",
                    "Конфигурация", station.getSpecs() == null ? "—" : station.getSpecs(),
                    "Состояние", station.isActive() ? "работает" : "на обслуживании");
        } else {
            fill(stationGrid, "Место", booking.getStationName());
        }

        String next = booking.getStatus().allowedTransitions().stream()
                .map(BookingStatus::getTitle).collect(Collectors.joining(", "));
        nextStatusLabel.setText(next.isEmpty() ? "Статус конечный — изменения недоступны."
                : "Можно перевести в: " + next + ".");
    }

    /** Заполняет сетку парами «название — значение». */
    private static void fill(GridPane grid, String... pairs) {
        grid.getChildren().clear();
        for (int i = 0; i < pairs.length; i += 2) {
            Label key = new Label(pairs[i]);
            key.getStyleClass().add("details-key");
            Label value = new Label(pairs[i + 1]);
            value.getStyleClass().add("details-value");
            value.setWrapText(true);
            grid.addRow(i / 2, key, value);
        }
    }

    // --------------------------------------------------------------- служебное

    private void reloadAndSelect(Long id) {
        table.getSelectionModel().clearSelection();
        FxTasks.run(() -> context.bookings().getAll(),
                bookings -> {
                    master.setAll(bookings);
                    select(id);
                },
                error -> Dialogs.error(owner(), error));
    }

    private void select(Long id) {
        if (id == null) {
            return;
        }
        sorted.stream().filter(b -> id.equals(b.getId())).findFirst().ifPresent(b -> {
            table.getSelectionModel().select(b);
            table.scrollTo(b);
        });
    }

    private Long selectedId() {
        Booking booking = table.getSelectionModel().getSelectedItem();
        return booking == null ? null : booking.getId();
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
