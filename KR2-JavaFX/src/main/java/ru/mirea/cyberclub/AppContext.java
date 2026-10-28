package ru.mirea.cyberclub;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import ru.mirea.cyberclub.db.DatabaseManager;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.service.ExportService;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Точка сборки приложения: создаёт DAO и сервисы один раз и выдаёт их контроллерам.
 *
 * <pre>
 * FXML (View) → Controller → Service (бизнес-правила) → Repository/DAO (SQL) → PostgreSQL
 * </pre>
 *
 * Сервисы при этом живут в {@link AppServices} — контейнере без JavaFX, которым же пользуется
 * MCP-сервер. Здесь же добавлено то, чего у {@link AppServices} нет: загрузка FXML и создание
 * контроллеров.
 *
 * Контроллеры создаёт {@link FXMLLoader} через {@link #createController(Class)}:
 * если у контроллера есть конструктор {@code (AppContext)}, в него передаётся этот объект.
 */
public class AppContext {

    private static final String VIEW_DIR = "/ru/mirea/cyberclub/ui/view/";

    private final AppServices services;

    public AppContext(DatabaseManager db) {
        this.services = new AppServices(db);
    }

    /** Результат загрузки FXML: корневой узел и его контроллер. */
    public record View<C>(Parent root, C controller) {
    }

    /** Загружает FXML-файл из ресурсов ui/view и создаёт его контроллер. */
    public <C> View<C> load(String fxml) {
        URL url = AppContext.class.getResource(VIEW_DIR + fxml);
        if (url == null) {
            throw new AppException("Не найден файл интерфейса " + fxml);
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setControllerFactory(this::createController);
        try {
            Parent root = loader.load();
            return new View<>(root, loader.getController());
        } catch (IOException e) {
            throw new AppException("Не удалось загрузить интерфейс " + fxml + ": " + e.getMessage(), e);
        }
    }

    /** Фабрика контроллеров для FXMLLoader (используется и для вложенных fx:include). */
    public Object createController(Class<?> type) {
        try {
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] params = constructor.getParameterTypes();
                if (params.length == 1 && params[0] == AppContext.class) {
                    return constructor.newInstance(this);
                }
            }
            return type.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AppException("Не удалось создать контроллер " + type.getSimpleName(), e);
        }
    }

    public DatabaseManager db() {
        return services.db();
    }

    public ClientService clients() {
        return services.clients();
    }

    public StationService stations() {
        return services.stations();
    }

    public BookingService bookings() {
        return services.bookings();
    }

    public StatisticsService statistics() {
        return services.statistics();
    }

    public ExportService export() {
        return services.export();
    }
}
