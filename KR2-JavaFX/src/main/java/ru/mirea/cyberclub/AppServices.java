package ru.mirea.cyberclub;

import ru.mirea.cyberclub.db.DatabaseManager;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.repository.ClientRepository;
import ru.mirea.cyberclub.repository.StationRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcBookingRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcClientRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcStationRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcStatisticsRepository;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.service.ExportService;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Контейнер сервисов без зависимости от JavaFX.
 *
 * <p>Потому выделен из {@link AppContext}, что к сервисам нужен доступ и там, где графического
 * интерфейса нет: MCP-сервер ({@code ru.mirea.cyberclub.mcp.McpMain}) запускается внешним ИИ
 * как отдельный процесс и должен работать без окна и без загрузки FXML. В этом классе нет
 * ни одного импорта {@code javafx.*} — единственное, что отделяет интеграции от графики.</p>
 *
 * <p>DAO создаются один раз в конструкторе, все сервисы получают их через внедрение зависимостей —
 * схема та же, что и в {@link AppContext}, только без представления.</p>
 */
public class AppServices {

    private final DatabaseManager db;
    private final ClientService clientService;
    private final StationService stationService;
    private final BookingService bookingService;
    private final StatisticsService statisticsService;
    private final ExportService exportService;

    public AppServices(DatabaseManager db) {
        this.db = db;
        ClientRepository clients = new JdbcClientRepository(db);
        StationRepository stations = new JdbcStationRepository(db);
        BookingRepository bookings = new JdbcBookingRepository(db);
        this.clientService = new ClientService(clients, bookings);
        this.stationService = new StationService(stations, bookings);
        this.bookingService = new BookingService(bookings, clients, stations);
        this.statisticsService = new StatisticsService(new JdbcStatisticsRepository(db));
        this.exportService = new ExportService(statisticsService);
    }

    /**
     * Для тестов: сервисы подставляются напрямую, без подключения к PostgreSQL —
     * тем же способом, как уже построены тесты сервисного слоя на InMemory*-репозиториях.
     * Параметр {@code db} остаётся null, так как обращений к БД в самих сервисах нет.
     */
    public AppServices(ClientService clients, StationService stations, BookingService bookings,
                       StatisticsService statistics, ExportService export) {
        this.db = null;
        this.clientService = clients;
        this.stationService = stations;
        this.bookingService = bookings;
        this.statisticsService = statistics;
        this.exportService = export;
    }

    public DatabaseManager db() {
        return db;
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
}
