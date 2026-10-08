package ru.mirea.cyberclub;

import java.util.Objects;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.mirea.cyberclub.db.DatabaseConfig;
import ru.mirea.cyberclub.db.DatabaseManager;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.ui.util.Dialogs;

/**
 * JavaFX-приложение «Киберспортивный клуб CyberClub» (КР2).
 *
 * <p>Stage — окно, Scene — содержимое окна, Node — любой элемент графа сцены
 * (кнопка, таблица, контейнер). Интерфейс главного окна описан в main-view.fxml.</p>
 */
public class CyberClubApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(CyberClubApp.class);

    public static final String STYLESHEET = "/ru/mirea/cyberclub/ui/css/app.css";

    @Override
    public void start(Stage stage) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            log.error("Необработанная ошибка в потоке {}", thread.getName(), error);
            if (Platform.isFxApplicationThread()) {
                Dialogs.error(null, error);
            }
        });

        AppContext context;
        try {
            DatabaseConfig config = DatabaseConfig.load();
            log.info("Подключение к БД: {}", config);
            DatabaseManager db = new DatabaseManager(config);
            db.checkConnection();
            context = new AppContext(db);
        } catch (AppException e) {
            log.error("Запуск невозможен", e);
            Dialogs.fatal("Не удалось подключиться к базе данных CyberClub",
                    e.getMessage() + "\n\nПроверьте, что PostgreSQL запущен, база cyberclub создана (db/init.ps1), "
                            + "а пароль задан в переменной окружения CYBERCLUB_DB_PASSWORD.");
            Platform.exit();
            return;
        }

        AppContext.View<Object> main = context.load("main-view.fxml");
        Scene scene = new Scene(main.root(), 1360, 820);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(STYLESHEET)).toExternalForm());

        stage.setTitle("CyberClub — киберспортивный клуб");
        var icon = getClass().getResource("/ru/mirea/cyberclub/ui/icon.png");
        if (icon != null) {
            stage.getIcons().add(new Image(icon.toExternalForm()));
        }
        stage.setMinWidth(1100);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
