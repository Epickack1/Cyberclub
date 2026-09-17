package ru.mirea.cyberclub.ui;

import java.nio.file.Path;
import java.util.List;

import ru.mirea.cyberclub.export.CsvExporter;
import ru.mirea.cyberclub.export.ExcelExporter;
import ru.mirea.cyberclub.export.Exporter;
import ru.mirea.cyberclub.repository.BookingRepository;
import ru.mirea.cyberclub.repository.ClientRepository;
import ru.mirea.cyberclub.repository.SchemaInspector;
import ru.mirea.cyberclub.repository.StationRepository;
import ru.mirea.cyberclub.repository.StatisticsRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcBookingRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcClientRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcSchemaInspector;
import ru.mirea.cyberclub.repository.jdbc.JdbcStationRepository;
import ru.mirea.cyberclub.repository.jdbc.JdbcStatisticsRepository;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.service.StatisticsService;
import ru.mirea.cyberclub.util.DatabaseManager;

/**
 * Сборка приложения: создаёт слои (репозитории -> сервисы -> меню)
 * и запускает главное меню. Слои связаны через интерфейсы, поэтому JDBC-реализации
 * можно заменить, не трогая сервисы и меню.
 */
public class ConsoleApp {

    private final ConsoleIO io;
    private final DatabaseManager db;
    private final ThemePreferences themePreferences;
    private final Path exportDirectory;

    public ConsoleApp(ConsoleIO io, DatabaseManager db, ThemePreferences themePreferences, Path exportDirectory) {
        this.io = io;
        this.db = db;
        this.themePreferences = themePreferences;
        this.exportDirectory = exportDirectory;
    }

    public void run() {
        // Repository: доступ к данным (JDBC)
        ClientRepository clientRepository = new JdbcClientRepository(db);
        StationRepository stationRepository = new JdbcStationRepository(db);
        BookingRepository bookingRepository = new JdbcBookingRepository(db);
        StatisticsRepository statisticsRepository = new JdbcStatisticsRepository(db);
        SchemaInspector schemaInspector = new JdbcSchemaInspector(db);

        // Service: бизнес-логика
        ClientService clientService = new ClientService(clientRepository, bookingRepository);
        StationService stationService = new StationService(stationRepository, bookingRepository);
        BookingService bookingService = new BookingService(bookingRepository, clientRepository, stationRepository);
        StatisticsService statisticsService = new StatisticsService(statisticsRepository);

        // Экспорт: список реализаций интерфейса Exporter
        List<Exporter> exporters = List.of(new ExcelExporter(), new CsvExporter());

        // UI: меню
        MainMenu mainMenu = new MainMenu(io,
                new ClientMenu(io, clientService),
                new StationMenu(io, stationService),
                new BookingMenu(io, bookingService, clientService, stationService),
                new SearchMenu(io, bookingService),
                new FilterMenu(io, bookingService, clientService),
                new SortMenu(io, bookingService),
                statisticsService,
                new ExportMenu(io, exporters, clientService, stationService, bookingService, statisticsService, exportDirectory),
                new DbTablesMenu(io, schemaInspector),
                new ThemeMenu(io, themePreferences));

        io.println("Подключено к базе данных: " + db.getUrl());
        mainMenu.run();
        io.println("До встречи в клубе!");
    }
}
