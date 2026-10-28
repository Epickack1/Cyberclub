package ru.mirea.cyberclub.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;
import ru.mirea.cyberclub.AppContext;
import ru.mirea.cyberclub.auth.Role;
import ru.mirea.cyberclub.auth.UserInfo;
import ru.mirea.cyberclub.ui.util.Dialogs;

/**
 * Главное окно (main-view.fxml): меню, вкладки и строка состояния.
 *
 * <p>Содержимое вкладок подключено через {@code <fx:include fx:id="bookings" .../>}.
 * FXMLLoader сам создаёт вложенные контроллеры и внедряет их в поля с именем
 * {@code fx:id + "Controller"} — например {@link #bookingsController}.</p>
 */
public class MainController {

    @FXML
    private TabPane tabs;
    @FXML
    private Tab bookingsTab;
    @FXML
    private Tab clientsTab;
    @FXML
    private Tab stationsTab;
    @FXML
    private Tab statisticsTab;
    @FXML
    private Label dbLabel;
    @FXML
    private VBox userBox;
    @FXML
    private Label userNameLabel;
    @FXML
    private Label userRoleLabel;
    @FXML
    private Button logoutButton;
    @FXML
    private MenuItem logoutMenuItem;

    /** Вкладка «Пользователи» — создаётся только для администратора. */
    private Tab usersTab;
    private UsersController usersController;

    @FXML
    private BookingsController bookingsController;
    @FXML
    private ClientsController clientsController;
    @FXML
    private StationsController stationsController;
    @FXML
    private StatisticsController statisticsController;

    private final AppContext context;

    public MainController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        dbLabel.setText(context.dataSource());
        if (context.authEnabled()) {
            context.auth().currentUser().ifPresent(this::showUser);
        }
        bookingsController.setOnShowStatistics(() -> tabs.getSelectionModel().select(statisticsTab));
        statisticsController.setSelectionSupplier(bookingsController::visibleBookings);

        // При переходе на вкладку её данные перечитываются из БД: изменения на одной вкладке
        // (например, новый клиент) сразу видны на другой (в списке клиентов формы брони).
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, tab) -> refresh(tab));
        refresh(tabs.getSelectionModel().getSelectedItem());
    }

    private void refresh(Tab tab) {
        RefreshableTab controller = controllerFor(tab);
        if (controller != null) {
            controller.refresh();
        }
    }

    private RefreshableTab controllerFor(Tab tab) {
        if (tab == bookingsTab) {
            return bookingsController;
        }
        if (tab == clientsTab) {
            return clientsController;
        }
        if (tab == stationsTab) {
            return stationsController;
        }
        if (tab == statisticsTab) {
            return statisticsController;
        }
        if (tab != null && tab == usersTab) {
            return usersController;
        }
        return null;
    }

    /** Показывает в шапке, кто вошёл; администратору добавляет вкладку «Пользователи». */
    private void showUser(UserInfo user) {
        userNameLabel.setText(user.displayName());
        userRoleLabel.setText(user.mainRole().getTitle() + " · " + user.email());
        for (var node : new javafx.scene.Node[] {userBox, logoutButton}) {
            node.setVisible(true);
            node.setManaged(true);
        }
        logoutMenuItem.setVisible(true);
        if (user.hasRole(Role.ADMIN)) {
            AppContext.View<UsersController> view = context.load("users-tab.fxml");
            usersController = view.controller();
            usersTab = new Tab("Пользователи", view.root());
            tabs.getTabs().add(usersTab);
        }
    }

    @FXML
    private void onLogout() {
        if (Dialogs.confirm(tabs.getScene().getWindow(), "Выйти из учётной записи?",
                "Откроется окно входа.")) {
            context.logout();
        }
    }

    // ------------------------------------------------------------------- меню

    @FXML
    private void onRefresh() {
        refresh(tabs.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onNewBooking() {
        tabs.getSelectionModel().select(bookingsTab);
        bookingsController.onAdd();
    }

    @FXML
    private void onExport() {
        tabs.getSelectionModel().select(bookingsTab);
        bookingsController.onExport();
    }

    @FXML
    private void onExit() {
        Platform.exit();
    }

    @FXML
    private void onAbout() {
        Dialogs.info(tabs.getScene().getWindow(), "CyberClub — киберспортивный клуб",
                "Контрольная работа №2: настольное приложение JavaFX.\n"
                        + "Учёт клиентов, игровых мест и бронирований.\n\n"
                        + "Java 21 · JavaFX 21 · FXML + CSS · JDBC / REST API (Spring Boot) · PostgreSQL\n"
                        + "Источник данных: " + context.dataSource());
    }
}
