package ru.mirea.cyberclub.ui.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.stage.Window;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.auth.Role;
import ru.mirea.cyberclub.model.Booking;
import ru.mirea.cyberclub.model.BookingStatus;
import ru.mirea.cyberclub.model.Client;
import ru.mirea.cyberclub.ui.util.Cells;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FormDialog;
import ru.mirea.cyberclub.ui.util.FxTasks;
import ru.mirea.cyberclub.util.DateTimeUtil;

/**
 * Вкладка «Клиенты» (clients-tab.fxml): CRUD клиентов и просмотр связанных данных —
 * бронирований выбранного клиента.
 */
public class ClientsController implements RefreshableTab {

    @FXML
    private TableView<Client> table;
    @FXML
    private TableColumn<Client, String> nicknameCol;
    @FXML
    private TableColumn<Client, String> fullNameCol;
    @FXML
    private TableColumn<Client, String> phoneCol;
    @FXML
    private TableColumn<Client, String> emailCol;
    @FXML
    private TableColumn<Client, LocalDate> birthDateCol;
    @FXML
    private TableColumn<Client, Integer> ageCol;
    @FXML
    private TextField searchField;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button accountButton;
    @FXML
    private Label countLabel;

    @FXML
    private Label historyTitle;
    @FXML
    private TableView<Booking> historyTable;
    @FXML
    private TableColumn<Booking, Long> hIdCol;
    @FXML
    private TableColumn<Booking, LocalDateTime> hStartCol;
    @FXML
    private TableColumn<Booking, String> hStationCol;
    @FXML
    private TableColumn<Booking, BookingStatus> hStatusCol;
    @FXML
    private TableColumn<Booking, BigDecimal> hPriceCol;
    @FXML
    private Label historySummary;

    private final AppContext context;
    private final ObservableList<Client> master = FXCollections.observableArrayList();
    private final FilteredList<Client> filtered = new FilteredList<>(master);
    private final ObservableList<Booking> history = FXCollections.observableArrayList();

    public ClientsController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        column(nicknameCol, Client::getNickname);
        column(fullNameCol, Client::getFullName);
        column(phoneCol, Client::getPhone);
        column(emailCol, Client::getEmail);
        column(birthDateCol, Client::getBirthDate);
        column(ageCol, c -> c.getAge(LocalDate.now()));
        birthDateCol.setCellFactory(Cells.date());
        nicknameCol.setComparator(String.CASE_INSENSITIVE_ORDER);

        SortedList<Client> sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Клиенты не найдены"));
        table.setRowFactory(view -> {
            TableRow<Client> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && !row.isEmpty()) {
                    onEdit();
                }
            });
            return row;
        });

        searchField.textProperty().addListener((obs, old, text) -> {
            filtered.setPredicate(context.clients().searchPredicate(text));
            updateCount();
        });

        var selected = table.getSelectionModel().selectedItemProperty();
        editButton.disableProperty().bind(selected.isNull());
        deleteButton.disableProperty().bind(selected.isNull());
        // Учётные записи клиентов для сайта создаёт только администратор системы
        boolean admin = context.authEnabled() && context.auth().hasRole(Role.ADMIN);
        accountButton.setVisible(admin);
        accountButton.setManaged(admin);
        accountButton.disableProperty().bind(selected.isNull());
        selected.addListener((obs, old, client) -> loadHistory(client));

        historyTable.setItems(history);
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setPlaceholder(new Label("Выберите клиента, чтобы увидеть его бронирования"));
        bookingColumn(hIdCol, Booking::getId);
        bookingColumn(hStartCol, Booking::getStartTime);
        bookingColumn(hStationCol, Booking::getStationName);
        bookingColumn(hStatusCol, Booking::getStatus);
        bookingColumn(hPriceCol, Booking::getTotalPrice);
        hStartCol.setCellFactory(Cells.dateTime());
        hStatusCol.setCellFactory(Cells.statusBadge());
        hPriceCol.setCellFactory(Cells.money());
        loadHistory(null);
    }

    private static <T> void column(TableColumn<Client, T> column, Function<Client, T> getter) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
    }

    private static <T> void bookingColumn(TableColumn<Booking, T> column, Function<Booking, T> getter) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
    }

    @Override
    @FXML
    public void refresh() {
        Client selected = table.getSelectionModel().getSelectedItem();
        Long selectedId = selected == null ? null : selected.getId();
        FxTasks.run(() -> context.clients().getAll(),
                clients -> {
                    master.setAll(clients);
                    updateCount();
                    select(selectedId);
                },
                error -> Dialogs.error(owner(), error));
    }

    @FXML
    private void onAdd() {
        try {
            AppContext.View<ClientFormController> view = context.load("client-form.fxml");
            FormDialog.show(owner(), view).ifPresent(client -> {
                master.add(client);
                select(client.getId());
                updateCount();
            });
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    @FXML
    private void onEdit() {
        Client client = table.getSelectionModel().getSelectedItem();
        if (client == null) {
            return;
        }
        try {
            AppContext.View<ClientFormController> view = context.load("client-form.fxml");
            view.controller().edit(client);
            FormDialog.show(owner(), view).ifPresent(updated -> {
                master.set(master.indexOf(client), updated);
                select(updated.getId());
            });
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    @FXML
    private void onDelete() {
        Client client = table.getSelectionModel().getSelectedItem();
        if (client == null) {
            return;
        }
        if (!Dialogs.confirm(owner(), "Удалить клиента " + client.getNickname() + "?",
                client.getFullName() + ", " + client.getPhone()
                        + "\n\nУдалить можно только клиента без бронирований. Действие необратимо.")) {
            return;
        }
        try {
            context.clients().delete(client.getId());
            master.remove(client);
            updateCount();
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    /** Учётная запись клиента для входа на сайт клуба; email и имя берутся из карточки. */
    @FXML
    private void onCreateAccount() {
        Client client = table.getSelectionModel().getSelectedItem();
        if (client == null) {
            return;
        }
        try {
            AppContext.View<UserFormController> view = context.load("user-form.fxml");
            view.controller().forClient(client.getEmail(), client.getFullName());
            FormDialog.show(owner(), view, "Создать", "Отмена").ifPresent(user ->
                    Dialogs.info(owner(), "Учётная запись создана",
                            client.getNickname() + " (" + client.getFullName() + ") может входить на сайт клуба.\n"
                                    + "Логин: " + user.email() + "\nПередайте клиенту пароль, который вы задали."));
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    /** Связанные данные: все брони выбранного клиента. */
    private void loadHistory(Client client) {
        history.clear();
        if (client == null) {
            historyTitle.setText("Бронирования клиента");
            historySummary.setText("");
            return;
        }
        historyTitle.setText("Бронирования клиента " + client.getNickname());
        FxTasks.run(() -> context.bookings().filterByClient(client.getId()),
                bookings -> {
                    history.setAll(bookings);
                    BigDecimal total = bookings.stream()
                            .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                            .map(Booking::getTotalPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    historySummary.setText("Всего: " + bookings.size() + " · на сумму " + DateTimeUtil.formatMoney(total)
                            + " (без отменённых)");
                },
                error -> Dialogs.error(owner(), error));
    }

    private void select(Long id) {
        if (id == null) {
            return;
        }
        List<Client> items = table.getItems();
        items.stream().filter(c -> id.equals(c.getId())).findFirst().ifPresent(c -> {
            table.getSelectionModel().select(c);
            table.scrollTo(c);
        });
    }

    private void updateCount() {
        countLabel.setText("Показано: " + filtered.size() + " из " + master.size());
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
