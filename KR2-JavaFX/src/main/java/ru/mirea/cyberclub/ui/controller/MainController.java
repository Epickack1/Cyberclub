package ru.mirea.cyberclub.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import ru.mirea.cyberclub.AppContext;
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
    private Tab chatTab;
    @FXML
    private Label dbLabel;

    @FXML
    private BookingsController bookingsController;
    @FXML
    private ClientsController clientsController;
    @FXML
    private StationsController stationsController;
    @FXML
    private StatisticsController statisticsController;
    @FXML
    private ChatController chatController;

    private final AppContext context;

    public MainController(AppContext context) {
        this.context = context;
    }

    @FXML
    private void initialize() {
        dbLabel.setText("PostgreSQL: " + context.db().describe());
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
        if (tab == chatTab) {
            return chatController;
        }
        return null;
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
                        + "Java 21 · JavaFX 21 · FXML + CSS · JDBC · PostgreSQL\n"
                        + "База данных: " + context.db().describe());
    }
}
