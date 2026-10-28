package ru.mirea.cyberclub;

import java.util.Objects;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mirea.cyberclub.auth.AuthApiClient;
import ru.mirea.cyberclub.config.AppConfig;
import ru.mirea.cyberclub.db.DatabaseConfig;
import ru.mirea.cyberclub.db.DatabaseManager;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.repository.rest.RestClient;
import ru.mirea.cyberclub.ui.controller.LoginController;
import ru.mirea.cyberclub.ui.util.Dialogs;
import ru.mirea.cyberclub.ui.util.FormDialog;

/**
 * JavaFX-приложение «Киберспортивный клуб CyberClub» (КР2).
 *
 * <p>Stage — окно, Scene — содержимое окна, Node — любой элемент графа сцены
 * (кнопка, таблица, контейнер). Интерфейс главного окна описан в main-view.fxml.</p>
 *
 * <p>Если включён вход (auth.enabled=true в app.properties), до главного окна показывается
 * окно входа: email и пароль проверяет сервер КР3. Кнопка «Выйти» возвращает к окну входа.</p>
 */
public class CyberClubApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(CyberClubApp.class);

    public static final String STYLESHEET = "/ru/mirea/cyberclub/ui/css/app.css";

    private Stage stage;
    private AppContext context;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            log.error("Необработанная ошибка в потоке {}", thread.getName(), error);
            if (Platform.isFxApplicationThread()) {
                Dialogs.error(null, error);
            }
        });

        AppConfig app;
        AppConfig.DataMode mode = AppConfig.DataMode.JDBC;
        try {
            app = AppConfig.load();
            mode = app.mode();
            if (mode == AppConfig.DataMode.REST) {
                log.info("Работа через REST API: {}", app.apiUrl());
                RestClient rest = new RestClient(app.apiUrl());
                context = AppContext.rest(rest, rest.checkHealth());
            } else {
                DatabaseConfig config = DatabaseConfig.load();
                log.info("Подключение к БД: {}", config);
                DatabaseManager db = new DatabaseManager(config);
                db.checkConnection();
                context = AppContext.jdbc(db);
            }
        } catch (AppException e) {
            log.error("Запуск невозможен", e);
            String hint = mode == AppConfig.DataMode.REST
                    ? "Сначала запустите PostgreSQL и сервер cyberclub-backend (run.cmd), проверьте "
                            + "http://localhost:8082/actuator/health. Для работы без сервера задайте CYBERCLUB_DATA_MODE=jdbc."
                    : "Проверьте, что PostgreSQL запущен, база cyberclub создана (db/init.ps1), "
                            + "а пароль задан в переменной окружения CYBERCLUB_DB_PASSWORD.";
            Dialogs.fatal("Не удалось подключиться к данным CyberClub", e.getMessage() + "\n\n" + hint);
            Platform.exit();
            return;
        }

        stage.setTitle("CyberClub — киберспортивный клуб");
        var icon = getClass().getResource("/ru/mirea/cyberclub/ui/icon.png");
        if (icon != null) {
            stage.getIcons().add(new Image(icon.toExternalForm()));
        }
        stage.setMinWidth(1100);
        stage.setMinHeight(680);
        // При смене пользователя главное окно прячется на время окна входа — приложение при этом
        // не должно закрываться само; завершаем его явно крестиком окна или кнопкой «Выход».
        Platform.setImplicitExit(false);
        stage.setOnCloseRequest(event -> Platform.exit());

        if (app.authEnabled()) {
            log.info("Вход сотрудников через сервер КР3: {}", app.authUrl());
            context.enableAuth(new AuthApiClient(app.authUrl()));
            context.setOnLogout(this::loginAndShowMain);
        }
        loginAndShowMain();
    }

    /** Окно входа (если вход включён), затем главное окно. «Выход» в окне входа закрывает приложение. */
    private void loginAndShowMain() {
        if (context.authEnabled()) {
            stage.hide();
            AppContext.View<LoginController> login = context.load("login-view.fxml");
            if (FormDialog.show(null, login, "Войти", "Выход").isEmpty()) {
                Platform.exit();
                return;
            }
        }
        // Главное окно собирается заново: после смены пользователя меняются шапка и вкладки
        AppContext.View<Object> main = context.load("main-view.fxml");
        Scene scene = new Scene(main.root(), 1360, 820);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(STYLESHEET)).toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
