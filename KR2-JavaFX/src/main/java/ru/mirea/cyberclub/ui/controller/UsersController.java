package ru.mirea.cyberclub.ui.controller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.function.Supplier;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FormDialog;
import ru.mirea.cyberclub.ui.util.FxTasks;

/**
 * Вкладка «Пользователи» (users-tab.fxml): регистрация сотрудников, отключение,
 * сброс пароля и снятие блокировки входа. Доступна только роли ADMIN.
 */
public class UsersController implements RefreshableTab {

    private static final PseudoClass INACTIVE = PseudoClass.getPseudoClass("inactive");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    @FXML
    private TableView<UserInfo> table;
    @FXML
    private TableColumn<UserInfo, String> emailCol;
    @FXML
    private TableColumn<UserInfo, String> nameCol;
    @FXML
    private TableColumn<UserInfo, String> roleCol;
    @FXML
    private TableColumn<UserInfo, String> stateCol;
    @FXML
    private TableColumn<UserInfo, String> lastLoginCol;
    @FXML
    private Button toggleButton;
    @FXML
    private Button resetButton;
    @FXML
    private Button unlockButton;
    @FXML
    private Label countLabel;

    private final AppContext context;
    private final ObservableList<UserInfo> master = FXCollections.observableArrayList();

    public UsersController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        column(emailCol, UserInfo::email);
        column(nameCol, UserInfo::displayName);
        column(roleCol, user -> user.mainRole().getTitle());
        column(stateCol, UserInfo::state);
        column(lastLoginCol, user -> format(user.lastLoginAt()));

        SortedList<UserInfo> sorted = new SortedList<>(master);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Пользователей нет"));
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(UserInfo user, boolean empty) {
                super.updateItem(user, empty);
                pseudoClassStateChanged(INACTIVE, !empty && user != null && (!user.enabled() || user.locked()));
            }
        });

        var selected = table.getSelectionModel().selectedItemProperty();
        toggleButton.disableProperty().bind(selected.isNull());
        resetButton.disableProperty().bind(selected.isNull());
        selected.addListener((obs, old, user) -> {
            toggleButton.setText(user != null && !user.enabled() ? "Включить" : "Отключить");
            unlockButton.setDisable(user == null || !user.locked());
        });
        unlockButton.setDisable(true);
    }

    private static void column(TableColumn<UserInfo, String> column, Function<UserInfo, String> getter) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(getter.apply(cell.getValue())));
    }

    private static String format(Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    @Override
    @FXML
    public void refresh() {
        FxTasks.run(() -> context.users().getAll(),
                users -> {
                    master.setAll(users);
                    updateCount();
                },
                error -> Dialogs.error(owner(), error));
    }

    @FXML
    private void onRegister() {
        try {
            AppContext.View<UserFormController> view = context.load("user-form.fxml");
            FormDialog.show(owner(), view, "Зарегистрировать", "Отмена").ifPresent(user -> {
                master.add(user);
                table.getSelectionModel().select(user);
                updateCount();
                Dialogs.info(owner(), "Пользователь зарегистрирован",
                        user.displayName() + " (" + user.email() + ") — " + user.mainRole().getTitle()
                                + ".\nПередайте сотруднику email и пароль для входа.");
            });
        } catch (RuntimeException e) {
            Dialogs.error(owner(), e);
        }
    }

    @FXML
    private void onToggleEnabled() {
        UserInfo user = table.getSelectionModel().getSelectedItem();
        if (user == null) {
            return;
        }
        boolean enable = !user.enabled();
        if (!enable && !Dialogs.confirm(owner(), "Отключить пользователя " + user.email() + "?",
                "Он сразу потеряет доступ к системе. Включить обратно можно этой же кнопкой.")) {
            return;
        }
        apply(() -> context.users().setEnabled(user, enable), user);
    }

    @FXML
    private void onUnlock() {
        UserInfo user = table.getSelectionModel().getSelectedItem();
        if (user != null) {
            apply(() -> context.users().unlock(user), user);
        }
    }

    @FXML
    private void onResetPassword() {
        UserInfo user = table.getSelectionModel().getSelectedItem();
        if (user == null || !Dialogs.confirm(owner(), "Сбросить пароль " + user.email() + "?",
                "Будет создан новый временный пароль, все сеансы пользователя закроются.")) {
            return;
        }
        FxTasks.run(() -> context.users().resetPassword(user),
                password -> {
                    Dialogs.info(owner(), "Пароль сброшен",
                            "Новый пароль для " + user.email() + ":\n\n" + password
                                    + "\n\nПередайте его сотруднику. Пароль показывается один раз.");
                    refresh();
                },
                error -> Dialogs.error(owner(), error));
    }

    /** Выполняет действие на сервере и заменяет строку таблицы обновлённым пользователем. */
    private void apply(Supplier<UserInfo> action, UserInfo old) {
        FxTasks.run(action::get,
                updated -> {
                    int index = master.indexOf(old);
                    if (index >= 0) {
                        master.set(index, updated);
                    }
                    table.getSelectionModel().select(updated);
                    updateCount();
                },
                error -> Dialogs.error(owner(), error));
    }

    private void updateCount() {
        long active = master.stream().filter(user -> user.enabled() && !user.locked()).count();
        countLabel.setText("Пользователей: " + master.size() + " · активных: " + active);
    }

    private Window owner() {
        return table.getScene() == null ? null : table.getScene().getWindow();
    }
}
