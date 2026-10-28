package ru.mirea.cyberclub;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import ru.mirea.cyberclub.db.DatabaseManager;
import ru.mirea.cyberclub.exception.AppException;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.repository.ClientRepository;
import ru.mirea.cyberclub.repository.StationRepository;
import ru.mirea.cyberclub.repository.StatisticsRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcBookingRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcClientRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcStationRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcStatisticsRepository;
import ru.mirea.cyberclub.repository.rest.RestBookingRepository;
import ru.mirea.cyberclub.repository.rest.RestClient;
import ru.mirea.cyberclub.repository.rest.RestClientRepository;
import ru.mirea.cyberclub.repository.rest.RestStationRepository;
import ru.mirea.cyberclub.repository.rest.RestStatisticsRepository;
import ru.mirea.cyberclub.auth.AuthApiClient;
import ru.mirea.cyberclub.service.AuthService;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.UserAdminService;
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
 * Контроллеры создаёт {@link FXMLLoader} через {@link #createController(Class)}:
 * если у контроллера есть конструктор {@code (AppContext)}, в него передаётся этот объект.
 */
public class AppContext {

    private static final String VIEW_DIR = "/ru/mirea/cyberclub/ui/view/";

    private final String dataSource;
    private final ClientService clientService;
    private final StationService stationService;
    private final BookingService bookingService;
    private final StatisticsService statisticsService;
    private final ExportService exportService;

    /** Работа с базой КР1 напрямую через JDBC. */
    public static AppContext jdbc(DatabaseManager db) {
        return new AppContext("PostgreSQL (JDBC): " + db.describe(),
                new JdbcClientRepository(db), new JdbcStationRepository(db),
                new JdbcBookingRepository(db), new JdbcStatisticsRepository(db));
    }

    /** Работа через REST API (cyberclub-backend); database — база сервера из /actuator/health. */
    public static AppContext rest(RestClient rest, String database) {
        return new AppContext("REST API: " + rest.baseUrl() + (database.isBlank() ? "" : " → " + database),
                new RestClientRepository(rest), new RestStationRepository(rest),
                new RestBookingRepository(rest), new RestStatisticsRepository(rest));
    }

    private AppContext(String dataSource, ClientRepository clients, StationRepository stations,
                       BookingRepository bookings, StatisticsRepository statistics) {
        this.dataSource = dataSource;
        this.clientService = new ClientService(clients, bookings);
        this.stationService = new StationService(stations, bookings);
        this.bookingService = new BookingService(bookings, clients, stations);
        this.statisticsService = new StatisticsService(statistics);
        this.exportService = new ExportService(statisticsService);
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

    /** Откуда берутся данные — для строки состояния и окна «О программе» (без пароля). */
    public String dataSource() {
        return dataSource;
    }

    public ClientService clients() {
        return clientService;
    }

    public StationService stations() {
        return stationService;
    }

    public BookingService bookings() {
        return bookingService;
    }

    public StatisticsService statistics() {
        return statisticsService;
    }

    public ExportService export() {
        return exportService;
    }

    // ------------------------------------------------------- вход сотрудников

    private AuthService authService;
    private UserAdminService userAdminService;
    private Runnable onLogout = () -> { };

    /** Включает вход через сервер КР3: окно входа при запуске и вкладку «Пользователи» для ADMIN. */
    public void enableAuth(AuthApiClient client) {
        this.authService = new AuthService(client);
        this.userAdminService = new UserAdminService(client, authService);
    }

    /** Вход включён (auth.enabled=true). Если выключен — приложение работает без входа, как в КР2. */
    public boolean authEnabled() {
        return authService != null;
    }

    public AuthService auth() {
        return authService;
    }

    public UserAdminService users() {
        return userAdminService;
    }

    /** Что делать по кнопке «Выйти»: приложение снова показывает окно входа. */
    public void setOnLogout(Runnable onLogout) {
        this.onLogout = onLogout;
    }

    public void logout() {
        if (authService != null) {
            authService.logout();
        }
        onLogout.run();
    }
}
