package ru.mirea.cyberclub.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import ru.mirea.cyberclub.exception.ValidationException;
import ru.mirea.cyberclub.export.CsvExporter;
import ru.mirea.cyberclub.export.ExcelExporter;
import ru.mirea.cyberclub.export.ExportData;
import ru.mirea.cyberclub.export.Exporter;
import ru.mirea.cyberclub.model.Booking;

/**
 * Выгрузка текущего набора бронирований в файл. Путь выбирает пользователь
 * (FileChooser в контроллере), формат определяется по расширению файла.
 */
public class ExportService {

    private final StatisticsService statisticsService;
    private final List<Exporter> exporters;

    public ExportService(StatisticsService statisticsService) {
        this(statisticsService, List.of(new ExcelExporter(), new CsvExporter()));
    }

    public ExportService(StatisticsService statisticsService, List<Exporter> exporters) {
        this.statisticsService = statisticsService;
        this.exporters = List.copyOf(exporters);
    }

    /** Поддерживаемые форматы (для фильтров диалога сохранения). */
    public List<Exporter> getExporters() {
        return exporters;
    }

    /**
     * Записывает брони (в переданном порядке) и показатели по ним в файл.
     *
     * @return количество выгруженных записей
     */
    public int export(List<Booking> bookings, Path file) {
        if (file == null) {
            throw new ValidationException("Файл для сохранения не выбран.");
        }
        if (bookings.isEmpty()) {
            throw new ValidationException("Нет данных для экспорта: таблица пуста (проверьте фильтры).");
        }
        Exporter exporter = exporterFor(file);
        exporter.export(new ExportData(List.copyOf(bookings), statisticsService.forBookings(bookings)), file);
        return bookings.size();
    }

    private Exporter exporterFor(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return exporters.stream()
                .filter(e -> name.endsWith("." + e.extension()))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Неподдерживаемый формат файла: " + name
                        + ". Выберите .xlsx или .csv."));
    }
}
