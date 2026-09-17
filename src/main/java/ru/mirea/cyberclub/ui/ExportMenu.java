package ru.mirea.cyberclub.ui;

import java.nio.file.Path;
import java.util.List;

import ru.mirea.cyberclub.export.ExportData;
import ru.mirea.cyberclub.export.Exporter;
import ru.mirea.cyberclub.service.BookingService;
import ru.mirea.cyberclub.service.ClientService;
import ru.mirea.cyberclub.service.StationService;
import ru.mirea.cyberclub.service.StatisticsService;

/**
 * Меню «Экспорт данных»: один пункт на каждую реализацию {@link Exporter}.
 * Меню не знает, какой именно формат за пунктом — работает через интерфейс.
 */
public class ExportMenu extends AbstractMenu {

    private final ClientService clientService;
    private final StationService stationService;
    private final BookingService bookingService;
    private final StatisticsService statisticsService;
    private final Path exportDirectory;

    public ExportMenu(ConsoleIO io, List<Exporter> exporters, ClientService clientService,
                      StationService stationService, BookingService bookingService,
                      StatisticsService statisticsService, Path exportDirectory) {
        super(io, "Экспорт данных");
        this.clientService = clientService;
        this.stationService = stationService;
        this.bookingService = bookingService;
        this.statisticsService = statisticsService;
        this.exportDirectory = exportDirectory;
        for (Exporter exporter : exporters) {
            add(exporter.getTitle(), () -> export(exporter));
        }
    }

    private void export(Exporter exporter) {
        ExportData data = new ExportData(
                clientService.getAll(),
                stationService.getAll(),
                bookingService.getAll(),
                statisticsService.toIndicators(statisticsService.collect()));
        Path result = exporter.export(data, exportDirectory);
        io.printSuccess("Экспорт выполнен: " + result.toAbsolutePath());
        io.println("Клиентов: " + data.clients().size() + ", мест: " + data.stations().size()
                + ", бронирований: " + data.bookings().size() + ".");
    }
}
